# R-Lead Spring Boot Backend

명함 → 리드 자동화 웹 프로그램의 Spring Boot 백엔드입니다. FastAPI 백엔드(`../backend`)와 **같은 REST API**를 제공하므로 프론트엔드를 그대로 쓸 수 있습니다. ([BACKEND_SELECTION.md](../BACKEND_SELECTION.md))

## 기술 스택

- Java 17, **Spring Boot 3.3.5**, Gradle (Wrapper 포함)
- **Spring AI 1.0.0-M4**: OpenAI·Anthropic 스타터, Gemini 는 Gemini 의 OpenAI 호환 엔드포인트를 Spring AI OpenAI 모델로 호출
- Spring Data JPA + Hibernate, **SQLite** (`rlead.db`)
- AWS SDK v2 (S3/MinIO), SpringDoc OpenAPI (Swagger UI), Lombok, libphonenumber

> Spring AI 1.0.0-M4 는 Spring Boot 3.x 를 요구합니다 (Boot 4.x 와 호환되지 않음). Spring Boot 버전을 올릴 때는 Spring AI 를 함께 올려야 합니다.

## 설정

```bash
cp .env.example .env
```

```properties
AI_PROVIDER=gemini                 # gemini | openai | claude | mock
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-3-flash-preview   # 기본 gemini-flash-latest. 과부하(503)/할당량(429) 시 교체
OPENAI_API_KEY=
CLAUDE_API_KEY=

S3_ENDPOINT=http://localhost:9000  # 백엔드 → 저장소
S3_PUBLIC_ENDPOINT=                # 브라우저 → 저장소 (비우면 S3_ENDPOINT). Docker 에서는 http://localhost:9000
S3_BUCKET_NAME=business-cards      # 버킷이 없으면 시작할 때 자동 생성

DATABASE_URL=jdbc:sqlite:rlead.db
PORT=8080
```

`.env` 는 `DotenvConfig` 가 읽습니다. **`backend-spring/.env` 를 우선** 찾고, 없으면 현재 디렉터리의 `.env` 를 읽습니다 (IDE 가 프로젝트 루트에서 실행해도 루트의 FastAPI용 `.env` 를 잘못 읽지 않도록). `.env` 값이 OS 환경 변수보다 우선합니다.

## 실행

```bash
./gradlew bootRun            # Windows: gradlew.bat bootRun  (또는 start.bat / start.ps1)
./gradlew test
./gradlew bootJar            # build/libs/rlead-1.0.0.jar
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- 헬스 체크: http://localhost:8080/api/cards/health

포트를 임시로 바꾸려면 `./gradlew bootRun --args="--server.port=8081"` (`.env` 의 `PORT` 가 환경 변수보다 우선하기 때문).

## Docker

```bash
# 프로젝트 루트에서
BACKEND_URL=http://backend-spring:8080 docker compose --profile spring up -d --build minio backend-spring frontend
```

SQLite 파일은 `spring-data` 볼륨(`/data/rlead.db`)에 저장됩니다.

## 구조

```
src/main/java/com/rainbow/rlead/
├── api/
│   ├── controller/   CardController(업로드), LeadController(리드·대기열), ExportController(CSV·통계)
│   ├── dto/          요청/응답 DTO (JSON 필드는 snake_case, FastAPI 와 동일)
│   ├── ApiExceptionHandler.java    # 오류 → {"message": ...}, 스택트레이스 비노출
│   └── LeadNotFoundException.java
├── service/
│   ├── CardUploadService, LeadService, StorageService
│   ├── ExtractionService       # 추출 작업: 다중 명함, 빈 이미지 거부, 재시도
│   ├── JobProcessorService     # 5초 주기 @Scheduled 워커, 시작 시 중단 작업 복구
│   ├── ValidationService, DuplicateCheckerService
│   └── extractor/              # CardExtractor, ExtractorFactory, Gemini/OpenAI/Claude/Mock, ExtractorSupport(프롬프트·파싱)
├── domain/           model(Lead, ProcessingJob, LeadStatus…), repository
└── config/           DotenvConfig, WebConfig(CORS·상태 파라미터), SwaggerConfig
```

## 동작 메모

- **상태값**: DB 에는 `EXTRACTED` 처럼 저장하지만 API 는 `extracted` (소문자)로 주고받으며, 요청 파라미터는 대소문자를 구분하지 않습니다.
- **다중 명함**: AI 응답 `{"cards":[...]}` 의 첫 장은 원래 리드, 나머지는 새 리드(`card_index` 1,2,…)로 만들고 사진은 공유합니다. 사진은 마지막 리드를 삭제할 때만 지웁니다.
- **빈 이미지/명함 아님**: `UnreadableCardException` → 재시도 없이 실패, 사유가 `/api/leads/queue/status` 의 `failed_leads` 로 노출됩니다.
- **로그**: 개인정보를 남기지 않습니다. SQL 로그는 `SQL_ECHO=true` 일 때만 출력됩니다.
- **CRM 연동**: 아직 구현되지 않았습니다 (`JobProcessorService` 의 `REGISTER_CRM` 은 작업만 완료 처리). CRM 이 필요하면 FastAPI 백엔드를 쓰거나 이 부분을 구현해야 합니다.
