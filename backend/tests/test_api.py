"""Basic API tests."""

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app


@pytest.fixture
async def client():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c


async def test_root_endpoint(client: AsyncClient) -> None:
    """루트 엔드포인트 테스트."""
    response = await client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["message"] == "R-Lead API"
    assert "version" in data


async def test_health_endpoint(client: AsyncClient) -> None:
    """헬스 체크 엔드포인트 테스트."""
    response = await client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


async def test_api_docs(client: AsyncClient) -> None:
    """API 문서 엔드포인트 테스트."""
    response = await client.get("/api/docs")
    assert response.status_code == 200


async def test_leads_list_accepts_both_trailing_slash_forms(client: AsyncClient) -> None:
    """/api/leads 와 /api/leads/ 모두 리다이렉트 없이 200 (Spring 백엔드와 동일)."""
    for path in ("/api/leads", "/api/leads/"):
        response = await client.get(path)
        assert response.status_code == 200, path
        assert response.json() == []


async def test_unknown_lead_returns_404(client: AsyncClient) -> None:
    response = await client.get("/api/leads/999999")
    assert response.status_code == 404
