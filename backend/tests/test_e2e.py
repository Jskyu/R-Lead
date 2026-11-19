"""E2E tests for the core business card processing flow (외부 서비스 없이 실행)."""

import json
from datetime import UTC, datetime, timedelta

import pytest
from conftest import FakeStorage
from httpx import ASGITransport, AsyncClient
from sqlalchemy import select

from app.database import AsyncSessionLocal
from app.main import app
from app.models.processing_job import JobStatus, ProcessingJob
from app.schemas.extraction import ExtractedData
from app.services.background_worker import background_worker
from app.services.extraction_service import extraction_service
from app.services.extractors.common import UnreadableCardError


@pytest.fixture
async def client():
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        yield c


def _metadata(uuid: str, **extra: object) -> dict[str, str]:
    return {
        "metadata": json.dumps(
            {
                "client_uuid": uuid,
                "event_name": "로보월드 2026",
                "booth_number": "A-123",
                "collected_by": "김영업",
                "interested_products": ["협동 로봇"],
                "marketing_consent": True,
                **extra,
            },
            ensure_ascii=False,
        )
    }


async def _upload(client: AsyncClient, image: bytes, uuid: str) -> int:
    files = {"image": ("card.jpg", image, "image/jpeg")}
    response = await client.post("/api/cards/upload", files=files, data=_metadata(uuid))
    assert response.status_code == 200, response.text
    return int(response.json()["lead_id"])


async def _run_worker() -> None:
    await background_worker.process_pending_jobs()


async def _make_job_retryable_now() -> None:
    """재시도 대기(백오프)를 건너뛰기 위해 started_at 을 과거로 돌린다."""
    async with AsyncSessionLocal() as db:
        jobs = (await db.execute(select(ProcessingJob))).scalars().all()
        for job in jobs:
            job.started_at = datetime.now(UTC) - timedelta(minutes=10)
        await db.commit()


class FakeExtractor:
    """호출 횟수와 응답을 제어하는 가짜 추출기."""

    def __init__(self, responses: list[object]) -> None:
        self.responses = responses
        self.calls = 0

    async def extract_all(self, image_data: bytes) -> list[ExtractedData]:
        item = self.responses[min(self.calls, len(self.responses) - 1)]
        self.calls += 1
        if isinstance(item, Exception):
            raise item
        return item  # type: ignore[return-value]


def _card(name: str, company: str, email: str, phone: str) -> ExtractedData:
    return ExtractedData(
        name=name, company=company, email=email, phone=phone, department="영업", job_title="대리"
    )


async def test_upload_extract_edit_approve_flow(client: AsyncClient, card_image: bytes) -> None:
    """명함 업로드 → AI 추출 → 검증 → 수정 → 승인 → CRM 작업 생성."""
    lead_id = await _upload(client, card_image, "e2e-1")

    # 업로드 직후에는 UPLOADED, 큐에 대기 중
    lead = (await client.get(f"/api/leads/{lead_id}")).json()
    assert lead["status"] == "uploaded"
    assert lead["collected_by"] == "김영업"
    assert lead["card_image_url"].startswith("http://storage.test/signed/")
    queue = (await client.get("/api/leads/queue/status")).json()
    assert queue["pending_count"] == 1

    await _run_worker()

    lead = (await client.get(f"/api/leads/{lead_id}")).json()
    assert lead["status"] == "extracted"
    assert lead["name"] == "홍길동"
    assert lead["card_index"] is None
    assert lead["field_confidences"]["email_confidence"] == 90
    assert lead["validation_issues"] == []  # 프론트가 쓰는 필드가 응답에 포함된다
    assert lead["ai_extracted_data"]["company"] == "(주)테스트컴퍼니"
    assert lead["image_delete_at"] is not None

    # 상태 필터는 소문자, 목록은 끝 슬래시 유무와 무관
    for path in ("/api/leads/?status=extracted", "/api/leads?status=extracted"):
        assert [item["id"] for item in (await client.get(path)).json()] == [lead_id]

    # 담당자가 잘못된 전화번호로 고치면 검증이 다시 계산된다
    patched = (await client.patch(f"/api/leads/{lead_id}", json={"phone": "12-34"})).json()
    assert patched["modified_fields"] == ["phone"]
    assert any(i["field"] == "phone" for i in patched["validation_issues"])
    assert patched["field_confidences"]["phone_confidence"] < 70

    # 올바른 번호로 다시 고치면 경고가 사라진다. 빈 이메일("")도 거부되지 않는다.
    fixed = (
        await client.patch(f"/api/leads/{lead_id}", json={"phone": "010-9876-5432", "email": ""})
    ).json()
    assert all(i["field"] != "phone" for i in fixed["validation_issues"])

    approved = await client.post(
        f"/api/leads/{lead_id}/approve", json={"approved": True, "updates": {"name": "홍길동2"}}
    )
    assert approved.status_code == 200
    assert approved.json()["status"] == "approved"
    assert approved.json()["name"] == "홍길동2"

    async with AsyncSessionLocal() as db:
        jobs = (
            (await db.execute(select(ProcessingJob).where(ProcessingJob.lead_id == lead_id)))
            .scalars()
            .all()
        )
    assert {j.job_type for j in jobs} == {"extract_card", "register_crm"}

    # CRM 미설정이면 작업만 완료되고 리드는 approved 로 남는다
    await _run_worker()
    assert (await client.get(f"/api/leads/{lead_id}")).json()["status"] == "approved"


async def test_reject_flow(client: AsyncClient, card_image: bytes) -> None:
    lead_id = await _upload(client, card_image, "e2e-reject")
    await _run_worker()
    rejected = await client.post(f"/api/leads/{lead_id}/approve", json={"approved": False})
    assert rejected.json()["status"] == "rejected"


async def test_duplicate_client_uuid_is_not_created_twice(
    client: AsyncClient, card_image: bytes
) -> None:
    first = await _upload(client, card_image, "e2e-dup")
    files = {"image": ("card.jpg", card_image, "image/jpeg")}
    second = await client.post("/api/cards/upload", files=files, data=_metadata("e2e-dup"))
    assert second.json()["duplicate"] is True
    assert second.json()["lead_id"] == first
    assert len((await client.get("/api/leads/")).json()) == 1


async def test_rejects_non_image_empty_and_oversized_upload(client: AsyncClient) -> None:
    text = await client.post(
        "/api/cards/upload",
        files={"image": ("a.txt", b"hello", "text/plain")},
        data=_metadata("x1"),
    )
    assert text.status_code == 400
    empty = await client.post(
        "/api/cards/upload", files={"image": ("a.jpg", b"", "image/jpeg")}, data=_metadata("x2")
    )
    assert empty.status_code == 400
    bad_meta = await client.post(
        "/api/cards/upload",
        files={"image": ("a.jpg", b"123", "image/jpeg")},
        data={"metadata": "not json"},
    )
    assert bad_meta.status_code == 400
    assert (await client.get("/api/leads/")).json() == []


async def test_blank_image_fails_without_retry_and_reports_reason(
    client: AsyncClient, blank_image: bytes
) -> None:
    """빈 이미지는 AI 를 호출하지 않고(지어내기 방지) 재시도 없이 실패로 확정된다."""
    extractor = FakeExtractor([])  # 호출되면 IndexError 로 테스트가 실패한다
    extraction_service.extractor = extractor  # type: ignore[assignment]
    lead_id = await _upload(client, blank_image, "e2e-blank")

    await _run_worker()

    assert extractor.calls == 0
    lead = (await client.get(f"/api/leads/{lead_id}")).json()
    assert lead["status"] == "uploaded"
    queue = (await client.get("/api/leads/queue/status")).json()
    assert queue["pending_count"] == 0
    assert queue["failed_count"] == 1
    assert "빈 이미지" in queue["failed_leads"][0]["error"]


async def test_non_card_response_is_rejected(client: AsyncClient, card_image: bytes) -> None:
    extraction_service.extractor = FakeExtractor(
        [UnreadableCardError("명함으로 인식되지 않는 이미지입니다.")]
    )  # type: ignore[assignment]
    lead_id = await _upload(client, card_image, "e2e-noncard")
    await _run_worker()
    queue = (await client.get("/api/leads/queue/status")).json()
    assert queue["failed_leads"][0]["id"] == lead_id
    assert queue["pending_count"] == 0  # 재시도하지 않는다


async def test_transient_failure_is_retried_with_backoff(
    client: AsyncClient, card_image: bytes
) -> None:
    extractor = FakeExtractor(
        [
            RuntimeError("Gemini API 오류 503"),
            [_card("재시도", "R사", "r@r.co.kr", "010-1111-2222")],
        ]
    )
    extraction_service.extractor = extractor  # type: ignore[assignment]
    lead_id = await _upload(client, card_image, "e2e-retry")

    await _run_worker()  # 1차 실패 → PENDING 으로 복귀
    assert extractor.calls == 1
    assert (await client.get("/api/leads/queue/status")).json()["pending_count"] == 1

    await _run_worker()  # 재시도 간격이 지나지 않아 건너뜀
    assert extractor.calls == 1

    await _make_job_retryable_now()
    await _run_worker()  # 2차 성공
    assert extractor.calls == 2
    lead = (await client.get(f"/api/leads/{lead_id}")).json()
    assert lead["status"] == "extracted"
    assert lead["name"] == "재시도"


async def test_retries_are_exhausted_then_failed(client: AsyncClient, card_image: bytes) -> None:
    extractor = FakeExtractor([RuntimeError("Gemini API 오류 503")])
    extraction_service.extractor = extractor  # type: ignore[assignment]
    await _upload(client, card_image, "e2e-exhaust")
    for _ in range(3):
        await _make_job_retryable_now()
        await _run_worker()
    assert extractor.calls == 3
    queue = (await client.get("/api/leads/queue/status")).json()
    assert queue["pending_count"] == 0
    assert queue["failed_count"] == 1


async def test_stuck_processing_jobs_are_recovered(client: AsyncClient, card_image: bytes) -> None:
    await _upload(client, card_image, "e2e-stuck")
    async with AsyncSessionLocal() as db:
        job = (await db.execute(select(ProcessingJob))).scalars().one()
        job.status = JobStatus.PROCESSING
        await db.commit()
    assert await background_worker.recover_stuck_jobs() == 1
    assert (await client.get("/api/leads/queue/status")).json()["pending_count"] == 1


async def test_multiple_cards_in_one_photo_become_multiple_leads(
    client: AsyncClient, card_image: bytes, fake_storage: FakeStorage
) -> None:
    """한 사진의 명함 3장 → 리드 3개. 사진은 공유하고 마지막 리드를 지울 때만 삭제한다."""
    cards = [
        _card("김가람", "(주)알파테크", "garam.kim@alpha.example", "010-1111-0001"),
        _card("이나래", "(주)베타엔지니어링", "narae.lee@beta.example", "010-1111-0002"),
        _card("박다온", "감마 공인중개사사무소", "daon.park@gamma.example", "010-1111-0003"),
    ]
    extractor = FakeExtractor([cards])
    extraction_service.extractor = extractor  # type: ignore[assignment]
    first_id = await _upload(client, card_image, "e2e-multi")

    await _run_worker()

    leads = sorted((await client.get("/api/leads/")).json(), key=lambda lead: lead["card_index"])
    assert [lead["name"] for lead in leads] == ["김가람", "이나래", "박다온"]
    assert [lead["card_index"] for lead in leads] == [0, 1, 2]
    assert leads[0]["id"] == first_id
    assert {lead["status"] for lead in leads} == {"extracted"}
    assert len({lead["card_image_key"] for lead in leads}) == 1  # 같은 사진 공유
    assert all(
        lead["event_name"] == "로보월드 2026" and lead["collected_by"] == "김영업" for lead in leads
    )
    assert all(
        lead["duplicate_of_lead_id"] is None for lead in leads
    )  # 형제끼리 중복으로 표시하지 않는다

    # 형제 리드를 재추출해도 리드가 늘지 않고, 자기 순서의 명함만 다시 읽는다
    second_id = leads[1]["id"]
    assert (
        await client.post("/api/leads/bulk-reextract", json={"lead_ids": [second_id]})
    ).status_code == 200
    await _run_worker()
    assert len((await client.get("/api/leads/")).json()) == 3
    assert (await client.get(f"/api/leads/{second_id}")).json()["name"] == "이나래"

    # 사진은 마지막 리드를 지울 때만 삭제된다
    key = leads[0]["card_image_key"]
    ids = [lead["id"] for lead in leads]
    assert (await client.delete(f"/api/leads/{ids[0]}")).status_code == 200
    assert key in fake_storage.objects
    bulk = await client.post("/api/leads/bulk-delete", json={"lead_ids": ids[1:]})
    assert bulk.json()["deleted_count"] == 2
    assert key not in fake_storage.objects
    assert (await client.get("/api/leads/")).json() == []


async def test_bulk_endpoints_validate_input(client: AsyncClient) -> None:
    assert (await client.post("/api/leads/bulk-delete", json={"lead_ids": []})).status_code == 400
    assert (
        await client.post("/api/leads/bulk-reextract", json={"lead_ids": [424242]})
    ).status_code == 404
    assert (await client.delete("/api/leads/424242")).status_code == 404


async def test_export_csv_and_stats(client: AsyncClient, card_image: bytes) -> None:
    lead_id = await _upload(client, card_image, "e2e-export")
    await _run_worker()
    await client.post(f"/api/leads/{lead_id}/approve", json={"approved": True})

    stats = (await client.get("/api/export/stats")).json()
    assert stats["total_leads"] == 1
    assert stats["status_counts"]["approved"] == 1
    assert stats["today_leads"] == 1

    csv_response = await client.get("/api/export/csv?status=approved")
    assert csv_response.status_code == 200
    assert csv_response.content.startswith(b"\xef\xbb\xbf")  # Excel 한글 호환 BOM
    text = csv_response.content.decode("utf-8-sig")
    assert "홍길동" in text and "김영업" in text
