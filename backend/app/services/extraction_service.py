"""Extraction service for processing business cards."""

from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.lead import Lead, LeadStatus
from app.models.processing_job import JobStatus, ProcessingJob
from app.schemas.extraction import ExtractedData
from app.services.extractors import get_extractor
from app.services.extractors.common import UnreadableCardError, is_blank_image
from app.services.storage import storage_service
from app.utils.image import resize_image_for_ai

# 한 사진에 명함이 여러 장일 수 있으므로 AI 에 보내는 이미지는 넉넉하게 유지한다.
AI_IMAGE_MAX_SIZE = 2048


class ExtractionService:
    """명함 추출 서비스."""

    def __init__(self) -> None:
        self.extractor = get_extractor()

    async def process_extraction_job(self, job: ProcessingJob, db: AsyncSession) -> None:
        """
        추출 작업을 처리합니다. 실패해도 예외를 던지지 않고 작업 상태에 기록합니다.

        - 일시 오류(AI 과부하 등)는 재시도 횟수가 남아 있으면 PENDING 으로 되돌립니다.
        - 명함이 아니거나 빈 이미지는 같은 결과가 반복되므로 바로 FAILED 로 확정합니다.
        - 한 사진에 명함이 여러 장이면 첫 장은 이 리드에, 나머지는 새 리드로 만듭니다.
        """
        try:
            job.status = JobStatus.PROCESSING
            job.started_at = datetime.now(UTC)
            await db.commit()

            lead = (
                await db.execute(select(Lead).where(Lead.id == job.lead_id))
            ).scalar_one_or_none()
            if not lead:
                raise RuntimeError(f"Lead not found: {job.lead_id}")
            if not lead.card_image_key:
                raise RuntimeError("Card image key is missing")

            # 원본은 저장소에 그대로 두고, AI 에는 크기를 줄인 이미지를 보낸다.
            original = await storage_service.download_image(lead.card_image_key)
            compressed = resize_image_for_ai(
                original, max_width=AI_IMAGE_MAX_SIZE, max_height=AI_IMAGE_MAX_SIZE, quality=88
            )
            print(f"📊 Image size: {len(original) / 1024:.1f}KB → {len(compressed) / 1024:.1f}KB")

            # 빈 이미지에 LLM 이 정보를 지어내는 것을 막는다 (AI 호출 전 규칙 검사)
            if is_blank_image(compressed):
                raise UnreadableCardError("빈 이미지입니다. 명함이 보이도록 다시 촬영해 주세요.")

            cards = await self.extractor.extract_all(compressed)
            print(f"🃏 Cards found in image: {len(cards)}")

            if lead.card_index is not None:
                # 이미 여러 장으로 나뉜 사진의 재추출: 이 리드가 맡은 순서의 명함만 사용한다.
                if lead.card_index >= len(cards):
                    raise UnreadableCardError(
                        f"사진에서 {lead.card_index + 1}번째 명함을 찾지 못했습니다."
                    )
                await self._apply_extraction(lead, cards[lead.card_index], db)
            else:
                await self._apply_extraction(lead, cards[0], db)
                if len(cards) > 1:
                    lead.card_index = 0
                    for index, card in enumerate(cards[1:], start=1):
                        extra = Lead(
                            status=LeadStatus.UPLOADED,
                            card_index=index,
                            event_name=lead.event_name,
                            booth_number=lead.booth_number,
                            collected_by=lead.collected_by,
                            collected_at=lead.collected_at,
                            interested_products=lead.interested_products,
                            marketing_consent=lead.marketing_consent,
                            marketing_consent_at=lead.marketing_consent_at,
                            card_image_url=lead.card_image_url,
                            card_image_key=lead.card_image_key,
                            image_delete_at=lead.image_delete_at,
                        )
                        db.add(extra)
                        await db.flush()
                        await self._apply_extraction(extra, card, db)
                        print(f"➕ Created lead {extra.id} for card #{index + 1} in the same photo")

            job.status = JobStatus.COMPLETED
            job.completed_at = datetime.now(UTC)
            await db.commit()
            print(f"✅ Extraction completed for lead {lead.id}")

        except Exception as e:
            await db.rollback()
            await db.refresh(job)
            attempts = (job.retry_count or 0) + 1
            job.retry_count = attempts
            job.error_message = str(e)[:500]
            # 같은 이미지는 다시 보내도 결과가 같으므로(명함 아님/빈 이미지) 재시도하지 않는다.
            retryable = not isinstance(e, UnreadableCardError)
            job.status = (
                JobStatus.PENDING
                if retryable and attempts < (job.max_retries or 3)
                else JobStatus.FAILED
            )
            await db.commit()
            print(f"❌ Extraction failed ({job.status}): {job.error_message}")

    async def _apply_extraction(
        self, lead: Lead, extracted: ExtractedData, db: AsyncSession
    ) -> None:
        """추출된 명함 한 장을 검증하고 리드에 반영합니다."""
        from app.services.duplicate_checker import duplicate_checker
        from app.utils.validation import calculate_all_confidences, validate_extracted_data

        data = extracted.model_dump()
        issues = validate_extracted_data(data)
        if issues:
            # 값(개인정보)은 로그에 남기지 않는다.
            print(f"⚠️  Found {len(issues)} validation issues: {[i.field for i in issues]}")

        confidences = calculate_all_confidences(data, issues)
        for field_name, confidence in confidences.items():
            setattr(extracted, field_name, confidence)

        # 중복 리드 체크 (같은 사진의 형제 리드는 제외)
        _, duplicate = await duplicate_checker.check_duplicate(
            email=extracted.email, phone=extracted.phone, company=extracted.company, db=db
        )
        if (
            duplicate
            and duplicate.id != lead.id
            and not (duplicate.card_image_key and duplicate.card_image_key == lead.card_image_key)
        ):
            lead.duplicate_of_lead_id = duplicate.id

        lead.name = extracted.name
        lead.company = extracted.company
        lead.department = extracted.department
        lead.job_title = extracted.job_title
        lead.email = extracted.email
        lead.phone = extracted.phone
        lead.address = extracted.address
        lead.website = extracted.website
        lead.ai_extracted_data = extracted.model_dump()
        lead.validation_issues = [issue.model_dump() for issue in issues]
        lead.field_confidences = confidences
        lead.status = LeadStatus.EXTRACTED
        await db.flush()


# Singleton instance
extraction_service = ExtractionService()
