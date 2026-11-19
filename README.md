# R-Lead

명함 → 리드 자동화 웹 프로그램

> AI가 읽고, 사람이 확인하고, CRM에 쌓인다.

전시회에서 받은 명함을 휴대폰 브라우저로 촬영하면 AI가 이름·회사·연락처를 뽑고, 담당자가 원본 사진과 나란히 확인·승인한 값만 CRM(또는 CSV)으로 넘깁니다. 프로젝트 명세와 설계 원칙은 [CLAUDE.md](CLAUDE.md)를 참고하세요.

## 주요 기능

- **촬영·업로드**: 휴대폰 카메라/앨범에서 여러 장 선택, 업로드 전 긴 변 1,600px로 축소·방향 보정. 행사·부스·수집 담당자는 기기에 기억됩니다.
- **한 사진에 명함 여러 장**: 한 장에 여러 명함이 찍혀 있어도 명함 수만큼 리드가 생깁니다 (사진은 공유, 리드마다 "N번째 명함" 표시).
- **AI 추출**: Gemini / OpenAI / Claude 중 선택. 읽을 수 없는 칸은 지어내지 않고 비워 둡니다. 빈 이미지·명함이 아닌 사진은 AI 호출 전/후에 규칙으로 거부합니다.
- **규칙 기반 검증**: 이메일·전화번호 형식, 빈 칸, 필드별 정확도(0/30/70/90)와 중복 리드 표시. 담당자가 값을 고치면 검증이 다시 계산됩니다.
- **담당자 확인**: 원본 사진 확대 보기, 수정, 승인/거부, "승인하고 다음 명함" 연속 검토.
- **끊겨도 안전**: 연결이 불안정하면 촬영본을 기기(IndexedDB)에 저장하고, 앱을 열 때·온라인 전환 시 자동 재전송합니다.
- **내보내기**: 상태별 CSV (Excel 한글 호환). CRM 연동(HubSpot/Salesforce)은 FastAPI 백엔드에 구현되어 있고, CRM이 정해지기 전에는 CSV로 대신합니다.
- **처리 대기열**: DB 작업 테이블 + 백그라운드 워커, 일시 오류 자동 재시도(최대 3회, 간격 증가), 서버가 중단돼도 `처리 중`으로 남은 작업을 재시작 시 복구합니다.

## 기술 스택

| 영역 | 선택 |
|---|---|
| 프론트엔드 | React 18 + TypeScript(strict) + Vite + **Bootstrap 5**, PWA |
| 백엔드 A | Python 3.12 + FastAPI + SQLAlchemy(async) + Alembic — `backend/` |
| 백엔드 B | Java 17 + Spring Boot 3.3 + **Spring AI 1.0.0-M4** + Gradle — `backend-spring/` |
| DB | FastAPI: PostgreSQL (테스트: SQLite) / Spring: SQLite |
| 저장소 | S3 호환 비공개 저장소 (개발: MinIO), 짧게 열리는 서명 URL로 조회 |
| 배포 | Docker Compose |

두 백엔드는 **같은 REST API**를 제공해서 프론트엔드가 그대로 동작합니다. 선택 방법은 [BACKEND_SELECTION.md](BACKEND_SELECTION.md)를 보세요.

## 빠른 시작 (Docker)

```bash
cp .env.example .env          # GEMINI_API_KEY 등 입력 (.env 는 커밋하지 않습니다)
docker compose up -d --build  # PostgreSQL + MinIO + FastAPI + 프론트엔드
```

- 프론트엔드: <http://localhost:3000>
- FastAPI 문서: <http://localhost:8000/api/docs>
- MinIO 콘솔: <http://localhost:9001> (minioadmin / minioadmin)

Spring Boot 백엔드로 실행하려면:

```bash
BACKEND_URL=http://backend-spring:8080 docker compose --profile spring up -d --build minio backend-spring frontend
```

자세한 실행 방법(로컬 개발 모드 포함)은 [QUICK_START.md](QUICK_START.md), 개발 환경은 [DEVELOPMENT.md](DEVELOPMENT.md)를 참고하세요.

## 프로젝트 구조

```
R-Lead/
├── backend/              # FastAPI 백엔드 (app/, alembic/, tests/)
├── backend-spring/       # Spring Boot 백엔드 (src/, build.gradle)
├── frontend/             # React + Bootstrap 프론트엔드 (src/)
├── docker-compose.yml    # postgres, minio, backend, (backend-spring), frontend
├── .env.example          # 환경 변수 예시
├── CLAUDE.md             # 프로젝트 명세·작업 규칙
└── *.md                  # 가이드 문서
```

## 문서

| 문서 | 내용 |
|---|---|
| [QUICK_START.md](QUICK_START.md) | 실행 방법과 문제 해결 |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 개발 환경, 테스트, 코드 품질 |
| [BACKEND_SELECTION.md](BACKEND_SELECTION.md) | FastAPI / Spring Boot 선택과 차이 |
| [backend-spring/README.md](backend-spring/README.md) | Spring Boot 백엔드 상세 |
| [FIELD_TEST_GUIDE.md](FIELD_TEST_GUIDE.md) | 전시장 현장 시험 절차 |
| [IMPLEMENTATION_REPORT.md](IMPLEMENTATION_REPORT.md), [FINAL_IMPLEMENTATION_REPORT.md](FINAL_IMPLEMENTATION_REPORT.md), [SPRING_BOOT_IMPLEMENTATION.md](SPRING_BOOT_IMPLEMENTATION.md) | 구현 과정 기록 (이력) |

## 테스트·코드 품질

```bash
# 백엔드(FastAPI): 외부 서비스 없이 실행되는 테스트 (SQLite + 가짜 AI/저장소)
cd backend && python -m pytest -q && ruff check app tests && mypy app

# 백엔드(Spring)
cd backend-spring && ./gradlew test

# 프론트엔드
cd frontend && npm run type-check && npm run lint && npm run build
```

## 아직 정하지 않은 것 (FIXME)

CLAUDE.md의 "8. 아직 정하지 않은 것"과 같습니다: 명함 사진을 해외 AI API로 보내도 되는지(개인정보 담당 확인), 사용할 AI 모델, CRM 종류, 로그인 방식(SSO), 배포 환경, 사진 보관 기간, 프로그램 이름.

## 라이선스

사내 전용 도구
