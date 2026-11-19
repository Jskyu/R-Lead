"""Lead schemas for API validation."""

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field

from app.models.lead import LeadStatus


class LeadBase(BaseModel):
    """Base lead schema."""

    name: str | None = None
    company: str | None = None
    department: str | None = None
    job_title: str | None = None
    email: str | None = None
    phone: str | None = None
    address: str | None = None
    website: str | None = None
    interested_products: list[str] | None = None
    marketing_consent: bool = False


class LeadCreate(LeadBase):
    """Lead creation schema."""

    event_name: str | None = None
    booth_number: str | None = None
    collected_by: str | None = None
    client_uuid: str = Field(..., description="Client-generated UUID for deduplication")


class LeadUpdate(BaseModel):
    """Lead update schema (담당자 수정용)."""

    name: str | None = None
    company: str | None = None
    department: str | None = None
    job_title: str | None = None
    email: str | None = None
    phone: str | None = None
    address: str | None = None
    website: str | None = None
    interested_products: list[str] | None = None
    marketing_consent: bool | None = None


class LeadResponse(LeadBase):
    """Lead response schema (프론트엔드/Spring 백엔드와 동일한 필드)."""

    id: int
    status: LeadStatus
    event_name: str | None = None
    booth_number: str | None = None
    collected_by: str | None = None
    collected_at: datetime | None = None
    marketing_consent_at: datetime | None = None

    # AI 추출 결과 / 규칙 기반 검증 결과
    ai_extracted_data: dict[str, Any] | None = None
    validation_issues: list[dict[str, Any]] | None = None
    field_confidences: dict[str, int] | None = None
    modified_fields: list[str] | None = None

    # 명함 사진
    card_image_url: str | None = None
    card_image_key: str | None = None
    image_delete_at: datetime | None = None

    # 한 사진 속 명함 순서 (여러 장일 때만), 중복 리드
    card_index: int | None = None
    duplicate_of_lead_id: int | None = None

    # CRM
    crm_id: str | None = None
    crm_registered_at: datetime | None = None
    crm_error_message: str | None = None

    client_uuid: str | None = None
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class LeadApproval(BaseModel):
    """Lead approval/rejection schema."""

    approved: bool = Field(..., description="True to approve, False to reject")
    updates: LeadUpdate | None = Field(None, description="Optional field updates before approval")
