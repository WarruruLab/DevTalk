# ADR-019: AI 관련 도메인과 서비스의 패키지 분리 및 재구성

## 상태
Accepted

## 결정 시점
2026-01-27

## 배경
LLM기능이 추가되면서, 관련 클래스들이 기능적 응집도 즉 로직은 service, domain(엔티티 및 인터페이스)보다는 계층적 편의성에 따라 배치되어 있었다.

- **문제점 1 :** `LlmClient`, `LlmStreamClient` 같은 인터페이스는 외부 시스템과 소통하는 역할을 수행하므로 도메인 영역에 정의되어야 하나, 서비스 계층(`service/llm`)에 위치
- **문제점 2 :** `AiStreamService`, `AiMessageService` 등 AI 로직을 조율하는 핵심 서비스들이, 단순 CRUD를 담당하는 `service/message` 패키지에 섞여 있어 역할 구분이 안됨
- **문제점 3 :** `service/llm` 패키지에는 인터페이스, DTO, 로직이 혼재되어 있어, 패키지만 보고 역할을 파악하기 어려웠다.

이로 인해 프로젝트가 확장될수록 AI 관련 로직과 일반 메시지 관리 로직 간의 결합도가 높아지고, 아키텍처 분리가 흐려질 위험이 있었다.

## 결정
우리는 AI 연동 기능을 **핵심 도메인 능력**으로 서비스 계층의 역할을 명확히 하기 위해 패키지 구조를 다음과 같이 재구성한다.

1. **인터페이스 및 DTO의 도메인 이동:**
    - `LlmClient`, `LlmStreamClient` 인터페이스와 관련 DTO(`LlmRequest`, `LlmMessage` 등)를 `domain/llm` 패키지로 이동한다.
    - 이는 "AI와 대화하는 능력"을 도메인의 핵심 명세로 정의함을 의미한다.

2. **AI 서비스의 응집:**
    - `service/message`에 있던 `AiStreamService`, `AiMessageService`를 `service/llm` 패키지로 이동한다.
    - 이제 `service/llm`은 AI 관련 흐름 제어를 전담한다.

3. **일반 서비스의 경량화:**
    - `service/message` 패키지에는 순수 DB CRUD를 담당하는 `MessageService`만 남겨둔다.

4. **보조 로직의 위치 유지:**
    - `LlmPromptComposer`와 `SessionSummaryService`는 애플리케이션 흐름을 돕는 역할이므로 `service/llm` 내부에 유지한다.

## 이유
이 구조를 선택한 근거는 **관심사의 분리(Separation of Concerns)**와 **의존성 규칙(Dependency Rule)** 준수이다.

- **도메인 순수성 강화:** 인터페이스를 도메인 영역에 둠으로써, 서비스 계층은 도메인이 정의한 명세에 의존하게 되고, 인프라 계층은 이를 구현하게 되어 의존성 방향이 올바르게 정렬된다.
- **기능적 응집도 향상:** `service/llm`만 보면 AI 관련 비즈니스 흐름을 모두 파악할 수 있고, `service/message`만 보면 데이터 관리 흐름을 파악할 수 있게 되어 유지보수성이 높아진다.
- **확장성:** 향후 AI 모델이 변경되거나 로직이 복잡해져도, `message` 패키지나 다른 도메인에 영향을 주지 않고 `llm` 패키지 내부에서만 변경을 격리할 수 있다.

## 결과
패키지 구조는 다음과 같이 변경된다.

- **`com.devtalk.devtalk.domain.devtalk.llm` (신설)**
    - `LlmClient` (Interface), `LlmStreamClient` (Interface)
    - `LlmRequest`, `LlmResponse` (DTO) 등
- **`com.devtalk.devtalk.service.devtalk.llm` (변경)**
    - `AiStreamService` (Class), `AiMessageService` (Class)
    - `SessionSummaryService` (Class), `LlmPromptComposer` (Class)
- **`com.devtalk.devtalk.service.devtalk.message` (변경)**
    - `MessageService` (Class) - 순수 CRUD 전담
