# 개발 환경 가이드

실행 방법은 [QUICK_START.md](QUICK_START.md), 백엔드 차이는 [BACKEND_SELECTION.md](BACKEND_SELECTION.md)를 먼저 보세요. 이 문서는 개발·테스트·코드 품질 규칙을 다룹니다.

## 프로젝트 구조

```
R-Lead/
├── backend/                  # FastAPI
│   ├── app/
│   │   ├── api/              # cards(업로드), leads(리드·대기열), export(CSV·통계)
│   │   ├── models/           # Lead, ProcessingJob (SQLAlchemy)
│   │   ├── schemas/          # Pydantic 입출력 모델
│   │   ├── services/
│   │   │   ├── extractors/   # gemini/openai/claude/mock + common(프롬프트·파싱·빈 이미지 검사)
│   │   │   ├── extraction_service.py   # 추출 작업 (다중 명함, 재시도)
│   │   │   ├── background_worker.py    # 5초 주기 워커, 중단 작업 복구
│   │   │   ├── storage.py              # S3/MinIO, 서명 URL
│   │   │   ├── duplicate_checker.py, crm_service.py, crm/
│   │   └── utils/            # validation(규칙 검증), image
│   ├── alembic/versions/     # 000~003 마이그레이션
│   └── tests/                # 외부 서비스 없이 실행되는 테스트
├── backend-spring/           # Spring Boot (Gradle) — src/main/java/com/rainbow/rlead/{api,service,domain,config}
└── frontend/src/
    ├── components/           # CameraCapture, LeadList, LeadReview, ExportPanel, Dashboard, QueueStatus, OfflineQueueManager
    ├── services/             # api.ts(API 클라이언트), offline-queue.ts(IndexedDB)
    ├── ui/                   # 토스트/확인 모달, 상태 배지
    └── types/, utils/, config/
```

## 처리 흐름과 설계 원칙 (구현 위치)

1. **업로드** (`api/cards.py` / `CardController`): 이미지 형식·크기 검증 → 저장소 저장 → 리드(`uploaded`) + 추출 작업 등록. `client_uuid` 로 재전송 중복을 막습니다.
2. **추출 작업** (`extraction_service.py` / `ExtractionService`):
   - 원본은 저장소에 두고 AI 에는 긴 변 2048px 이하로 줄인 이미지를 보냅니다.
   - **빈 이미지는 AI 호출 전에 거부**합니다 (LLM 이 빈 사진에도 그럴듯한 값을 지어내는 문제 방지).
   - AI 는 `{"cards": [...]}` 로 명함 여러 장을 돌려줍니다. 첫 장은 원래 리드, 나머지는 같은 사진·수집 정보를 가진 새 리드(`card_index` 1,2,…)가 됩니다. 사진 속 명함이 없으면 실패(재시도 없음).
   - 일시 오류는 최대 3회 재시도(재시도 횟수×20초 간격), 이후 `failed`. 서버 재시작 시 `processing` 으로 남은 작업은 `pending` 으로 복구합니다.
3. **검증** (`utils/validation.py` / `ValidationService`): LLM 신뢰도에 의존하지 않고 규칙으로 계산합니다 — 빈 칸 0, 형식 오류 30, 선택 필드 통과 70, 핵심 필드 통과 90. 담당자가 값을 고치면 현재 값 기준으로 다시 계산합니다.
4. **담당자 확인** → 승인된 값만 CRM 작업(`register_crm`) 또는 CSV 로 넘어갑니다. AI 값은 승인 없이 CRM 에 들어가지 않습니다.

개인정보: 로그에 이름·연락처·사진 주소를 남기지 않습니다. 그래서 SQL 로그(`SQL_ECHO`)는 기본 꺼져 있고, 검증 로그에는 값 대신 필드명만 출력합니다.

## 테스트와 코드 품질

### 백엔드 (FastAPI)

```bash
cd backend
python -m pytest -q          # 31개: API, E2E(업로드→추출→수정→승인), 재시도, 다중 명함, CSV 등
ruff check app tests && ruff format --check app tests
mypy app                     # strict
```

테스트는 임시 SQLite·메모리 저장소·mock/가짜 추출기를 쓰므로 PostgreSQL, MinIO, AI 키가 필요 없습니다 (`tests/conftest.py`).

### 백엔드 (Spring)

```bash
cd backend-spring
./gradlew test               # ExtractorSupportTest 등
./gradlew build
```

### 프론트엔드

```bash
cd frontend
npm run type-check && npm run lint && npm run build
npx prettier --check src
```

## 마이그레이션 (FastAPI)

```bash
cd backend
alembic upgrade head                         # 최신으로
alembic revision -m "설명"                   # 새 마이그레이션 (모델 변경 시)
alembic current                              # 현재 버전
```

운영 DB 에는 컬럼을 추가하는 마이그레이션만 적용하세요. 컨테이너는 시작할 때 `alembic upgrade head` 를 자동 실행합니다.

## 주의사항

- **비밀값**(API 키 등)은 환경 변수/`.env` 로만 다루고 커밋하지 않습니다. 예시는 `.env.example` 에만 둡니다.
- **AI 비용/할당량**: 개발 중 반복 테스트는 `AI_PROVIDER=mock` 을 쓰세요. 무료 할당량을 넘으면 `429`, 과부하면 `503` 이 나며 목록/리뷰 화면에 사유가 표시됩니다.
- **PWA 캐시**: UI 를 바꿨는데 반영되지 않으면 서비스 워커를 해제하세요 (개발자도구 → Application → Service Workers → Unregister).
- **Windows 콘솔 인코딩**: FastAPI 는 시작 시 stdout 을 UTF-8 로 맞춥니다. Spring 로그의 한글이 깨져 보이면 콘솔 코드 페이지 문제입니다(동작에는 영향 없음).
- **Spring 환경 변수 우선순위**: `.env` 값이 OS 환경 변수보다 우선합니다(예: `PORT`). 포트를 임시로 바꾸려면 `./gradlew bootRun --args="--server.port=8081"`.

## 문제 해결

```bash
docker compose ps && docker compose logs -f backend    # 컨테이너 상태/로그
docker compose restart minio                           # MinIO 재시작
alembic current                                        # 마이그레이션 버전 확인
rm -rf frontend/node_modules && (cd frontend && npm install)   # 프론트 의존성 재설치
```
