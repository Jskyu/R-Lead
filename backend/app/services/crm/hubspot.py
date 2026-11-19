"""HubSpot CRM integration."""

import httpx

from app.config import get_settings
from app.models.lead import Lead

settings = get_settings()


class HubSpotIntegration:
    """HubSpot CRM 연동."""

    def __init__(self) -> None:
        if not settings.crm_api_key:
            raise ValueError("CRM_API_KEY is not set for HubSpot")

        self.api_key = settings.crm_api_key
        # FIXME: HubSpot API URL은 설정에서 가져오거나 기본값 사용
        self.base_url = settings.crm_api_url or "https://api.hubapi.com"

    async def create_lead(self, lead: Lead) -> str:
        """HubSpot에 리드를 생성합니다."""
        # HubSpot Contact 생성 API
        url = f"{self.base_url}/crm/v3/objects/contacts"

        # 리드 데이터를 HubSpot 형식으로 변환
        properties = {
            "firstname": lead.name or "",
            "lastname": "",  # 한국식 이름은 분리 필요
            "email": lead.email or "",
            "phone": lead.phone or "",
            "company": lead.company or "",
            "jobtitle": lead.job_title or "",
            "address": lead.address or "",
            "website": lead.website or "",
        }

        # 빈 값 제거
        properties = {k: v for k, v in properties.items() if v}

        async with httpx.AsyncClient() as client:
            response = await client.post(
                url,
                json={"properties": properties},
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
            )

            if response.status_code != 201:
                raise Exception(f"HubSpot create failed: {response.status_code} - {response.text}")

            data = response.json()
            return str(data["id"])

    async def update_lead(self, crm_id: str, lead: Lead) -> None:
        """HubSpot의 리드를 업데이트합니다."""
        url = f"{self.base_url}/crm/v3/objects/contacts/{crm_id}"

        properties = {
            "firstname": lead.name or "",
            "email": lead.email or "",
            "phone": lead.phone or "",
            "company": lead.company or "",
            "jobtitle": lead.job_title or "",
        }

        properties = {k: v for k, v in properties.items() if v}

        async with httpx.AsyncClient() as client:
            response = await client.patch(
                url,
                json={"properties": properties},
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
            )

            if response.status_code != 200:
                raise Exception(f"HubSpot update failed: {response.status_code} - {response.text}")

    async def test_connection(self) -> bool:
        """HubSpot 연결을 테스트합니다."""
        url = f"{self.base_url}/crm/v3/objects/contacts"

        async with httpx.AsyncClient() as client:
            try:
                response = await client.get(
                    url,
                    headers={"Authorization": f"Bearer {self.api_key}"},
                    params={"limit": 1},
                )
                return response.status_code == 200
            except Exception:
                return False
