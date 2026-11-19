"""Salesforce CRM integration."""

from app.config import get_settings
from app.models.lead import Lead

settings = get_settings()


class SalesforceIntegration:
    """Salesforce CRM 연동."""

    def __init__(self) -> None:
        if not settings.crm_api_key:
            raise ValueError("CRM_API_KEY is not set for Salesforce")

        # TODO: Salesforce 인증 구현
        # FIXME: Salesforce는 OAuth 2.0 사용, 별도 라이브러리 필요
        # from simple_salesforce import Salesforce
        raise NotImplementedError("Salesforce integration not implemented yet")

    async def create_lead(self, lead: Lead) -> str:
        """Salesforce에 리드를 생성합니다."""
        # TODO: Salesforce API 구현
        raise NotImplementedError("Salesforce create_lead not implemented")

    async def update_lead(self, crm_id: str, lead: Lead) -> None:
        """Salesforce의 리드를 업데이트합니다."""
        raise NotImplementedError("Salesforce update_lead not implemented")

    async def test_connection(self) -> bool:
        """Salesforce 연결을 테스트합니다."""
        raise NotImplementedError("Salesforce test_connection not implemented")
