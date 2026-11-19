"""CRM registration service."""

from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.lead import Lead, LeadStatus
from app.models.processing_job import JobStatus, ProcessingJob
from app.services.crm import get_crm_integration


class CRMService:
    """CRM 등록 서비스."""

    async def process_crm_registration_job(self, job: ProcessingJob, db: AsyncSession) -> None:
        """CRM 등록 작업을 처리합니다."""
        # 작업 상태를 처리 중으로 변경
        job.status = JobStatus.PROCESSING
        job.started_at = datetime.now(UTC)
        await db.commit()

        try:
            # Lead 조회
            stmt = select(Lead).where(Lead.id == job.lead_id)
            result = await db.execute(stmt)
            lead = result.scalar_one_or_none()

            if not lead:
                raise Exception(f"Lead {job.lead_id} not found")

            # Lead 상태 확인 (승인된 리드만 등록)
            if lead.status != LeadStatus.APPROVED:
                raise Exception(f"Lead {lead.id} is not approved (status: {lead.status})")

            # CRM 연동 가져오기
            crm = get_crm_integration()
            if not crm:
                # CRM이 설정되지 않은 경우 작업 완료로 처리
                print(f"⚠️  CRM not configured, skipping registration for lead {lead.id}")
                job.status = JobStatus.COMPLETED
                job.completed_at = datetime.now(UTC)
                await db.commit()
                return

            # CRM에 리드 등록
            print(f"📤 Registering lead {lead.id} to CRM...")
            crm_id = await crm.create_lead(lead)

            # Lead 업데이트
            lead.crm_id = crm_id
            lead.crm_registered_at = datetime.now()
            lead.status = LeadStatus.CRM_REGISTERED
            lead.crm_error_message = None

            # 작업 완료
            job.status = JobStatus.COMPLETED
            job.completed_at = datetime.now(UTC)

            await db.commit()
            print(f"✅ Lead {lead.id} registered to CRM with ID: {crm_id}")

        except Exception as e:
            # 에러 처리
            error_message = str(e)
            print(f"❌ CRM registration failed for job {job.id}: {error_message}")

            # Lead 에러 메시지 저장
            stmt = select(Lead).where(Lead.id == job.lead_id)
            result = await db.execute(stmt)
            lead = result.scalar_one_or_none()
            if lead:
                lead.status = LeadStatus.CRM_FAILED
                lead.crm_error_message = error_message

            # 재시도 횟수 증가
            job.retry_count += 1
            job.error_message = error_message

            if job.retry_count >= job.max_retries:
                # 최대 재시도 횟수 초과
                job.status = JobStatus.FAILED
                print(f"❌ Job {job.id} failed permanently after {job.retry_count} retries")
            else:
                # 재시도 대기
                job.status = JobStatus.PENDING
                print(f"🔄 Job {job.id} will be retried ({job.retry_count}/{job.max_retries})")

            await db.commit()


# Singleton instance
crm_service = CRMService()
