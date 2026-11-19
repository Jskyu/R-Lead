# Spring Boot 백엔드 구현 노트

> 구현 내용과 현재 상태 요약입니다. 사용법은 [backend-spring/README.md](backend-spring/README.md), 두 백엔드 비교는 [BACKEND_SELECTION.md](BACKEND_SELECTION.md)를 보세요.

## 현재 상태

FastAPI 백엔드와 같은 REST API/응답 필드를 제공합니다 (CRM 연동 제외).

| 영역 | 구현 |
|---|---|
| 프레임워크 | Spring Boot 3.3.5, Java 17, Gradle |
| AI | Spring AI 1.0.0-M4 — OpenAI(`OpenAiChatModel`), Claude(`AnthropicChatModel`), Gemini(OpenAI 호환 엔드포인트 + `OpenAiChatModel` 를 `GeminiExtractor` 안에서 직접 생성) |
| API 문서 | SpringDoc (`/swagger-ui.html`) |
| 업로드 | 이미지 형식/크기 검증, `client_uuid` 중복 방지, `collected_by` 저장 |
| 추출 | 다중 명함(`card_index`), 빈 이미지·명함 아님 거부, 최대 3회 재시도, 재시작 시 중단 작업 복구 |
| 검증 | 이메일·전화번호 규칙, 신뢰도 0/30/70/90, 수정 시 재검증 |
| 내보내기 | `/api/export/csv`, `/api/export/stats` |
| 저장소 | S3/MinIO, path-style 서명 URL(`S3_PUBLIC_ENDPOINT`), 버킷 자동 생성 |

## 설계 결정과 이유

- **Boot 3.3 + Spring AI M4**: Spring AI 1.0.0-M4 가 Boot 4 와 호환되지 않아 Boot 4.1 에서 3.3.5 로 내렸고, SpringDoc 도 2.6.0 으로 맞췄습니다.
- **Gemini 는 Vertex AI 스타터를 쓰지 않음**: Vertex AI 는 GCP 프로젝트(`project-id`)와 서비스 계정이 필요하지만 현재 키는 AI Studio API 키입니다. 대신 Gemini 의 OpenAI 호환 엔드포인트(`https://generativelanguage.googleapis.com/v1beta/openai`)를 Spring AI OpenAI 모델로 호출합니다. 진짜 OpenAI 용 자동 구성 빈과 충돌하지 않도록 빈으로 등록하지 않고 `GeminiExtractor` 내부에서만 만듭니다.
- **재시도**: Spring AI 기본 재시도(10회, 최대 3분)는 단일 스레드 워커를 오래 막으므로 2회로 줄이고, 작업 단위 재시도(3회, 20초×횟수 간격)는 `ExtractionService`/`JobProcessorService` 가 맡습니다.
- **빈 이미지 방어**: LLM 이 빈 사진에도 그럴듯한 명함 정보를 지어내는 것이 확인되어, 밝기 표준편차로 AI 호출 전에 거부하고 `{"cards": []}` 응답도 거부합니다 (`ExtractorSupport`).
- **상태값 소문자**: API 는 FastAPI 와 같이 소문자(`LeadStatus` 의 `@JsonValue`), 파라미터는 대소문자 무시.
- **SQLite 만 지원**: Hibernate 방언이 SQLite 로 고정되어 있고 JSON 컬럼이 `TEXT` 입니다. PostgreSQL 로 운영하려면 방언·컬럼 정의 조정이 필요합니다.
- **오류 응답**: `ApiExceptionHandler` 가 `{"message": ...}` 로 통일하고 스택트레이스를 응답에 싣지 않습니다.

## 알려진 제한

- CRM 등록(`REGISTER_CRM`)과 감사 메일(`SEND_EMAIL`)은 아직 작업만 완료 처리하는 스텁입니다.
- 로그인(SSO)이 없습니다.
- Gemini `gemini-3-flash-preview` 같은 미리보기 모델은 바뀔 수 있습니다 (`GEMINI_MODEL` 로 교체).
