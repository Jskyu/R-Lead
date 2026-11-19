"""Lead model - CRM 리드 정보."""

from datetime import datetime
from enum import Enum
from typing import Any

from sqlalchemy import JSON, Boolean, DateTime, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin


class LeadStatus(str, Enum):
    """리드 처리 상태."""

    UPLOADED = "uploaded"  # 업로드됨
    EXTRACTED = "extracted"  # AI 추출됨
    REVIEWED = "reviewed"  # 담당자 확인됨
    APPROVED = "approved"  # 승인됨
    REJECTED = "rejected"  # 거부됨
    CRM_REGISTERED = "crm_registered"  # CRM 등록됨
    CRM_FAILED = "crm_failed"  # CRM 등록 실패


class Lead(Base, TimestampMixin):
    """리드 모델."""

    __tablename__ = "leads"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)

    # 명함 정보
    name: Mapped[str | None] = mapped_column(String(255), index=True)
    company: Mapped[str | None] = mapped_column(String(255), index=True)
    department: Mapped[str | None] = mapped_column(String(255))
    job_title: Mapped[str | None] = mapped_column(String(255))
    email: Mapped[str | None] = mapped_column(String(255), index=True)
    phone: Mapped[str | None] = mapped_column(String(50), index=True)
    address: Mapped[str | None] = mapped_column(Text)
    website: Mapped[str | None] = mapped_column(String(500))

    # 관심 제품 (JSON array)
    interested_products: Mapped[list[str] | None] = mapped_column(JSON)

    # 수집 출처
    event_name: Mapped[str | None] = mapped_column(String(255))  # 행사명
    booth_number: Mapped[str | None] = mapped_column(String(100))  # 부스 번호
    collected_by: Mapped[str | None] = mapped_column(String(255))  # 수집한 직원
    collected_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    # 마케팅 수신 동의
    marketing_consent: Mapped[bool] = mapped_column(Boolean, default=False)
    marketing_consent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    # 처리 상태
    status: Mapped[LeadStatus] = mapped_column(String(50), default=LeadStatus.UPLOADED, index=True)

    # AI 추출 결과 (JSONB - 원본)
    ai_extracted_data: Mapped[dict[str, Any] | None] = mapped_column(JSON)

    # 검증 문제 목록 (ValidationIssue 배열)
    validation_issues: Mapped[list[dict[str, Any]] | None] = mapped_column(JSON)

    # 필드별 신뢰도 (0~99)
    field_confidences: Mapped[dict[str, int] | None] = mapped_column(JSON)

    # 담당자가 수정한 필드 목록
    modified_fields: Mapped[list[str] | None] = mapped_column(JSON)

    # 명함 사진
    card_image_url: Mapped[str | None] = mapped_column(String(1000))  # S3 URL
    card_image_key: Mapped[str | None] = mapped_column(String(500))  # S3 key
    image_delete_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    # 한 사진에 명함이 여러 장일 때 몇 번째 명함인지 (0부터, 한 장이면 None)
    card_index: Mapped[int | None] = mapped_column()

    # 중복 리드 참조
    duplicate_of_lead_id: Mapped[int | None] = mapped_column()

    # CRM 정보
    crm_id: Mapped[str | None] = mapped_column(String(255))  # CRM에서 발급한 ID
    crm_registered_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    crm_error_message: Mapped[str | None] = mapped_column(Text)

    # 클라이언트가 생성한 UUID (중복 방지)
    client_uuid: Mapped[str | None] = mapped_column(String(36), unique=True, index=True)

    def __repr__(self) -> str:
        return (
            f"<Lead(id={self.id}, name={self.name}, company={self.company}, status={self.status})>"
        )
