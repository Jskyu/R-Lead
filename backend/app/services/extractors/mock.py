"""Mock extractor for testing without AI API credits."""

from app.schemas.extraction import ExtractedData


class MockExtractor:
    """테스트용 Mock 추출기 - AI API 크레딧 없이 테스트 가능."""

    async def extract_all(self, image_data: bytes) -> list[ExtractedData]:
        """Mock 명함 데이터를 반환합니다 (항상 한 장)."""
        return [
            ExtractedData(
                name="홍길동",
                company="(주)테스트컴퍼니",
                department="개발팀",
                job_title="대리",
                email="hong@test.com",
                phone="010-1234-5678",
                address="서울시 강남구 테헤란로 123",
                website="https://test.com",
            )
        ]
