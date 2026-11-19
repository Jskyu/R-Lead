"""AI extraction schemas."""

from pydantic import BaseModel, Field


class FieldConfidence(BaseModel):
    """필드별 신뢰도 정보."""

    value: str | None = Field(None, description="추출된 값")
    confidence: int = Field(
        default=0,
        ge=0,
        le=99,
        description="신뢰도 (0~99). 0=빈 값, 30=형식 오류, 70=형식 검증 통과, 90=명확한 추출",
    )


class ExtractedData(BaseModel):
    """AI가 추출한 명함 데이터 (Pydantic V2)."""

    name: str | None = Field(
        None,
        description="이름. 명함에서 읽을 수 없으면 비워 두세요.",
    )
    company: str | None = Field(
        None,
        description="회사명. 명함에서 읽을 수 없으면 비워 두세요.",
    )
    department: str | None = Field(
        None,
        description="부서. 명함에서 읽을 수 없으면 비워 두세요.",
    )
    job_title: str | None = Field(
        None,
        description="직함. 명함에서 읽을 수 없으면 비워 두세요.",
    )
    email: str | None = Field(
        None,
        description="이메일 주소. 명함에서 읽을 수 없으면 비워 두세요.",
    )
    phone: str | None = Field(
        None,
        description="전화번호 (휴대폰 또는 사무실). 명함에서 읽을 수 없으면 비워 두세요.",
    )
    address: str | None = Field(
        None,
        description="주소. 명함에서 읽을 수 없으면 비워 두세요.",
    )
    website: str | None = Field(
        None,
        description="웹사이트 URL. 명함에서 읽을 수 없으면 비워 두세요.",
    )

    # 신뢰도 정보
    name_confidence: int = Field(default=0, ge=0, le=99, description="이름 신뢰도 (0~99)")
    company_confidence: int = Field(default=0, ge=0, le=99, description="회사명 신뢰도 (0~99)")
    department_confidence: int = Field(default=0, ge=0, le=99, description="부서 신뢰도 (0~99)")
    job_title_confidence: int = Field(default=0, ge=0, le=99, description="직함 신뢰도 (0~99)")
    email_confidence: int = Field(default=0, ge=0, le=99, description="이메일 신뢰도 (0~99)")
    phone_confidence: int = Field(default=0, ge=0, le=99, description="전화번호 신뢰도 (0~99)")
    address_confidence: int = Field(default=0, ge=0, le=99, description="주소 신뢰도 (0~99)")
    website_confidence: int = Field(default=0, ge=0, le=99, description="웹사이트 신뢰도 (0~99)")

    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "name": "홍길동",
                    "company": "ABC 기술",
                    "department": "기술영업팀",
                    "job_title": "부장",
                    "email": "hong@abc.co.kr",
                    "phone": "010-1234-5678",
                    "address": "서울시 강남구 테헤란로 123",
                    "website": "https://abc.co.kr",
                }
            ]
        }
    }


class ValidationIssue(BaseModel):
    """검증 문제점."""

    field: str = Field(..., description="문제가 있는 필드명")
    issue_type: str = Field(
        ..., description="문제 유형 (empty, invalid_format, missing_in_original)"
    )
    message: str = Field(..., description="사람이 읽을 수 있는 설명")


class ExtractedDataWithValidation(ExtractedData):
    """검증 결과가 포함된 추출 데이터."""

    validation_issues: list[ValidationIssue] = Field(
        default_factory=list, description="발견된 검증 문제 목록"
    )
    is_valid: bool = Field(..., description="전체 데이터가 유효한지 여부")
