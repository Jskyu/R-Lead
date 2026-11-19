"""Duplicate lead detection service."""

from difflib import SequenceMatcher

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.lead import Lead


class DuplicateChecker:
    """중복 리드 확인 서비스."""

    @staticmethod
    def _normalize_string(s: str | None) -> str:
        """문자열을 정규화합니다 (공백 제거, 소문자)."""
        if not s:
            return ""
        return s.strip().lower()

    @staticmethod
    def _get_korean_initials(text: str) -> str:
        """한글 문자열에서 초성을 추출합니다."""
        # 한글 초성 리스트
        CHOSUNG_LIST = [
            "ㄱ",
            "ㄲ",
            "ㄴ",
            "ㄷ",
            "ㄸ",
            "ㄹ",
            "ㅁ",
            "ㅂ",
            "ㅃ",
            "ㅅ",
            "ㅆ",
            "ㅇ",
            "ㅈ",
            "ㅉ",
            "ㅊ",
            "ㅋ",
            "ㅌ",
            "ㅍ",
            "ㅎ",
        ]

        result = []
        for char in text:
            if "가" <= char <= "힣":
                # 한글 유니코드에서 초성 추출
                code = ord(char) - ord("가")
                chosung_index = code // (21 * 28)
                result.append(CHOSUNG_LIST[chosung_index])
            else:
                result.append(char)

        return "".join(result)

    @staticmethod
    def _calculate_similarity(str1: str, str2: str) -> float:
        """두 문자열의 유사도를 계산합니다 (0.0 ~ 1.0)."""
        if not str1 or not str2:
            return 0.0

        # 정규화
        s1 = DuplicateChecker._normalize_string(str1)
        s2 = DuplicateChecker._normalize_string(str2)

        # SequenceMatcher로 유사도 계산
        return SequenceMatcher(None, s1, s2).ratio()

    async def find_duplicates(
        self,
        email: str | None,
        phone: str | None,
        company: str | None,
        db: AsyncSession,
    ) -> list[Lead]:
        """
        중복 리드를 찾습니다.

        Args:
            email: 이메일 주소
            phone: 전화번호
            company: 회사명
            db: 데이터베이스 세션

        Returns:
            중복 가능성이 있는 리드 목록
        """
        duplicates: list[Lead] = []

        # 1. 이메일 완전 일치 (가장 확실한 중복)
        if email:
            normalized_email = self._normalize_string(email)
            stmt = select(Lead).where(Lead.email.ilike(normalized_email))
            result = await db.execute(stmt)
            email_matches = result.scalars().all()
            duplicates.extend(email_matches)

        # 2. 전화번호 일치
        if phone and not duplicates:
            # 숫자만 추출
            phone_digits = "".join(filter(str.isdigit, phone))
            if phone_digits:
                stmt = select(Lead).where(Lead.phone.ilike(f"%{phone_digits}%"))
                result = await db.execute(stmt)
                phone_matches = result.scalars().all()
                duplicates.extend(phone_matches)

        # 3. 회사명 유사도 검사 (이메일, 전화번호가 없을 때)
        if company and not duplicates:
            # 회사명으로 검색 (부분 일치)
            stmt = select(Lead).where(Lead.company.ilike(f"%{company}%"))
            result = await db.execute(stmt)
            company_leads = result.scalars().all()

            # 유사도 계산
            for lead in company_leads:
                if lead.company:
                    similarity = self._calculate_similarity(company, lead.company)
                    # 유사도 80% 이상이면 중복 가능성
                    if similarity >= 0.8:
                        duplicates.append(lead)

        return duplicates

    async def check_duplicate(
        self,
        email: str | None,
        phone: str | None,
        company: str | None,
        db: AsyncSession,
    ) -> tuple[bool, Lead | None]:
        """
        중복 여부를 확인합니다.

        Args:
            email: 이메일 주소
            phone: 전화번호
            company: 회사명
            db: 데이터베이스 세션

        Returns:
            (is_duplicate, duplicate_lead) 튜플
        """
        duplicates = await self.find_duplicates(email, phone, company, db)

        if duplicates:
            # 가장 최근 리드 반환
            return True, duplicates[0]

        return False, None


# Singleton instance
duplicate_checker = DuplicateChecker()
