"""Background worker for processing jobs."""

import asyncio
from datetime import UTC, datetime, timedelta

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import AsyncSessionLocal
from app.models.processing_job import JobStatus, JobType, ProcessingJob
from app.services.crm_service import crm_service
from app.services.extraction_service import extraction_service


class BackgroundWorker:
    """백그라운드 작업 워커."""

    def __init__(self) -> None:
        self.is_running = False

    async def recover_stuck_jobs(self) -> int:
        """서버가 비정상 종료되어 PROCESSING 으로 남은 작업을 PENDING 으로 되돌립니다."""
        async with AsyncSessionLocal() as db:
            result = await db.execute(
                update(ProcessingJob)
                .where(ProcessingJob.status == JobStatus.PROCESSING)
                .values(status=JobStatus.PENDING)
            )
            await db.commit()
            return int(getattr(result, "rowcount", 0) or 0)

    async def start(self) -> None:
        """워커를 시작합니다."""
        self.is_running = True
        recovered = await self.recover_stuck_jobs()
        if recovered:
            print(f"♻️  Recovered {recovered} stuck jobs")
        print("🔄 Background worker started")

        while self.is_running:
            try:
                await self.process_pending_jobs()
                await asyncio.sleep(5)  # 5초마다 체크
            except Exception as e:
                print(f"❌ Worker error: {e}")
                await asyncio.sleep(10)  # 에러 발생 시 10초 대기

    @staticmethod
    def _is_backing_off(job: ProcessingJob, now: datetime) -> bool:
        """재시도 간격(재시도 횟수 × 20초)이 아직 지나지 않았으면 True."""
        if not job.retry_count or not job.started_at:
            return False
        started = job.started_at
        if started.tzinfo is None:  # SQLite 등은 시간대 없이 UTC 로 저장된다
            started = started.replace(tzinfo=UTC)
        return started + timedelta(seconds=20 * job.retry_count) > now

    async def stop(self) -> None:
        """워커를 중지합니다."""
        self.is_running = False
        print("⏹️  Background worker stopped")

    async def process_pending_jobs(self) -> None:
        """대기 중인 작업을 처리합니다."""
        async with AsyncSessionLocal() as db:
            # 대기 중인 작업 조회
            stmt = (
                select(ProcessingJob)
                .where(ProcessingJob.status == JobStatus.PENDING)
                .where(ProcessingJob.retry_count < ProcessingJob.max_retries)
                .order_by(ProcessingJob.created_at)
                .limit(10)  # 한 번에 최대 10개씩 처리
            )

            result = await db.execute(stmt)
            jobs = result.scalars().all()

            # 재시도 대기 중인 작업은 (재시도 횟수 × 20초)가 지난 뒤에 다시 처리한다.
            now = datetime.now(UTC)
            ready = [job for job in jobs if not self._is_backing_off(job, now)]
            if not ready:
                return

            print(f"📋 Processing {len(ready)} pending jobs")

            for job in ready:
                try:
                    await self.process_job(job, db)
                except Exception as e:
                    print(f"❌ Job {job.id} failed: {e}")

    async def process_job(self, job: ProcessingJob, db: AsyncSession) -> None:
        """개별 작업을 처리합니다."""
        if job.job_type == JobType.EXTRACT_CARD:
            await extraction_service.process_extraction_job(job, db)
        elif job.job_type == JobType.REGISTER_CRM:
            await crm_service.process_crm_registration_job(job, db)
        elif job.job_type == JobType.SEND_EMAIL:
            # TODO: 이메일 발송 구현 (Phase 2)
            print(f"TODO: Email sending for job {job.id}")
            # Phase 2에서 구현 예정
            job.status = JobStatus.COMPLETED
            job.completed_at = datetime.now()
            await db.commit()
        else:
            raise ValueError(f"Unknown job type: {job.job_type}")


# Singleton instance
background_worker = BackgroundWorker()
