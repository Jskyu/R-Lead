"""Business card upload schemas."""

from pydantic import BaseModel, Field


class CardUploadRequest(BaseModel):
    """명함 업로드 요청."""

    client_uuid: str = Field(..., description="Client-generated UUID for deduplication")
    event_name: str | None = Field(None, description="행사명")
    booth_number: str | None = Field(None, description="부스 번호")
    collected_by: str | None = Field(None, description="수집 담당자")
    interested_products: list[str] | None = Field(None, description="관심 제품 목록")
    marketing_consent: bool = Field(False, description="마케팅 수신 동의")


class CardUploadResponse(BaseModel):
    """명함 업로드 응답."""

    lead_id: int = Field(..., description="생성된 리드 ID")
    message: str = Field(..., description="상태 메시지")
    duplicate: bool = Field(False, description="중복 여부")
    duplicate_of_lead_id: int | None = Field(None, description="중복된 기존 리드 ID")


class BatchUploadResult(BaseModel):
    """개별 업로드 결과."""

    index: int = Field(..., description="파일 인덱스")
    success: bool = Field(..., description="성공 여부")
    lead_id: int | None = Field(None, description="생성된 리드 ID")
    message: str = Field(..., description="상태 메시지")
    duplicate: bool = Field(False, description="중복 여부")


class BatchUploadResponse(BaseModel):
    """배치 업로드 응답."""

    total: int = Field(..., description="전체 파일 수")
    success_count: int = Field(..., description="성공한 파일 수")
    results: list[BatchUploadResult] = Field(..., description="개별 결과 목록")
