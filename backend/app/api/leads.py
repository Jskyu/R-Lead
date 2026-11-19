"""Lead management API."""

from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.lead import Lead, LeadStatus
from app.models.processing_job import JobStatus, JobType, ProcessingJob
from app.schemas.lead import LeadApproval, LeadResponse, LeadUpdate
from app.services.storage import storage_service
from app.utils.validation import calculate_all_confidences, validate_extracted_data

router = APIRouter()

_CARD_FIELDS = (
    "name",
    "company",
    "department",
    "job_title",
    "email",
    "phone",
    "address",
    "website",
)


class BulkRequest(BaseModel):
    """일괄 작업 요청."""

    lead_ids: list[int] = []


async def _get_lead_or_404(db: AsyncSession, lead_id: int) -> Lead:
    lead = (await db.execute(select(Lead).where(Lead.id == lead_id))).scalar_one_or_none()
    if not lead:
        raise HTTPException(status_code=404, detail="리드를 찾을 수 없습니다.")
    return lead


async def _attach_image_url(db: AsyncSession, lead: Lead) -> None:
    """응답용으로 명함 사진의 임시(서명) URL 을 만든다. DB 에는 저장하지 않는다."""
    if lead.card_image_key:
        try:
            url = await storage_service.generate_presigned_url(lead.card_image_key, expiration=3600)
        except Exception as e:
            print(f"Failed to generate presigned URL: {e}")
            return
        db.expunge(lead)  # 서명 URL 이 UPDATE 로 DB 에 기록되지 않도록 세션에서 분리한다
        lead.card_image_url = url


def _apply_updates(lead: Lead, update: LeadUpdate) -> None:
    """담당자가 수정한 값을 반영하고 수정한 필드를 기록한다."""
    modified = list(lead.modified_fields or [])
    for field, value in update.model_dump(exclude_unset=True).items():
        if hasattr(lead, field) and getattr(lead, field) != value:
            setattr(lead, field, value)
            if field in _CARD_FIELDS and field not in modified:
                modified.append(field)
    lead.modified_fields = modified

    # 담당자가 값을 고쳤으면 현재 값 기준으로 규칙 검증과 신뢰도를 다시 계산한다.
    # (고치기 전의 오류 표시가 남아 있으면 확인 화면이 오해를 준다)
    if lead.ai_extracted_data is not None:
        values = {f: getattr(lead, f) for f in _CARD_FIELDS}
        issues = validate_extracted_data(values)
        lead.field_confidences = calculate_all_confidences(values, issues)
        lead.validation_issues = [i.model_dump() for i in issues]


async def _delete_lead_with_image(db: AsyncSession, lead: Lead, already_deleted: set[int]) -> None:
    """리드와 관련 작업을 지운다. 사진은 같은 사진을 쓰는 리드가 더 없을 때만 지운다."""
    if lead.card_image_key:
        excluded = already_deleted | {lead.id}
        others = (
            await db.execute(
                select(func.count())
                .select_from(Lead)
                .where(Lead.card_image_key == lead.card_image_key, Lead.id.notin_(excluded))
            )
        ).scalar_one()
        if others == 0:
            try:
                await storage_service.delete_image(lead.card_image_key)
            except Exception as e:
                # 이미지 삭제에 실패해도 리드 삭제는 진행한다.
                print(f"⚠️ Failed to delete image from S3: {e}")

    await db.execute(delete(ProcessingJob).where(ProcessingJob.lead_id == lead.id))
    await db.delete(lead)
    await db.flush()
    already_deleted.add(lead.id)


@router.get("/pending", response_model=list[LeadResponse])
async def get_pending_leads(
    limit: int = 50, offset: int = 0, db: AsyncSession = Depends(get_db)
) -> list[Lead]:
    """담당자 확인이 필요한 리드 목록을 조회합니다."""
    stmt = (
        select(Lead)
        .where(Lead.status == LeadStatus.EXTRACTED)
        .order_by(Lead.created_at.desc())
        .limit(limit)
        .offset(offset)
    )
    leads = list((await db.execute(stmt)).scalars().all())
    for lead in leads:
        await _attach_image_url(db, lead)
    return leads


@router.get("", response_model=list[LeadResponse], include_in_schema=False)
@router.get("/", response_model=list[LeadResponse])
async def get_all_leads(
    status: LeadStatus | None = None,
    limit: int = 100,
    offset: int = 0,
    db: AsyncSession = Depends(get_db),
) -> list[Lead]:
    """모든 리드 목록을 조회합니다 (상태 필터 가능)."""
    stmt = select(Lead).order_by(Lead.created_at.desc(), Lead.id.desc()).limit(limit).offset(offset)
    if status:
        stmt = stmt.where(Lead.status == status)

    leads = list((await db.execute(stmt)).scalars().all())
    for lead in leads:
        await _attach_image_url(db, lead)
    return leads


@router.get("/queue/status")
async def get_queue_status(db: AsyncSession = Depends(get_db)) -> dict[str, Any]:
    """처리 대기 중인 작업 상태를 조회합니다."""

    async def jobs_by(status: JobStatus) -> list[ProcessingJob]:
        stmt = select(ProcessingJob).where(ProcessingJob.status == status)
        return list((await db.execute(stmt)).scalars().all())

    async def lead_summaries(ids: list[int | None]) -> list[dict[str, Any]]:
        wanted = [i for i in ids if i is not None]
        if not wanted:
            return []
        rows = (await db.execute(select(Lead).where(Lead.id.in_(wanted)))).scalars().all()
        return [
            {"id": r.id, "event_name": r.event_name, "created_at": str(r.created_at)} for r in rows
        ]

    pending = await jobs_by(JobStatus.PENDING)
    processing = await jobs_by(JobStatus.PROCESSING)
    failed = await jobs_by(JobStatus.FAILED)

    # 실패 사유 (리드별 가장 최근 추출 작업). 이미 재추출에 성공했거나 삭제된 리드의
    # 과거 실패 이력은 제외하고, 지금도 실패 상태(UPLOADED)인 리드만 센다.
    latest: dict[int, ProcessingJob] = {}
    for job in sorted(failed, key=lambda j: j.id):
        if job.lead_id is not None and job.job_type == JobType.EXTRACT_CARD:
            latest[job.lead_id] = job
    still_uploaded: set[int] = set()
    if latest:
        rows = await db.execute(
            select(Lead.id).where(Lead.id.in_(list(latest)), Lead.status == LeadStatus.UPLOADED)
        )
        still_uploaded = set(rows.scalars().all())
    failed_leads = [
        {"id": lead_id, "error": (job.error_message or "")[:200]}
        for lead_id, job in latest.items()
        if lead_id in still_uploaded
    ]

    return {
        "pending_count": len(pending),
        "processing_count": len(processing),
        "failed_count": len(failed_leads),
        "pending_leads": await lead_summaries([j.lead_id for j in pending]),
        "processing_leads": await lead_summaries([j.lead_id for j in processing]),
        "failed_leads": failed_leads,
    }


@router.post("/bulk-reextract")
async def bulk_reextract_leads(
    request: BulkRequest, db: AsyncSession = Depends(get_db)
) -> dict[str, Any]:
    """여러 리드를 일괄 재추출합니다."""
    if not request.lead_ids:
        raise HTTPException(status_code=400, detail="No lead IDs provided")

    leads = list((await db.execute(select(Lead).where(Lead.id.in_(request.lead_ids)))).scalars())
    if not leads:
        raise HTTPException(status_code=404, detail="리드를 찾을 수 없습니다.")

    for lead in leads:
        # 상태를 UPLOADED 로 되돌리고 기존 추출 결과를 초기화한다 (card_index 는 유지).
        lead.status = LeadStatus.UPLOADED
        lead.ai_extracted_data = None
        lead.validation_issues = None
        lead.field_confidences = None
        db.add(ProcessingJob(job_type=JobType.EXTRACT_CARD, lead_id=lead.id))

    await db.commit()
    return {
        "message": f"Created {len(leads)} reextraction jobs",
        "job_count": len(leads),
        "lead_ids": request.lead_ids,
    }


@router.post("/bulk-delete")
async def bulk_delete_leads(
    request: BulkRequest, db: AsyncSession = Depends(get_db)
) -> dict[str, Any]:
    """여러 리드를 일괄 삭제합니다."""
    if not request.lead_ids:
        raise HTTPException(status_code=400, detail="No lead IDs provided")

    leads = list((await db.execute(select(Lead).where(Lead.id.in_(request.lead_ids)))).scalars())
    if not leads:
        raise HTTPException(status_code=404, detail="리드를 찾을 수 없습니다.")

    deleted: set[int] = set()
    for lead in leads:
        await _delete_lead_with_image(db, lead, deleted)

    await db.commit()
    return {
        "message": f"Deleted {len(deleted)} leads",
        "deleted_count": len(deleted),
        "lead_ids": request.lead_ids,
    }


@router.get("/{lead_id}", response_model=LeadResponse)
async def get_lead(lead_id: int, db: AsyncSession = Depends(get_db)) -> Lead:
    """특정 리드의 상세 정보를 조회합니다."""
    lead = await _get_lead_or_404(db, lead_id)
    await _attach_image_url(db, lead)
    return lead


@router.patch("/{lead_id}", response_model=LeadResponse)
async def update_lead(lead_id: int, update: LeadUpdate, db: AsyncSession = Depends(get_db)) -> Lead:
    """리드 정보를 수정합니다 (담당자)."""
    lead = await _get_lead_or_404(db, lead_id)
    _apply_updates(lead, update)
    await db.commit()
    await db.refresh(lead)
    await _attach_image_url(db, lead)
    return lead


@router.post("/{lead_id}/approve", response_model=LeadResponse)
async def approve_lead(
    lead_id: int, approval: LeadApproval, db: AsyncSession = Depends(get_db)
) -> Lead:
    """리드를 승인 또는 거부합니다."""
    lead = await _get_lead_or_404(db, lead_id)

    if approval.updates:
        _apply_updates(lead, approval.updates)

    if approval.approved:
        lead.status = LeadStatus.APPROVED
        # CRM 등록 작업 생성
        db.add(ProcessingJob(job_type=JobType.REGISTER_CRM, lead_id=lead.id))
    else:
        lead.status = LeadStatus.REJECTED

    await db.commit()
    await db.refresh(lead)
    await _attach_image_url(db, lead)
    return lead


@router.delete("/{lead_id}")
async def delete_lead(lead_id: int, db: AsyncSession = Depends(get_db)) -> dict[str, Any]:
    """리드를 삭제합니다 (처리 중이어도 삭제 가능)."""
    lead = await _get_lead_or_404(db, lead_id)
    await _delete_lead_with_image(db, lead, set())
    await db.commit()
    return {"message": f"Lead {lead_id} deleted successfully"}
