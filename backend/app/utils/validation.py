"""Validation utilities for lead data."""

import re
from email.utils import parseaddr

import phonenumbers
from phonenumbers import NumberParseException

from app.schemas.extraction import ValidationIssue


def validate_email(email: str | None) -> ValidationIssue | None:
    """
    이메일 주소를 검증합니다.

    Args:
        email: 검증할 이메일 주소

    Returns:
        ValidationIssue if invalid, None if valid
    """
    if not email:
        return ValidationIssue(
            field="email",
            issue_type="empty",
            message="이메일 주소가 비어 있습니다.",
        )

    # 기본 형식 검증
    email = email.strip()
    name, addr = parseaddr(email)

    # 간단한 정규식 검증
    email_pattern = r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"
    if not re.match(email_pattern, addr):
        return ValidationIssue(
            field="email",
            issue_type="invalid_format",
            message=f"이메일 형식이 올바르지 않습니다: {email}",
        )

    return None


def validate_phone(phone: str | None, region: str = "KR") -> ValidationIssue | None:
    """
    전화번호를 검증합니다.

    Args:
        phone: 검증할 전화번호
        region: 국가 코드 (기본값: 한국)

    Returns:
        ValidationIssue if invalid, None if valid
    """
    if not phone:
        return ValidationIssue(
            field="phone",
            issue_type="empty",
            message="전화번호가 비어 있습니다.",
        )

    try:
        # phonenumbers 라이브러리로 파싱
        parsed = phonenumbers.parse(phone, region)

        # 유효성 검증
        if not phonenumbers.is_valid_number(parsed):
            return ValidationIssue(
                field="phone",
                issue_type="invalid_format",
                message=f"전화번호 형식이 올바르지 않습니다: {phone}",
            )

        # 가능한 번호인지 확인
        if not phonenumbers.is_possible_number(parsed):
            return ValidationIssue(
                field="phone",
                issue_type="invalid_format",
                message=f"전화번호가 불가능한 형식입니다: {phone}",
            )

        return None

    except NumberParseException:
        return ValidationIssue(
            field="phone",
            issue_type="invalid_format",
            message=f"전화번호를 해석할 수 없습니다: {phone}",
        )


def validate_required_field(
    field_name: str, value: str | None, min_length: int = 1
) -> ValidationIssue | None:
    """
    필수 필드를 검증합니다.

    Args:
        field_name: 필드명
        value: 필드 값
        min_length: 최소 길이

    Returns:
        ValidationIssue if invalid, None if valid
    """
    if not value or len(value.strip()) < min_length:
        return ValidationIssue(
            field=field_name,
            issue_type="empty",
            message=f"{field_name} 필드가 비어 있습니다.",
        )

    return None


def calculate_field_confidence(
    field_name: str, value: str | None, validation_issue: ValidationIssue | None = None
) -> int:
    """
    필드의 신뢰도를 계산합니다 (규칙 기반).

    신뢰도 점수 기준:
    - 0: 빈 값
    - 30: 형식 오류
    - 70: 형식 검증 통과 (필수 아닌 필드)
    - 90: 명확한 추출 (필수 필드이며 형식 검증 통과)

    Args:
        field_name: 필드명
        value: 필드 값
        validation_issue: 검증 문제 (있으면 신뢰도 낮음)

    Returns:
        신뢰도 (0~99)
    """
    # 빈 값
    if not value or len(value.strip()) == 0:
        return 0

    # 검증 문제가 있으면
    if validation_issue:
        if validation_issue.issue_type == "empty":
            return 0
        elif validation_issue.issue_type in ["invalid_format", "missing_in_original"]:
            return 30

    # 필수 필드 (name, company, email, phone)는 90점
    essential_fields = ["name", "company", "email", "phone"]
    if field_name in essential_fields:
        return 90

    # 선택 필드는 70점
    return 70


def validate_extracted_data(
    extracted: dict[str, str | None], required_fields: list[str] | None = None
) -> list[ValidationIssue]:
    """
    추출된 데이터를 검증합니다.

    Args:
        extracted: 추출된 데이터 딕셔너리
        required_fields: 필수 필드 목록 (기본값: name, company)

    Returns:
        검증 문제 목록
    """
    if required_fields is None:
        required_fields = ["name", "company"]

    issues: list[ValidationIssue] = []

    # 필수 필드 검증
    for field in required_fields:
        value = extracted.get(field)
        issue = validate_required_field(field, value)
        if issue:
            issues.append(issue)

    # 이메일 검증
    email = extracted.get("email")
    if email:
        issue = validate_email(email)
        if issue:
            issues.append(issue)

    # 전화번호 검증
    phone = extracted.get("phone")
    if phone:
        issue = validate_phone(phone)
        if issue:
            issues.append(issue)

    return issues


def calculate_all_confidences(
    extracted: dict[str, str | None], issues: list[ValidationIssue]
) -> dict[str, int]:
    """
    모든 필드의 신뢰도를 계산합니다.

    Args:
        extracted: 추출된 데이터 딕셔너리
        issues: 검증 문제 목록

    Returns:
        필드명_confidence: 신뢰도 딕셔너리
    """
    # 필드별로 issues를 매핑
    field_issues = {issue.field: issue for issue in issues}

    confidences = {}
    fields = ["name", "company", "department", "job_title", "email", "phone", "address", "website"]

    for field in fields:
        value = extracted.get(field)
        issue = field_issues.get(field)
        confidence = calculate_field_confidence(field, value, issue)
        confidences[f"{field}_confidence"] = confidence

    return confidences
