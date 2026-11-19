"""추출기 공통 로직: 프롬프트, 응답 파싱, 빈 이미지 검사.

LLM 이 빈 이미지에도 그럴듯한 명함 정보를 지어내는 문제를 막기 위해
(1) AI 호출 전 규칙 기반 빈 이미지 검사, (2) "명함 없음" 응답 거부, (3) 빈 결과 거부를 둔다.
"""

import json
from io import BytesIO
from typing import Any

from PIL import Image, ImageStat

from app.schemas.extraction import ExtractedData

PROMPT = """
이 이미지에 있는 명함을 모두 찾아, 각 명함에 실제로 인쇄된 정보만 추출해주세요.
이미지에는 명함이 한 장일 수도, 여러 장일 수도 있습니다.

규칙:
- 명함 한 장당 항목 하나입니다. 명함이 여러 장이면 위에서 아래로, 같은 줄이면 왼쪽에서 오른쪽 순서로 배열에 담으세요.
- 같은 명함을 두 번 넣지 마세요. 다른 명함의 정보를 섞지 마세요.
- 이미지에 명함이 없거나(빈 이미지, 풍경, 문서 등) 글자를 읽을 수 없으면 "cards"를 빈 배열 []로 반환하세요.
- 이미지에 실제로 보이지 않는 값은 절대 추측하거나 만들어내지 말고 null로 반환하세요.
- 예시나 가상의 데이터를 만들어내지 마세요.
- 전화번호는 읽은 그대로 반환하되, 번호가 여러 개면 휴대폰 번호 하나만 "phone"에 넣으세요 (휴대폰이 없으면 대표 번호 하나). 번호를 쉼표로 이어 붙이지 마세요. 팩스(Fax/F) 번호는 phone에 넣지 마세요.

JSON 형식으로만 응답하세요 (다른 텍스트 금지):
{
    "cards": [
        {
            "name": "이름 (읽을 수 없으면 null)",
            "company": "회사명 (읽을 수 없으면 null)",
            "department": "부서 (읽을 수 없으면 null)",
            "job_title": "직함 (읽을 수 없으면 null)",
            "email": "이메일 (읽을 수 없으면 null)",
            "phone": "전화번호 (읽을 수 없으면 null)",
            "address": "주소 (읽을 수 없으면 null)",
            "website": "웹사이트 (읽을 수 없으면 null)"
        }
    ]
}
"""

_FIELDS = ("name", "company", "department", "job_title", "email", "phone", "address", "website")


class UnreadableCardError(Exception):
    """이미지가 명함이 아니거나 읽을 수 있는 글자가 없을 때.

    같은 이미지를 다시 보내도 결과가 같으므로 재시도하지 않는다.
    """


def _clean(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return None if not text or text.lower() == "null" else text


def _load_json(text: str) -> Any:
    cleaned = (text or "").strip()
    if not cleaned:
        raise RuntimeError("AI 응답이 비어 있습니다.")
    if cleaned.startswith("```json"):
        cleaned = cleaned[7:]
    elif cleaned.startswith("```"):
        cleaned = cleaned[3:]
    if cleaned.endswith("```"):
        cleaned = cleaned[:-3]
    return json.loads(cleaned.strip())


def parse_cards(text: str) -> list[ExtractedData]:
    """LLM 응답에서 명함 여러 장을 읽는다.

    ``{"cards": [...]}`` 형식, 배열, 단일 객체(이전 형식)를 모두 받는다.
    읽을 수 있는 명함이 하나도 없으면 UnreadableCardError.
    """
    root = _load_json(text)

    if isinstance(root, dict) and "cards" in root:
        items: list[Any] = root["cards"] if isinstance(root["cards"], list) else []
    elif isinstance(root, list):
        items = root
    elif isinstance(root, dict):
        items = [root]
    else:
        items = []

    cards: list[ExtractedData] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        values = {field: _clean(item.get(field)) for field in _FIELDS}
        if all(v is None for v in values.values()):
            continue  # 모든 칸이 비어 있으면 명함이 아닌 것으로 본다
        cards.append(ExtractedData(**values))

    if not cards:
        raise UnreadableCardError("명함으로 인식되지 않는 이미지입니다. 다시 촬영해 주세요.")
    return cards


def is_blank_image(image_data: bytes, threshold: float = 4.0) -> bool:
    """거의 단색(빈 종이, 렌즈 가림 등)이면 True. 밝기 표준편차가 매우 낮은 경우."""
    try:
        with Image.open(BytesIO(image_data)) as img:
            gray = img.convert("L")
            gray.thumbnail((200, 200))
            stddev = ImageStat.Stat(gray).stddev[0]
        return bool(stddev < threshold)
    except Exception:
        return False
