"""Base CRM integration interface."""

from typing import Protocol

from app.models.lead import Lead


class CRMIntegration(Protocol):
    """CRM 연동 인터페이스."""

    async def create_lead(self, lead: Lead) -> str:
        """
        CRM에 리드를 생성합니다.

        Args:
            lead: 생성할 리드

        Returns:
            CRM에서 발급한 리드 ID

        Raises:
            Exception: CRM 등록 실패 시
        """
        ...

    async def update_lead(self, crm_id: str, lead: Lead) -> None:
        """
        CRM의 리드를 업데이트합니다.

        Args:
            crm_id: CRM 리드 ID
            lead: 업데이트할 리드

        Raises:
            Exception: CRM 업데이트 실패 시
        """
        ...

    async def test_connection(self) -> bool:
        """
        CRM 연결을 테스트합니다.

        Returns:
            연결 성공 여부
        """
        ...
