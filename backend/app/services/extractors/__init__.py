"""Extractor factory."""

from app.config import get_settings
from app.services.extractors.base import CardExtractor
from app.services.extractors.claude import ClaudeExtractor
from app.services.extractors.gemini import GeminiExtractor
from app.services.extractors.mock import MockExtractor
from app.services.extractors.openai import OpenAIExtractor

settings = get_settings()


def get_extractor() -> CardExtractor:
    """설정에 따라 적절한 추출기를 반환합니다."""
    if settings.ai_provider == "mock":
        return MockExtractor()
    elif settings.ai_provider == "gemini":
        return GeminiExtractor()
    elif settings.ai_provider == "openai":
        return OpenAIExtractor()
    elif settings.ai_provider == "claude":
        return ClaudeExtractor()
    else:
        raise ValueError(f"Unknown AI provider: {settings.ai_provider}")
