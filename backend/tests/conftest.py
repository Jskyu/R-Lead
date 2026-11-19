"""Pytest configuration.

테스트는 외부 서비스(AI, S3/MinIO, PostgreSQL)에 의존하지 않는다.
- DB: 임시 SQLite 파일
- AI: mock 추출기 (필요한 테스트는 가짜 추출기로 교체)
- 저장소: 메모리 저장소
환경 변수는 앱 모듈을 import 하기 전에 설정해야 .env 보다 우선한다.
"""

import os
import tempfile
from collections.abc import AsyncGenerator
from io import BytesIO
from pathlib import Path

_TMP_DIR = Path(tempfile.mkdtemp(prefix="rlead-tests-"))
os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{(_TMP_DIR / 'test.db').as_posix()}"
os.environ["SECRET_KEY"] = "test-secret-key-for-unit-tests-min-32-chars"
os.environ["AI_PROVIDER"] = "mock"
os.environ["DEBUG"] = "false"
os.environ["CRM_TYPE"] = "none"

import pytest  # noqa: E402
import pytest_asyncio  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402
from sqlalchemy import delete  # noqa: E402
from sqlalchemy.ext.asyncio import create_async_engine  # noqa: E402

from app.config import get_settings  # noqa: E402
from app.database import AsyncSessionLocal  # noqa: E402
from app.models.base import Base  # noqa: E402
from app.models.lead import Lead  # noqa: E402
from app.models.processing_job import ProcessingJob  # noqa: E402


class FakeStorage:
    """메모리 저장소 (MinIO 대체)."""

    def __init__(self) -> None:
        self.objects: dict[str, bytes] = {}
        self.deleted: list[str] = []
        self._counter = 0

    async def ensure_bucket_exists(self) -> None:
        return None

    async def upload_image(self, data: bytes, content_type: str = "image/jpeg") -> tuple[str, str]:
        self._counter += 1
        key = f"cards/test-{self._counter}.jpg"
        self.objects[key] = data
        return key, f"http://storage.test/{key}"

    async def download_image(self, key: str) -> bytes:
        return self.objects[key]

    async def generate_presigned_url(self, key: str, expiration: int = 3600) -> str:
        return f"http://storage.test/signed/{key}"

    async def delete_image(self, key: str) -> None:
        self.objects.pop(key, None)
        self.deleted.append(key)

    def calculate_delete_date(self):  # type: ignore[no-untyped-def]
        from datetime import datetime, timedelta

        return datetime.now() + timedelta(days=90)


@pytest_asyncio.fixture(scope="session", autouse=True, loop_scope="session")
async def setup_database() -> AsyncGenerator[None, None]:
    """테스트 DB 테이블을 만들고, 끝나면 정리한다."""
    engine = create_async_engine(get_settings().database_url, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    await engine.dispose()


@pytest_asyncio.fixture(autouse=True, loop_scope="session")
async def clean_tables() -> AsyncGenerator[None, None]:
    """각 테스트 전에 리드/작업 테이블을 비운다."""
    async with AsyncSessionLocal() as db:
        await db.execute(delete(ProcessingJob))
        await db.execute(delete(Lead))
        await db.commit()
    yield


@pytest.fixture(autouse=True)
def fake_storage(monkeypatch: pytest.MonkeyPatch) -> FakeStorage:
    """모든 모듈이 참조하는 저장소 싱글턴의 메서드를 메모리 저장소로 교체한다."""
    from app.services.storage import storage_service

    fake = FakeStorage()
    for name in (
        "ensure_bucket_exists",
        "upload_image",
        "download_image",
        "generate_presigned_url",
        "delete_image",
        "calculate_delete_date",
    ):
        monkeypatch.setattr(storage_service, name, getattr(fake, name))
    return fake


def make_card_image(width: int = 800, height: int = 500, text: str = "Hong Gil-dong") -> bytes:
    """글자가 있는 명함 모양 이미지 (빈 이미지 검사를 통과한다)."""
    img = Image.new("RGB", (width, height), color=(250, 250, 245))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, width, 24], fill=(30, 80, 160))
    for i, line in enumerate([text, "ABC Tech Co., Ltd.", "010-1234-5678", "hong@abc.co.kr"]):
        draw.text((40, 80 + i * 60), line, fill=(20, 20, 20))
        draw.rectangle([40, 110 + i * 60, 40 + 20 * len(line), 114 + i * 60], fill=(60, 60, 60))
    buf = BytesIO()
    img.save(buf, format="JPEG", quality=90)
    return buf.getvalue()


def make_blank_image() -> bytes:
    """완전히 흰 이미지."""
    buf = BytesIO()
    Image.new("RGB", (800, 500), color=(255, 255, 255)).save(buf, format="JPEG")
    return buf.getvalue()


@pytest.fixture
def card_image() -> bytes:
    return make_card_image()


@pytest.fixture
def blank_image() -> bytes:
    return make_blank_image()


@pytest.fixture(autouse=True)
def reset_extractor() -> None:
    """테스트가 추출기를 바꿔도 다음 테스트는 mock 으로 시작한다."""
    from app.services.extraction_service import extraction_service
    from app.services.extractors.mock import MockExtractor

    extraction_service.extractor = MockExtractor()  # type: ignore[assignment]
