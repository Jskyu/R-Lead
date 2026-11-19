"""CRM integration factory."""

from app.config import get_settings
from app.services.crm.base import CRMIntegration
from app.services.crm.hubspot import HubSpotIntegration
from app.services.crm.salesforce import SalesforceIntegration

settings = get_settings()


def get_crm_integration() -> CRMIntegration | None:
    """설정에 따라 적절한 CRM 연동을 반환합니다."""
    if settings.crm_type == "none":
        return None
    elif settings.crm_type == "hubspot":
        return HubSpotIntegration()
    elif settings.crm_type == "salesforce":
        return SalesforceIntegration()
    else:
        raise ValueError(f"Unknown CRM type: {settings.crm_type}")
