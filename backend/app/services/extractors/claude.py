"""Anthropic Claude extractor."""

import base64

from anthropic import AsyncAnthropic

from app.config import get_settings
from app.schemas.extraction import ExtractedData
from app.services.extractors.common import PROMPT, UnreadableCardError, parse_cards

settings = get_settings()


class ClaudeExtractor:
    """Claude를 사용한 명함 추출기."""

    def __init__(self) -> None:
        if not settings.claude_api_key:
            raise ValueError("CLAUDE_API_KEY is not set")

        self.client = AsyncAnthropic(api_key=settings.claude_api_key, max_retries=1, timeout=90.0)

    async def extract_all(self, image_data: bytes) -> list[ExtractedData]:
        """Claude로 명함 데이터를 추출합니다."""
        image_b64 = base64.b64encode(image_data).decode("utf-8")

        try:
            response = await self.client.messages.create(
                model="claude-3-5-sonnet-20241022",
                max_tokens=2000,
                temperature=0.1,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image",
                                "source": {
                                    "type": "base64",
                                    "media_type": "image/jpeg",
                                    "data": image_b64,
                                },
                            },
                            {"type": "text", "text": PROMPT},
                        ],
                    }
                ],
            )
            block = response.content[0]
            text = getattr(block, "text", "") or ""
            return parse_cards(text)
        except UnreadableCardError:
            raise
        except Exception as e:
            raise RuntimeError(f"Claude extraction failed: {e}") from e
