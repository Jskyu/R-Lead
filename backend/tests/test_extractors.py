"""추출기 공통 로직(응답 파싱, 빈 이미지 검사)과 검증 규칙 단위 테스트."""

import pytest
from conftest import make_blank_image, make_card_image

from app.api.export import _safe
from app.services.extractors.common import UnreadableCardError, is_blank_image, parse_cards
from app.utils.validation import calculate_all_confidences, validate_extracted_data


def test_parse_cards_returns_one_entry_per_card_in_order() -> None:
    text = json_text(
        {
            "cards": [
                {"name": "가", "company": "A사"},
                {"name": "나", "company": "B사"},
                {"name": None, "company": ""},  # 모든 칸이 비면 건너뜀
                {"name": "다", "company": "C사"},
            ]
        }
    )
    cards = parse_cards(text)
    assert [c.name for c in cards] == ["가", "나", "다"]


def test_parse_cards_accepts_code_block_legacy_object_and_null_strings() -> None:
    cards = parse_cards('```json\n{"name": "홍길동", "department": "null", "email": "a@b.co"}\n```')
    assert len(cards) == 1
    assert cards[0].name == "홍길동"
    assert cards[0].department is None


@pytest.mark.parametrize("text", ['{"cards": []}', '{"name": null, "company": null}', "[]"])
def test_parse_cards_rejects_when_nothing_readable(text: str) -> None:
    with pytest.raises(UnreadableCardError):
        parse_cards(text)


def test_parse_cards_rejects_empty_response() -> None:
    with pytest.raises(RuntimeError):
        parse_cards("   ")


def test_blank_image_detection() -> None:
    assert is_blank_image(make_blank_image()) is True
    assert is_blank_image(make_card_image()) is False
    assert is_blank_image(b"not an image") is False  # 판단 불가하면 AI 로 넘긴다


def test_validation_and_confidence_rules() -> None:
    data = {"name": "홍길동", "company": "ABC", "email": "bad-email", "phone": "010-1234-5678"}
    issues = validate_extracted_data(data)
    assert [i.field for i in issues] == ["email"]
    confidences = calculate_all_confidences(data, issues)
    assert confidences["email_confidence"] == 30
    assert confidences["name_confidence"] == 90
    assert confidences["department_confidence"] == 0  # 빈 칸


def test_validation_messages_are_korean_and_phone_parse_error_has_no_library_text() -> None:
    issues = validate_extracted_data({"name": "a", "company": "b", "phone": "abc"})
    phone_issue = next(i for i in issues if i.field == "phone")
    assert "supplied" not in phone_issue.message.lower()


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ('=HYPERLINK("http://x")', '\'=HYPERLINK("http://x")'),
        ("@cmd", "'@cmd"),
        ("+82-10-1234-5678", "+82-10-1234-5678"),  # 국제 전화번호 형식은 그대로
        ("홍길동", "홍길동"),
        (7, 7),
    ],
)
def test_csv_formula_injection_guard(value: object, expected: object) -> None:
    assert _safe(value) == expected


def json_text(obj: object) -> str:
    import json

    return json.dumps(obj, ensure_ascii=False)
