"""Application configuration using Pydantic settings."""

from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # Application
    environment: Literal["development", "production"] = "development"
    debug: bool = True
    # SQL 로그는 바인딩 값(이름·이메일 등 개인정보)을 그대로 출력하므로 DEBUG 와 분리해 기본 꺼 둔다.
    sql_echo: bool = False
    secret_key: str = Field(..., min_length=32)

    # Database
    database_url: str = Field(
        default="sqlite+aiosqlite:///./rlead.db",
        description="PostgreSQL or SQLite connection string",
    )

    # S3 Storage
    s3_endpoint: str = "http://localhost:9000"
    s3_access_key: str = "minioadmin"
    s3_secret_key: str = "minioadmin"
    s3_bucket_name: str = "business-cards"
    s3_region: str = "us-east-1"
    # 브라우저가 접근하는 저장소 주소. Docker 에서는 내부 주소(minio:9000)와 달라서 따로 둔다.
    # 비워 두면 s3_endpoint 를 그대로 사용한다.
    s3_public_endpoint: str | None = None
    image_retention_days: int = 90

    # AI Provider
    # FIXME: Choose one AI provider (openai, gemini, claude, mock)
    ai_provider: Literal["openai", "gemini", "claude", "mock"] = "gemini"
    openai_api_key: str | None = None
    gemini_api_key: str | None = None
    # Gemini 모델. 과부하(503)/할당량(429) 시 다른 모델로 교체
    gemini_model: str = "gemini-flash-latest"
    claude_api_key: str | None = None

    # CRM
    # FIXME: Configure CRM integration
    crm_type: Literal["none", "hubspot", "salesforce"] = "none"
    crm_api_key: str | None = None
    crm_api_url: str | None = None

    # Email (Phase 2)
    smtp_host: str = "smtp.gmail.com"
    smtp_port: int = 587
    smtp_user: str | None = None
    smtp_password: str | None = None
    from_email: str = "noreply@example.com"

    # Authentication
    # FIXME: Configure authentication provider
    auth_provider: Literal["google", "microsoft"] = "google"
    google_client_id: str | None = None
    google_client_secret: str | None = None
    microsoft_client_id: str | None = None
    microsoft_tenant_id: str | None = None

    # Frontend
    frontend_url: str = "http://localhost:5173"

    # CORS
    cors_origins: list[str] = Field(default=["http://localhost:5173", "http://localhost:3000"])

    @property
    def is_development(self) -> bool:
        """Check if running in development mode."""
        return self.environment == "development"

    @property
    def is_production(self) -> bool:
        """Check if running in production mode."""
        return self.environment == "production"


@lru_cache
def get_settings() -> Settings:
    """Get cached application settings."""
    return Settings()
