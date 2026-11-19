"""Google Gemini extractor."""

import base64

import httpx

from app.config import get_settings
from app.schemas.extraction import ExtractedData
from app.services.extractors.common import PROMPT, UnreadableCardError, parse_cards

settings = get_settings()


class GeminiExtractor:
    """Gemini를 사용한 명함 추출기 (REST API 방식)."""

    def __init__(self) -> None:
        if not settings.gemini_api_key:
            raise ValueError("GEMINI_API_KEY is not set")

        self.api_key = settings.gemini_api_key
        self.api_url = (
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"{settings.gemini_model}:generateContent"
        )

    async def extract_all(self, image_data: bytes) -> list[ExtractedData]:
        """Gemini로 명함 데이터를 추출합니다."""
        payload = {
            "contents": [
                {
                    "parts": [
                        {
                            "inline_data": {
                                "mime_type": "image/jpeg",
                                "data": base64.b64encode(image_data).decode("utf-8"),
                            }
                        },
                        {"text": PROMPT},
                    ]
                }
            ],
            "generationConfig": {"temperature": 0.1, "responseMimeType": "application/json"},
        }

        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                response = await client.post(
                    self.api_url,
                    headers={"Content-Type": "application/json", "X-goog-api-key": self.api_key},
                    json=payload,
                )
                response.raise_for_status()
                result = response.json()

            candidates = result.get("candidates") or []
            parts = (candidates[0].get("content") or {}).get("parts") if candidates else None
            if not parts:
                raise RuntimeError("응답에 내용이 없습니다.")
            return parse_cards(parts[0].get("text", ""))

        except UnreadableCardError:
            raise
        except httpx.HTTPStatusError as e:
            status = e.response.status_code
            hint = " (할당량 초과/과부하일 수 있음)" if status in (429, 503) else ""
            raise RuntimeError(f"Gemini API 오류 {status}{hint}") from e
        except Exception as e:
            raise RuntimeError(f"Gemini extraction failed: {e or repr(e)}") from e
