"""OpenAI GPT-4o extractor."""

import base64

from openai import AsyncOpenAI

from app.config import get_settings
from app.schemas.extraction import ExtractedData
from app.services.extractors.common import PROMPT, UnreadableCardError, parse_cards

settings = get_settings()


class OpenAIExtractor:
    """OpenAI GPT-4o를 사용한 명함 추출기."""

    def __init__(self) -> None:
        if not settings.openai_api_key:
            raise ValueError("OPENAI_API_KEY is not set")

        self.client = AsyncOpenAI(api_key=settings.openai_api_key, max_retries=1, timeout=90.0)

    async def extract_all(self, image_data: bytes) -> list[ExtractedData]:
        """OpenAI GPT-4o로 명함 데이터를 추출합니다."""
        image_b64 = base64.b64encode(image_data).decode("utf-8")

        try:
            response = await self.client.chat.completions.create(
                model="gpt-4o",
                temperature=0.1,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": PROMPT},
                            {
                                "type": "image_url",
                                "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"},
                            },
                        ],
                    }
                ],
                max_tokens=2000,
                response_format={"type": "json_object"},
            )
            content = response.choices[0].message.content or ""
            return parse_cards(content)
        except UnreadableCardError:
            raise
        except Exception as e:
            raise RuntimeError(f"OpenAI extraction failed: {e}") from e
