# R-Lead 빠른 시작 가이드

## 준비물

- Docker Desktop (PostgreSQL·MinIO·백엔드·프론트엔드 실행)
- AI API 키 하나 이상 (Gemini / OpenAI / Claude). 키가 없으면 `AI_PROVIDER=mock` 으로 화면 흐름만 확인할 수 있습니다.
- 로컬 개발 모드: Python 3.12, Node.js 20+, (Spring 사용 시) JDK 17

## 1. 환경 변수

```bash
cp .env.example .env
```

`.env`에서 최소한 아래를 채우세요. (`.env`는 커밋되지 않습니다.)

```
AI_PROVIDER=gemini          # gemini | openai | claude | mock
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-flash-latest   # 과부하(503)/할당량(429)이면 다른 모델로 교체
```

> 루트 `.env` 는 Docker Compose 가 읽습니다. FastAPI 를 직접 실행할 때는 `backend/.env` (`backend/.env.example`), Spring 을 직접 실행할 때는 `backend-spring/.env` (`backend-spring/.env.example`) 를 따로 만듭니다.

## 2. 실행 방법

### 방법 1: Docker로 전체 실행 (추천)

```bash
docker compose up -d --build
```

Windows에서는 `start-docker.bat` / `.\start-docker.ps1` 로도 실행할 수 있습니다.

| 서비스 | 주소 |
|---|---|
| 프론트엔드 | http://localhost:3000 |
| FastAPI 문서 | http://localhost:8000/api/docs |
| MinIO 콘솔 | http://localhost:9001 (minioadmin / minioadmin) |

Spring Boot 백엔드로 실행하려면 (SQLite 볼륨을 쓰므로 PostgreSQL 불필요):

```bash
BACKEND_URL=http://backend-spring:8080 docker compose --profile spring up -d --build minio backend-spring frontend
# Spring 문서: http://localhost:8080/swagger-ui.html
```

> PowerShell: `$env:BACKEND_URL="http://backend-spring:8080"; docker compose --profile spring up -d --build minio backend-spring frontend`

코드를 고치면 이미지를 다시 빌드해야 합니다 (`docker compose up -d --build`).

### 방법 2: 로컬 개발 모드 (코드 수정 즉시 반영)

인프라만 Docker로 띄우고 백엔드·프론트엔드는 로컬 프로세스로 실행합니다.

```bash
docker compose up -d postgres minio
```

**FastAPI 백엔드**

```bash
cd backend
py -3.12 -m venv venv && venv\Scripts\activate     # macOS/Linux: python3 -m venv venv && source venv/bin/activate
pip install -r requirements-simple.txt
alembic upgrade head
uvicorn app.main:app --reload --port 8000
```

FastAPI 는 `backend/.env` 를 읽습니다. 처음에는 `cp .env.example .env` (backend 폴더에서) 로 만들고 `GEMINI_API_KEY` 등을 채우세요. 로컬 PostgreSQL 주소는 `postgresql+asyncpg://postgres:postgres@localhost:5432/rlead` 입니다 (`+asyncpg` 가 필요).

**또는 Spring Boot 백엔드**

```bash
cd backend-spring
cp .env.example .env     # AI 키 등 입력
./gradlew bootRun        # Windows: gradlew.bat bootRun  (또는 start.bat / start.ps1)
```

**프론트엔드**

```bash
cd frontend
npm install
# 백엔드 선택: .env 의 VITE_BACKEND_TYPE=fastapi | springboot  (dev 서버 시작 시 읽음)
npm run dev              # http://localhost:5173
```

Windows 스크립트 `start-local.bat` / `.\start-local.ps1` 은 PostgreSQL·MinIO 실행 → 마이그레이션 → FastAPI → 프론트엔드를 한 번에 실행합니다. Spring으로 실행하려면 `.\start-local.ps1 -Backend spring` (CMD: `start-local.bat spring`).

## 3. 중지

```bash
docker compose down            # 컨테이너 중지 (데이터 유지)
docker compose down -v         # 데이터(DB·사진)까지 삭제 — 주의
```

Windows: `stop.bat` / `.\stop.ps1`. 로컬 프로세스는 해당 창에서 Ctrl+C.

## 4. 첫 사용 흐름

1. **명함 촬영** 탭에서 행사·부스·담당자를 입력하고 명함을 촬영(또는 앨범에서 선택)해 업로드
2. 10~20초 후 **리드 관리**에서 `확인 대기` 항목 열기 (한 사진에 여러 장이면 장수만큼 리드가 생김)
3. 원본 사진과 추출 값을 비교하고 필요하면 수정 → **승인하고 다음 명함**
4. **내보내기**에서 승인된 리드를 CSV로 내려받기

## 문제 해결

| 증상 | 확인 |
|---|---|
| 화면이 옛날 디자인 그대로 | 브라우저의 옛 서비스 워커 캐시 — 개발자도구 → Application → Service Workers → Unregister 후 `Ctrl+Shift+R` |
| 추출이 계속 "추출 실패" | 리드 목록/리뷰 화면에 실패 사유가 표시됩니다. `429`/`503` 은 AI 할당량·과부하 → 잠시 후 재추출하거나 `GEMINI_MODEL` 변경 |
| "빈 이미지" / "명함으로 인식되지 않음" | 사진이 비었거나 명함이 아닙니다. 재촬영 (같은 사진은 재시도해도 결과가 같아 자동 재시도하지 않음) |
| 명함 사진이 안 보임 | MinIO 실행 확인, `S3_PUBLIC_ENDPOINT`(브라우저가 접근하는 주소, 기본 `http://localhost:9000`) |
| 포트 충돌 | 3000/5173/8000/8080/5432/9000/9001 사용 중인 프로세스 확인 (`netstat -ano \| findstr :8000`) |
| Spring 이 PostgreSQL 주소로 DB 연결 실패 | 프로젝트 루트 `.env` 는 FastAPI용입니다. Spring 은 `backend-spring/.env` 를 읽습니다 (`backend-spring` 에서 실행) |
| Docker 로그 | `docker compose logs -f backend` |
| 완전 초기화 | `docker compose down -v` 후 다시 `up -d --build` |

더 자세한 내용: [DEVELOPMENT.md](DEVELOPMENT.md), [BACKEND_SELECTION.md](BACKEND_SELECTION.md), [FIELD_TEST_GUIDE.md](FIELD_TEST_GUIDE.md)
