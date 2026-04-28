# ADR-011: InMemoryStore를 제거 Repository/Service로 변경

## 상태
Accepted

## 결정 시점
2026-01-05

## 배경
DevTalk 3회차의 목표는 기능 확장이 아니라 구조와 책임이 코드로 만드는 것이다.

기존 구현에서는 빠르게 검증하기 위해 Controller가 `InMemoryStore`를 직접 참조하며 `ConcurrentHashMap`, `List`를 다루고 있었다.  
저장 및 검증이 api 계층에 있어 경계가 흐려졌고, in-memory가 api에 노출되어
향후 JPA/DB로 전환할 때 수정 범위가 커질 우려가 있었다.

또한 “이 로그로 글을 쓸 수 있는가”가 최종 기준인 프로젝트에서,
어디서 어떤 판단(세션 존재/실패 처리)이 이루어지는지가 코드로 드러나야 했다.

## 결정
- `InMemoryStore`를 완전히 제거한다.
- domain 계층에 저장 계약을 정의한다.
    - `SessionRepository`
    - `MessageRepository`
- infra 계층에 in-memory 구현체를 둔다.
    - `InMemorySessionRepository`
    - `InMemoryMessageRepository`
- service 계층을 도입하여 유스케이스 흐름/검증을 담당한다.
    - `SessionService`
    - `MessageService`
- Controller는 Service만 호출하며 저장 기술(Map/List/in-memory)을 알지 못하게 한다.
- Optional은 Repository 반환에서만 사용하고, Service에서 해제(예외 전환)한다.
- 메시지가 없는 상태는 “정상 상태”로 보고 조회 시 `empty list`로 표현한다(null 반환 금지).

## 이유
- 1. api에서 저장 기술을 제거하고, service에서 판단을 맡겨 책임 경계를 명확히 할 수 있다.
- 2. Repository interface를 기준으로 in-memory → JPA/DB로 구현체 교체가 가능해진다.
- 3. 세션 존재 검증과 실패 처리가 Service에 모이면서 “판단의 위치”가 코드로 드러난다.
- 4. 메시지 조회에서 `null` 대신 `empty list`를 사용해 상위 계층의 NPE 및 방어 코드를 줄인다.
- 5. 3회차는 구조 증명이 목적이므로 DTO 분리, 예외 응답 표준화, 동시성 완전 보장 등은 후순위로 둔다.

## 결과
- Controller에서 `InMemoryStore/Map/ConcurrentHashMap/List` 직접 사용이 제거
- Controller → Service → Repository(interface) → infra 구현체 흐름 확정
- `MessageService`에서 세션 존재 검증을 공통 메서드(`verifySession`)로 분리해 중복을 제거
- `findAllBySessionId`는 `null` 대신 `empty list`를 반환하도록 구현해 조회 안정성을 확보
- in-memory `append`는 `putIfAbsent` 기반으로 정리하여 “덮어쓰기”에 의한 메시지 유실 위험을 줄였다(3회차 한정).
