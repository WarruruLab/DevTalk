# ADR-013: LLM-연동-방식-및-결과-결정.md

## 상태
Accepted

## 결정 시점
2026-01-13

---

## 배경

DevTalk은 단순한 AI 채팅 서비스가 아니라,  
**AI 호출 과정에서의 판단·실패·한계를 모두 기록 자산으로 남기는 개발 기록 시스템**이다.

이를 위해 LLM 연동 과정에서 다음과 같은 요구사항이 존재했다.

- LLM 호출 실패도 예외로 처리하지 않고 **정상 흐름의 결과로 기록**할 것
- AI 응답 생성 로직은 Service 계층에서 통제할 것
- 특정 LLM 벤더(Gemini)에 대한 의존을 infra 계층으로 한정할 것
- 개발/테스트/데모 환경에서 **외부 API 호출 없이도 시스템이 동작**해야 할 것
- 대화 컨텍스트는 비용과 지연을 고려해 **정책적으로 제한**할 수 있어야 할 것

기존의 Google 공식 SDK(`google-cloud-ai-generativelanguage`) 사용 여부와,  
LLM 호출 결과를 예외로 처리할지 값으로 처리할지에 대한 판단이 필요했다.

---

## 결정

### 1. LLM 연동 방식

- Google 공식 SDK를 사용하지 않고,
- **Spring `RestClient`를 이용해 Gemini API를 직접 호출**한다.
- LLM 연동은 `LlmClient` 인터페이스로 추상화하고,
  - 실제 구현은 `GeminiHttpClient` (infra)
  - 개발/테스트용 구현은 `MockLlmClient`로 분리한다.
- 어떤 구현체를 사용할지는 설정 값(`llm.mode`)으로 결정한다.

### 2️. LLM 호출 결과 모델링

- LLM 호출 결과는 예외가 아닌 **값(Value)** 으로 표현한다.
- `sealed interface LlmResult`를 도입하고,
  - `LlmResult.Success`
  - `LlmResult.Failure`
  두 가지 상태만 허용한다.
- `Failure`에는 실패 코드, 사용자 메시지, 상세 정보(detail)를 포함한다.
- Service/Controller 계층에서는 `LlmResult`를 분기하여
  성공/실패 모두 **메시지 로그로 저장**한다.

### 3. Prompt 입력 구조 분리

- AI에게 전달되는 입력을 명확히 분리한다.
  - `systemPrompt`: AI의 역할·규칙·정책을 정의하는 시스템 레벨 입력
  - `messages`: 대화 히스토리 기반 컨텍스트
- 대화 로그 중 어떤 메시지를 LLM에 전달할지는
  `PromptContextBuilder`에서 정책적으로 결정한다.
- 컨텍스트는 **총 글자 수(maxChars)** 기준으로 제한한다.

---

## 이유

### LLM 연동을 직접 HTTP 호출로 구현한 이유

- 공식 SDK는 예외 중심 처리로 인해
  실패를 “기록 가능한 결과”로 다루기 어렵다.
- 응답 구조·실패 바디·상태 코드를 세밀하게 통제하기 어렵다.
- DevTalk의 “실패도 자산”이라는 철학과 맞지 않는다.
- RestClient 방식은 요청/응답/실패를 전부 코드로 소유할 수 있다.

### LlmResult를 sealed interface로 설계한 이유

- 성공/실패 외의 제3의 상태를 허용하지 않기 위함
- 흔한 이름(`Success`, `Failure`)을 명확한 네임스페이스로 묶기 위함
- 실패를 예외가 아닌 **정상적인 결과 흐름**으로 강제하기 위함
- 호출자는 반드시 결과를 분기해서 해석하도록 유도하기 위함

### systemPrompt를 분리한 이유

- 대화 로그(messages)와 AI 행동 규칙은 성격이 다르다.
- systemPrompt는 저장 대상이 아닌 **정책 입력**이다.
- 컨텍스트 제한(maxChars) 정책의 영향을 받지 않아야 한다.
- 향후 모델 변경(OpenAI, Claude 등) 시에도 개념을 그대로 유지할 수 있다.

---

## 결과

- Service 계층은 LLM 벤더(Gemini)를 전혀 알지 않는다.
- LLM 호출 실패 시에도 HTTP 200 흐름을 유지할 수 있다.
- 실패한 AI 응답도 `Message(role=AI, status=FAILED)` 형태로 로그 자산화할 수 있다.
- 개발 환경에서는 MockLlmClient를 통해 비용 없이 전체 흐름을 검증할 수 있다.
- LLM 연동 구조가 DevTalk의 기록 중심 설계와 일관되게 고정되었다.

---

## 후속 작업

- `AiMessageService.generateAndSave(sessionId)`에서
  `LlmResult` → `Message` 저장 흐름 완성
- AI 실패(detail)를 내부 marker 또는 메타데이터로 저장
- Swagger에 AI 메시지 생성 API 문서화
- 프론트에서 AI 실패 메시지 렌더링 UX 점검
