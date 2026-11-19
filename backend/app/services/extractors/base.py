"""Base extractor interface."""

from typing import Protocol

from app.schemas.extraction import ExtractedData


class CardExtractor(Protocol):
    """명함 데이터 추출 인터페이스."""

    async def extract_all(self, image_data: bytes) -> list[ExtractedData]:
        """
        한 사진에 있는 모든 명함을 추출합니다 (위→아래, 왼쪽→오른쪽 순서).

        Args:
            image_data: 명함 이미지 바이트 데이터

        Returns:
            명함 한 장당 하나씩의 추출 결과

        Raises:
            UnreadableCardError: 명함이 없거나 읽을 수 없을 때 (재시도 불필요)
            Exception: 그 밖의 추출 실패 (재시도 대상)
        """
        ...
