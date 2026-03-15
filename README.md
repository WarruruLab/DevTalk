# 🛠 개발톡(DevTalk) — 개발 문제 해결 과정을 기록 자산으로

> **"문제를 풀었다는 결과보다, 어떻게 풀었는지에 대한 사고과정과  해결과정이 더 가치 있다"**
>
> 개발톡(DevTalk)은 개발 중 만나는 문제를 AI와 함께 풀어나가며,
> 그 사고 흐름 전체를 **재사용 가능한 지식 자산**으로 변환하는 도구입니다.

<br/>

## 📍 WarruruLab 전체 파이프라인

> 개발톡(DevTalk)은 **WarruruLab**의 첫 번째 서비스입니다.

| | 개발톡(DevTalk) | MCP Server | DevLog |
|:---:|:---:|:---:|:---:|
| **역할** | AI 대화로 문제 해결 로그 생성 | 메시지를 의미 단위 블록으로 분류 | 블록 선택 → 블로그 초안 생성 |
| **기술** | Spring Boot · Java 21 | FastAPI · Python · Ollama | Spring Boot · Java 21 |
| **출력** | Session · Message 저장 | session_block (tags + code_snippets) | 블로그 글 초안 |

서비스 간 호출은 DevLog가 주도합니다. DevLog가 필요한 시점에 개발톡(DevTalk)과 MCP Server를 직접 호출하는 구조입니다.

```
[Step 1] 메시지 동기화                           구현 완료
DevLog ──── REST ────▶ 개발톡(DevTalk)
            내부 API로 메시지 페이징 수집

[Step 2] 블록 분류                               구현 중
DevLog ──── REST ────▶ MCP Server
            메시지 전달 → session_block 반환

[Step 3] 초안 생성                               구현 중
DevLog ──── REST ────▶ Gemini API
            저장된 블록으로 블로그 초안 생성
```

연관 레포: [MCP Server](https://github.com/WarruruLab/MCP) · [DevLog](https://github.com/WarruruLab/DevLog)

---

<br/>

## 📌 개발톡(DevTalk) 한눈에 보기

```
사용자가 문제 입력
       ↓
AI와 실시간 스트리밍 대화 (SSE)
       ↓
시도 · 실패 · 판단이 모두 Session 로그로 저장
       ↓
사용자가 직접 해결됨(Resolved) 선언 → MCP Server가 블록으로 분류
```

**단순한 AI 채팅이 아닙니다.**
실패도 기록하고, 판단 근거도 남기고, 사고 흐름 자체를 자산으로 만드는 시스템입니다.

<br/>

## 왜 이것을 만들었나

개발하다 보면 문제를 해결하고 나서 **"어떻게 해결했지?"** 를 기억하지 못하는 경우가 많습니다.
AI에게 물어보고, 구글링을 하고, 여러 번 시도하는 과정은 따로 기록하지 않는 한 사라지고 최종 결과만 남습니다.

| 기존 방식 | 개발톡(DevTalk) |
|:---|:---|
| 문제 해결 후 과정이 사라짐 | 시도 · 실패 · 판단이 모두 로그로 남음 |
| 해결 여부를 도구가 판단 | 사람이 직접 Resolved 선언 |
| 블로그 글 작성이 별도 작업 | 로그 → MCP → DevLog로 자동 연결 |

<br/>

## ⚙️ 기술 스택

| 영역 | 기술 | 선택 이유 |
|:---|:---|:---|
| Backend | Spring Boot 4.0.1 · Java 21 | 백엔드 취업 목표에 맞는 주력 스택, 최신 LTS 버전으로 실무 환경 반영 |
| Web | Spring MVC · WebFlux (SSE) | REST API는 MVC, AI 스트리밍 응답은 WebFlux의 Flux로 처리 |
| Database | MySQL 8 · **Spring JDBC** (진행 중) | 아래 DB 전환 전략 참고 |
| AI | Google Gemini API | 외부 SDK 없이 RestClient로 직접 호출해 요청·응답·실패를 코드로 완전히 통제 |
| Frontend | React 19 · TypeScript · Vite | 채팅 UI 빠른 구현을 위한 선택, 타입 안전성 확보 |
| 문서화 | ADR 23개 | 모든 설계 결정의 맥락과 이유를 코드와 함께 추적 |

### DB 전환 전략

JPA를 바로 도입하는 대신, **단계적으로 전환하며 직접 비교**하는 방식을 택했습니다.

```
[1단계] InMemory     →  [2단계] JDBC (현재)  →  [3단계] JPA
  빠른 흐름 검증             SQL 직접 작성              ORM 적용
  구조 확인용                성능 측정 · 최적화           JDBC와 비교
```

JDBC 단계에서 SQL 쿼리를 직접 작성하고, 인덱스 설계와 성능 측정을 먼저 경험합니다.
이후 JPA를 적용해 두 방식의 생산성·성능·트레이드오프를 직접 비교하는 것이 목표입니다.

저장 기술이 바뀌어도 Service 코드는 변경 없이 Repository 구현체만 교체되도록 인터페이스로 분리했습니다.

```
domain/session/SessionRepository.java        ← 인터페이스 (계약, 불변)
infra/persistence/JdbcSessionRepository.java ← 현재 구현체
infra/persistence/JpaSessionRepository.java  ← 예정 구현체
```

<br/>

## 🏗 아키텍처

### 레이어 구조

```
           ┌─────────────────────────────┐
           │       React Frontend        │
           └────────────┬────────────────┘
                        │ 요청 (REST / SSE)
                        ▼
           ┌─────────────────────────────┐
           │      Spring Boot API        │
           │  ├── Controller             │
           │  ├── AiStreamService        │◀── SSE 스트리밍 중계
           │  └── Service Layer          │
           └────────────┬────────────────┘
                        │ 조회 / 저장
                        ▼
           ┌─────────────────────────────┐
           │    Repository Interface     │◀── 저장 기술과 완전 분리
           │  (JdbcSessionRepository     │
           │   JdbcMessageRepository)    │
           └──────┬──────────────┬───────┘
                  │              │
                  ▼              ▼
            MySQL 8.0     Gemini Streaming API
                  │              │
                  └──────┬───────┘
                         │ 응답 반환
                         ▼
           ┌─────────────────────────────┐
           │      Spring Boot API        │
           └────────────┬────────────────┘
                        │ 응답 (JSON / SSE chunk)
                        ▼
           ┌─────────────────────────────┐
           │       React Frontend        │
           └─────────────────────────────┘
```

### 핵심 도메인 설계

**Session** — 하나의 문제 해결 단위

| 필드 | 타입 | 설명 |
|:---|:---|:---|
| sessionId | **UUID** | 고유 식별자 — 선택 이유는 아래 참고 |
| title | String | 문제 제목 |
| status | ACTIVE / RESOLVED | 사람이 직접 판단해서 선언 |
| description | String | 문제 설명 |
| aiSummary | String | AI 요약 (컨텍스트 압축용) |

**Message** — 대화 단위

| 필드 | 타입 | 설명 |
|:---|:---|:---|
| messageId | **UUID** | 고유 식별자 — 선택 이유는 아래 참고 |
| role | USER / AI / SYSTEM | 메시지 주체 |
| status | SUCCESS / FAILED | 실패도 기록 |
| markers | QUESTION / INSIGHT / ATTEMPT / KEY_POINT / REFERENCE | MCP가 블록 분류 시 참고 |
| metadata | - | 토큰 수 · 응답 시간 · 종료 사유 |

**ID 전략: Auto Increment 대신 UUID를 선택한 이유**

InMemory 단계에서 `UUID.randomUUID()`로 바로 생성해 객체에 담을 수 있어 구현이 단순했습니다. Auto Increment는 DB에 INSERT한 뒤 생성된 키를 별도로 조회해야 하는 과정이 필요해 초기 구현 비용이 더 높았습니다.

> UUID와 Auto Increment 간의 성능 트레이드오프는 JPA 도입 시점에 직접 비교 분석한 뒤 변경 여부를 결정할 예정입니다.

<br/>

## 💡 주목할 설계 결정들

### 1. AI 응답 실패를 예외가 아닌 값으로 처리

```java
// LlmResult: sealed interface로 성공/실패 두 가지만 허용
public sealed interface LlmResult permits LlmResult.Success, LlmResult.Failure {
    record Success(String text, LlmFinishReason finishReason, LlmTokenUsage tokenUsage) implements LlmResult {}
    record Failure(LlmFailureCode code, String message, String detail) implements LlmResult {}
}
```

- AI 호출이 실패해도 서버 흐름이 중단되지 않음
- 실패 응답도 `Message(role=AI, status=FAILED)`로 DB에 저장
- "실패도 기록 자산"이라는 프로젝트 철학을 코드로 강제

### 2. LLM 교체 가능한 구조 — 인터페이스 기반 AI 추상화

현재는 Gemini API를 사용하지만, **다른 AI 모델로 교체하거나 함께 사용하는 것이 설계적으로 열려 있습니다.**

```java
// Service는 이 인터페이스만 알고 있습니다
public interface LlmClient {
    LlmResult generate(LlmRequest request);
}

public interface LlmStreamClient {
    Flux<LlmStreamEvent> stream(LlmRequest request);
}
```

| 현재 구현체 | 설명 |
|:---|:---|
| GeminiHttpClient | Gemini REST API 호출 |
| GeminiStreamClient | Gemini Streaming API 호출 |
| MockLlmClient | 테스트용 가짜 응답 |

| 추가 가능한 구현체 | 설명 |
|:---|:---|
| OpenAiHttpClient | GPT-4o, GPT-4.1 등 |
| ClaudeHttpClient | Claude Sonnet / Opus 등 |
| OllamaHttpClient | 로컬 모델 (Llama, Mistral 등) |

어떤 구현체를 사용할지는 설정값 하나로 결정됩니다.

```yaml
llm:
  mode: gemini   # gemini | openai | claude | mock
```

### 3. 컨텍스트 전략: 요약 + 꼬리(Tail) 구조

```
LLM 입력 구성
 ├── [SUMMARY] 이전 대화 압축 요약 (최대 1000자)
 ├── Tail: 최근 N개 메시지 (SYSTEM · FAILED 제외, maxChars 이내)
 └── 최신 USER 질문
```

현재는 최근 메시지 **12개 / 6000자** 이내로 운영 중이며,
실제 사용 데이터가 쌓이면서 **최적값을 점진적으로 조정**해 나갈 계획입니다.

### 4. SSE 스트리밍 아키텍처

```
Frontend                Spring Boot             Gemini API
   │                        │                       │
   │─── GET /ai/stream ────▶│                       │
   │                        │─── POST stream ──────▶│
   │◀── event: start ───────│                       │
   │◀── event: delta ───────│◀─── chunk ────────────│
   │◀── event: delta ───────│◀─── chunk ────────────│
   │◀── event: done ────────│                       │
   │                        │   DB에 최종 저장       │
```

- 사용자는 응답 생성 즉시 확인 가능 (지연 체감 최소화)
- 완료 시점에만 DB 저장

<br/>

## 📁 프로젝트 구조

```
devtalk/
├── backend/
│   └── src/main/java/com/devtalk/devtalk/
│       ├── api/              # Controller · DTO
│       ├── domain/
│       │   ├── session/      # Session · SessionRepository (인터페이스)
│       │   ├── message/      # Message · MessageRepository (인터페이스)
│       │   └── llm/          # LlmClient · LlmResult · LlmRequest
│       ├── service/
│       │   ├── session/      # SessionService
│       │   ├── message/      # MessageService
│       │   └── llm/          # AiStreamService · AiMessageService
│       │       └── context/  # LlmPromptComposer · SessionSummaryService
│       ├── infra/
│       │   ├── llm/          # GeminiStreamClient · GeminiHttpClient · MockLlmClient
│       │   └── persistence/  # JdbcSessionRepository · JdbcMessageRepository
│       └── config/           # LlmConfig · StreamConfig · WebConfig
│
├── frontend/
│   └── src/
│       ├── App.tsx           # 라우팅 (list ↔ chat)
│       ├── SessionList.tsx   # 세션 목록 · 생성 · 편집
│       └── ChatView.tsx      # 채팅 UI · SSE 스트리밍 처리
│
└── docs/
    ├── adr/                  # Architecture Decision Records (ADR-001 ~ ADR-023)
    ├── design/               # 도메인 설계 문서
    ├── troubleshooting/      # 문제 해결 기록
    └── log/                  # 주요 API 호출 로그
```

<br/>

## 📝 ADR (Architecture Decision Record)

모든 설계 결정을 ADR로 기록했습니다. "왜 이렇게 만들었는가"를 코드와 함께 추적할 수 있습니다.

| ADR | 결정 내용 |
|:---|:---|
| ADR-001 | 개발톡(DevTalk) → DevLog 순차 개발 전략 |
| ADR-004 | Session / Message 최소 도메인으로 시작 |
| ADR-006 | 실패도 기록 자산으로 취급 |
| ADR-008 | MessageStatus = 전달 성공이 아닌 **생성 결과** 의미 |
| ADR-009 | Resolved = AI 판단 아닌 **사람의 선언** |
| ADR-011 | InMemory → JDBC → JPA 단계적 저장 전환 전략 |
| ADR-015 | Gemini SDK 대신 RestClient 직접 호출 이유 |
| ADR-018 | 컨텍스트 전략: 요약 + Tail 구조로 전환 |
| ADR-020 | SSE 기반 실시간 스트리밍 도입 |
| ADR-022 | MCP 도입 이유 및 멀티 모듈 아키텍처 전환 결정 |
| ADR-023 | 개발톡(DevTalk) ↔ DevLog REST API 기반 연결 설계 |

<br/>

## 🚀 로컬 실행

### 사전 준비

- Java 21+, Node.js 22+, MySQL 8.0+, Gemini API Key

### 백엔드

```bash
cp backend/.env.example backend/.env
# .env에 LLM_GEMINI_API_KEY, MYSQL_PASSWORD 입력

cd backend
./gradlew bootRun
# → http://localhost:8080
```

### 프론트엔드

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5174
```

### 환경변수

| 변수명 | 설명 | 기본값 |
|:---|:---|:---|
| `LLM_GEMINI_API_KEY` | Gemini API 키 | 필수 |
| `LLM_GEMINI_MODEL` | 사용할 모델명 | `gemini-2.5-flash` |
| `MYSQL_PASSWORD` | DB 비밀번호 | 필수 |
| `MYSQL_URL` | DB 연결 URL | `jdbc:mysql://localhost:3306/devtalk` |
| `LLM_MODE` | `gemini` 또는 `mock` | `gemini` |

> `LLM_MODE=mock` 설정 시 Gemini API 없이도 전체 흐름 테스트 가능
