"""Business card upload API."""

import json
from datetime import datetime
from uuid import uuid4

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.lead import Lead, LeadStatus
from app.models.processing_job import JobType, ProcessingJob
from app.schemas.card import (
    BatchUploadResponse,
    BatchUploadResult,
    CardUploadRequest,
    CardUploadResponse,
)
from app.services.storage import storage_service

router = APIRouter()

MAX_IMAGE_BYTES = 10 * 1024 * 1024  # 10MB


def _validate_image(image: UploadFile, data: bytes) -> None:
    """이미지 파일만 허용한다 (HEIC 등 image/* 포함)."""
    if not data:
        raise HTTPException(status_code=400, detail="빈 이미지 파일입니다.")
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="이미지 파일이 너무 큽니다. (최대 10MB)")
    content_type = (image.content_type or "").lower()
    if not content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드할 수 있습니다.")


async def _create_lead(
    db: AsyncSession, request: CardUploadRequest, image_data: bytes, content_type: str
) -> Lead:
    """이미지를 저장소에 올리고 리드와 AI 추출 작업을 만든다."""
    s3_key, s3_url = await storage_service.upload_image(image_data, content_type=content_type)

    lead = Lead(
        status=LeadStatus.UPLOADED,
        event_name=request.event_name,
        booth_number=request.booth_number,
        collected_by=request.collected_by,
        interested_products=request.interested_products,
        marketing_consent=request.marketing_consent,
        marketing_consent_at=datetime.now() if request.marketing_consent else None,
        card_image_url=s3_url,
        card_image_key=s3_key,
        image_delete_at=storage_service.calculate_delete_date(),
        client_uuid=request.client_uuid,
        collected_at=datetime.now(),
    )
    db.add(lead)
    await db.flush()  # ID 생성을 위해 flush

    # 백그라운드 작업 등록 (AI 추출). 한 사진에 명함이 여러 장이면 추출 단계에서 리드가 늘어난다.
    db.add(ProcessingJob(job_type=JobType.EXTRACT_CARD, lead_id=lead.id))
    return lead


@router.post("/upload", response_model=CardUploadResponse)
async def upload_card(
    image: UploadFile = File(..., description="명함 이미지 파일"),
    metadata: str = Form(..., description="메타데이터 JSON 문자열"),
    db: AsyncSession = Depends(get_db),
) -> CardUploadResponse:
    """명함 사진을 업로드하고 처리 작업을 등록합니다.

    한 사진에 명함이 여러 장이어도 업로드는 한 번이며, AI 추출 단계에서 명함 수만큼 리드가 생깁니다.
    """
    try:
        upload_request = CardUploadRequest(**json.loads(metadata))
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid metadata: {e}") from e

    image_data = await image.read()
    _validate_image(image, image_data)

    # 중복 업로드 체크 (client_uuid 로) - 재전송으로 리드가 중복 생성되지 않게 한다.
    existing = (
        await db.execute(select(Lead).where(Lead.client_uuid == upload_request.client_uuid))
    ).scalar_one_or_none()
    if existing:
        return CardUploadResponse(
            lead_id=existing.id,
            message="이미 업로드된 명함입니다.",
            duplicate=True,
            duplicate_of_lead_id=existing.id,
        )

    try:
        lead = await _create_lead(
            db, upload_request, image_data, image.content_type or "image/jpeg"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Storage upload failed: {e}") from e

    await db.commit()
    return CardUploadResponse(
        lead_id=lead.id,
        message="명함이 업로드되었습니다. AI 추출을 시작합니다.",
        duplicate=False,
    )


@router.post("/upload-batch", response_model=BatchUploadResponse)
async def upload_cards_batch(
    images: list[UploadFile] = File(..., description="명함 이미지 파일들"),
    metadata: str = Form(..., description="메타데이터 JSON 문자열"),
    db: AsyncSession = Depends(get_db),
) -> BatchUploadResponse:
    """여러 명함 사진을 한 번에 업로드하고 처리 작업을 등록합니다."""
    try:
        base_metadata = {k: v for k, v in json.loads(metadata).items() if k != "client_uuid"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid metadata: {e}") from e

    results: list[BatchUploadResult] = []
    success_count = 0

    for index, image in enumerate(images):
        try:
            data = await image.read()
            _validate_image(image, data)
            request = CardUploadRequest(client_uuid=str(uuid4()), **base_metadata)
            lead = await _create_lead(db, request, data, image.content_type or "image/jpeg")
            results.append(
                BatchUploadResult(
                    index=index,
                    success=True,
                    lead_id=lead.id,
                    message="업로드 성공",
                    duplicate=False,
                )
            )
            success_count += 1
        except HTTPException as e:
            results.append(BatchUploadResult(index=index, success=False, message=str(e.detail)))
        except Exception as e:
            results.append(
                BatchUploadResult(index=index, success=False, message=f"업로드 실패: {e}")
            )

    await db.commit()
    return BatchUploadResponse(total=len(images), success_count=success_count, results=results)


@router.get("/health")
async def health_check() -> dict[str, str]:
    """API 헬스 체크."""
    return {"status": "ok", "service": "cards"}
