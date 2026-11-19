"""Export API for leads."""

import csv
from datetime import datetime
from io import StringIO
from typing import Any

from fastapi import APIRouter, Depends, Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.lead import Lead, LeadStatus

router = APIRouter()


def _safe(value: object) -> object:
    """CSV 수식 주입 방지: =,+,-,@ 로 시작하는 텍스트 앞에 작은따옴표를 붙인다 (전화번호 형식은 제외)."""
    if isinstance(value, str) and value[:1] in ("=", "+", "-", "@"):
        import re

        if not re.fullmatch(r"[+-]?[0-9][0-9\-\s()]*", value):
            return "'" + value
    return value


@router.get("/csv")
async def export_leads_csv(
    status: LeadStatus | None = None,
    db: AsyncSession = Depends(get_db),
) -> Response:
    """리드를 CSV 파일로 내보냅니다."""
    # 리드 조회
    stmt = select(Lead).order_by(Lead.created_at.desc())

    if status:
        stmt = stmt.where(Lead.status == status)

    result = await db.execute(stmt)
    leads = result.scalars().all()

    # CSV 생성
    output = StringIO()
    writer = csv.writer(output)

    # 헤더
    writer.writerow(
        [
            "ID",
            "이름",
            "회사",
            "부서",
            "직함",
            "이메일",
            "전화번호",
            "주소",
            "웹사이트",
            "관심 제품",
            "행사명",
            "부스",
            "수집자",
            "수집 일시",
            "마케팅 동의",
            "상태",
            "생성 일시",
        ]
    )

    # 데이터
    for lead in leads:
        writer.writerow(
            _safe(cell)
            for cell in [
                lead.id,
                lead.name or "",
                lead.company or "",
                lead.department or "",
                lead.job_title or "",
                lead.email or "",
                lead.phone or "",
                lead.address or "",
                lead.website or "",
                ", ".join(lead.interested_products or []),
                lead.event_name or "",
                lead.booth_number or "",
                lead.collected_by or "",
                lead.collected_at.strftime("%Y-%m-%d %H:%M:%S") if lead.collected_at else "",
                "예" if lead.marketing_consent else "아니오",
                lead.status if isinstance(lead.status, str) else lead.status.value,
                lead.created_at.strftime("%Y-%m-%d %H:%M:%S"),
            ]
        )

    # CSV 응답 반환
    csv_content = output.getvalue()
    filename = f"leads_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"

    return Response(
        content=csv_content.encode("utf-8-sig"),  # BOM 추가 (Excel 한글 호환)
        media_type="text/csv",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


@router.get("/stats")
async def get_export_stats(db: AsyncSession = Depends(get_db)) -> dict[str, Any]:
    """대시보드용 상세 통계를 반환합니다."""
    from datetime import timedelta

    # 상태별 카운트
    status_counts: dict[str, int] = {}
    for status in LeadStatus:
        stmt = select(Lead).where(Lead.status == status)
        result = await db.execute(stmt)
        count = len(result.scalars().all())
        status_counts[status.value] = count

    # 전체 리드 수
    stmt = select(Lead)
    result = await db.execute(stmt)
    all_leads = result.scalars().all()
    total_leads = len(all_leads)

    # 오늘 수집된 리드
    today = datetime.now().date()
    today_leads = sum(
        1 for lead in all_leads if lead.collected_at and lead.collected_at.date() == today
    )

    # 이번 주 수집된 리드 (월요일 시작)
    today_weekday = today.weekday()
    week_start = today - timedelta(days=today_weekday)
    this_week_leads = sum(
        1 for lead in all_leads if lead.collected_at and lead.collected_at.date() >= week_start
    )

    # 확인 대기 중인 리드 (extracted 상태)
    pending_review = status_counts.get(LeadStatus.EXTRACTED.value, 0)

    return {
        "total_leads": total_leads,
        "status_counts": status_counts,
        "today_leads": today_leads,
        "this_week_leads": this_week_leads,
        "pending_review": pending_review,
    }
