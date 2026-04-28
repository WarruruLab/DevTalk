# ADR-006: Controller에서 in-memory 저장소를 임시로 사용

## 상태

Accepted

## 결정 시점

2026-01-03

## 배경

2~3회차 단계에서는 프론트엔드와 백엔드 간 실제 통신 흐름을 빠르게 검증하는 것이 목표였다.

- 데이터 영속성 자체는 핵심 문제가 아니었다
- DB, JPA, Repository 설계는 오히려 초기 진행 속도를 늦추고 최소한의 저장 구조는 필요했다

Controller 단에서 임시 저장을 할 것인지,
아니면 Service/Repository를 먼저 도입할 것인지가 선택지로 존재했다.

## 결정

2~3회차에서는 Controller에서 ConcurrentHashMap 기반 in-memory 저장소를 직접 사용

Service와 Repository는 도입하지 않으며,
임시 저장 구조는 이후 회차에서 제거하는 것을 전제로 한다.

## 이유

- 현재 단계의 목표는 통신 검증과 흐름 확인이다
- 저장 로직을 최소화해 핵심 흐름에 집중할 수 있다
- in-memory 구조는 이후 Service/Repository로 옮기기 쉽다

의도적으로 포기한 부분은 다음과 같다.

- 데이터 영속성
- 트랜잭션 처리
- 저장소 추상화

## 결과

- InMemoryStore 클래스가 도입되었다
- Session과 Message 관계를 Map으로 표현한다
- 서버 재시작 시 데이터가 유실된다

이 구조는 3회차 이후 Service 계층 도입 시 제거될 예정이다.
