"""Processing job model - 백그라운드 작업 큐."""

from datetime import datetime
from enum import Enum

from sqlalchemy import DateTime, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class JobStatus(str, Enum):
    """작업 상태."""

    PENDING = "pending"  # 대기 중
    PROCESSING = "processing"  # 처리 중
    COMPLETED = "completed"  # 완료
    FAILED = "failed"  # 실패


class JobType(str, Enum):
    """작업 종류."""

    EXTRACT_CARD = "extract_card"  # 명함 AI 추출
    REGISTER_CRM = "register_crm"  # CRM 등록
    SEND_EMAIL = "send_email"  # 이메일 발송


class ProcessingJob(Base, TimestampMixin):
    """백그라운드 작업 모델."""

    __tablename__ = "processing_jobs"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)

    # 작업 정보
    job_type: Mapped[JobType] = mapped_column(String(50), nullable=False)
    status: Mapped[JobStatus] = mapped_column(String(50), default=JobStatus.PENDING, index=True)

    # 연관 리드 ID
    lead_id: Mapped[int | None] = mapped_column(Integer, index=True)

    # 재시도
    retry_count: Mapped[int] = mapped_column(Integer, default=0)
    max_retries: Mapped[int] = mapped_column(Integer, default=3)

    # 처리 시간
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    # 에러 정보
    error_message: Mapped[str | None] = mapped_column(Text)

    def __repr__(self) -> str:
        return f"<ProcessingJob(id={self.id}, type={self.job_type}, status={self.status})>"
