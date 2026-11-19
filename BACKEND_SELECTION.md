# 백엔드 선택 가이드

R-Lead는 **같은 REST API**를 제공하는 두 가지 백엔드 구현을 포함합니다. 프론트엔드는 `/api` 만 호출하므로, 프록시 대상만 바꾸면 어느 쪽으로도 동작합니다.

| | FastAPI (기본) | Spring Boot |
|---|---|---|
| 위치 | `backend/` | `backend-spring/` |
| 언어/프레임워크 | Python 3.12 · FastAPI · SQLAlchemy(async) | Java 17 · Spring Boot 3.3 · Spring Data JPA |
| AI 호출 | OpenAI/Anthropic SDK, Gemini REST | **Spring AI 1.0.0-M4** (OpenAI·Claude 스타터, Gemini는 OpenAI 호환 엔드포인트) |
| DB | PostgreSQL (운영) / SQLite (테스트) | SQLite |
| 마이그레이션 | Alembic (`alembic upgrade head`) | Hibernate `ddl-auto: update` |
| API 문서 | http://localhost:8000/api/docs | http://localhost:8080/swagger-ui.html |
| 포트 | 8000 | 8080 |
| CRM 연동 | HubSpot / Salesforce 구현됨 (`CRM_TYPE`) | 미구현 (승인 시 작업만 완료 처리, CSV 사용) |

## 선택하는 방법

### 로컬 개발

프론트엔드 개발 서버(Vite)가 시작할 때 `VITE_BACKEND_TYPE` 을 읽어 `/api` 프록시 대상을 정합니다.

```bash
# frontend/.env
VITE_BACKEND_TYPE=fastapi       # → http://localhost:8000  (VITE_FASTAPI_URL 로 변경 가능)
# VITE_BACKEND_TYPE=springboot  # → http://localhost:8080  (VITE_SPRINGBOOT_URL 로 변경 가능)
```

값을 바꾼 뒤에는 `npm run dev` 를 다시 시작하세요. 임시로 바꾸려면 환경 변수로 지정합니다:

```bash
VITE_BACKEND_TYPE=springboot npm run dev           # macOS/Linux
$env:VITE_BACKEND_TYPE="springboot"; npm run dev   # PowerShell
```

### Docker

nginx 가 `/api` 를 `BACKEND_URL` 로 프록시합니다.

```bash
# FastAPI (기본)
docker compose up -d --build

# Spring Boot
BACKEND_URL=http://backend-spring:8080 docker compose --profile spring up -d --build minio backend-spring frontend
```

두 백엔드를 동시에 띄워도 되지만 DB가 다르므로(PostgreSQL vs SQLite) **데이터는 공유되지 않습니다.**

## 기능 호환성

두 백엔드 모두 아래를 지원하며 응답 필드가 같습니다.

- 명함 업로드 (단일/다중), 업로드 전 이미지 형식·크기 검증
- **한 사진에 명함 여러 장** → 명함 수만큼 리드 생성 (`card_index`, 사진 공유)
- AI 추출 (Gemini / OpenAI / Claude), 빈 이미지·명함 아님 거부 (지어내기 방지)
- 규칙 기반 검증·필드별 정확도(0/30/70/90), 수정 시 재검증, 중복 리드 표시
- 리드 조회/수정/승인/거부/삭제, 일괄 재추출·삭제
- 처리 대기열: 자동 재시도(최대 3회, 간격 증가), 실패 사유(`failed_leads`), 재시작 시 중단된 작업 복구
- 상태별 CSV 내보내기(UTF-8 BOM, 수식 주입 방지)와 대시보드 통계 (`/api/export/*`)
- 오류 응답은 `{"message": ...}` (FastAPI 는 `detail` 도 함께 쓰며 프론트엔드가 둘 다 처리)
- 사진은 비공개 저장소에 두고 서명 URL(1시간)로 조회, 같은 사진을 쓰는 마지막 리드를 지울 때만 삭제

## 환경 변수 (공통)

| 변수 | 설명 |
|---|---|
| `AI_PROVIDER` | `gemini` / `openai` / `claude` / `mock` |
| `GEMINI_API_KEY`, `OPENAI_API_KEY`, `CLAUDE_API_KEY` | 선택한 공급자의 키 |
| `GEMINI_MODEL` | Gemini 모델 (기본 `gemini-flash-latest`). 503/429 시 교체 |
| `S3_ENDPOINT` | 백엔드가 저장소에 접근하는 주소 |
| `S3_PUBLIC_ENDPOINT` | **브라우저**가 사진을 여는 주소 (Docker 에서 `http://localhost:9000`). 비우면 `S3_ENDPOINT` |
| `S3_BUCKET_NAME`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | 저장소 설정 |
| `IMAGE_RETENTION_DAYS` | 사진 보관 기간 (FIXME: 정책 확정 필요) |

FastAPI 전용: `DATABASE_URL`(`postgresql+asyncpg://...`), `SECRET_KEY`(32자 이상), `CRM_TYPE`, `SQL_ECHO`(SQL 로그 — 개인정보가 출력되므로 개발 중에만).
Spring 전용: `DATABASE_URL`(`jdbc:sqlite:...`), `PORT`, `SQL_ECHO`.

## 선택 기준

- **FastAPI**: CRM(HubSpot/Salesforce) 연동이 필요하거나 PostgreSQL 로 운영할 때, Python AI 생태계를 그대로 쓰고 싶을 때. (CLAUDE.md 의 기본 스택)
- **Spring Boot**: 사내 Java 인프라에 맞춰야 할 때. 현재는 SQLite 기반이라 단일 서버 데모/소규모 운영에 적합하며, CRM 연동은 추가 구현이 필요합니다.

## 문제 해결

| 증상 | 확인 |
|---|---|
| 프론트가 백엔드에 연결되지 않음 | 백엔드 포트(8000/8080)와 `VITE_BACKEND_TYPE`, Vite 재시작 여부 |
| 포트 충돌 | `netstat -ano \| findstr :8000` (Spring 은 `:8080`) |
| Spring `database is locked` | 같은 `rlead.db` 를 쓰는 인스턴스가 둘 이상 떠 있지 않은지 확인 |
| Spring 이 `postgresql://...` 로 연결 시도 | 프로젝트 루트 `.env` 는 FastAPI용입니다. Spring 은 `backend-spring/.env` 를 우선 읽습니다 |
