# 🐦 mySns

> **GraphQL 기반 SNS 백엔드** — 개인 학습용으로 시작했지만 실서비스급 품질을 목표로 합니다. RAM/오버헤드 최소화가 설계 원칙.

---

## 📌 개요

| 항목 | 내용 |
|---|---|
| 프로젝트 타입 | GraphQL API 서버 (SNS 도메인) |
| 언어 / 런타임 | Kotlin 2.2.21 / JDK 26 (bytecode target 24) |
| 프레임워크 | Spring Boot 4.0.6, Spring for GraphQL |
| 통신 프로토콜 | GraphQL over HTTP (`POST /graphql`) |
| 인증 방식 (예정) | JWT (현재 Stage 1: permitAll) |
| 상태 | 🟢 Stage 1 — 스키마 + Resolver 스켈레톤 동작 |

---

## 🧱 기술 스택

```
┌──────────────────┬─────────────────────────────────────────────────┐
│ 레이어           │ 기술                                            │
├──────────────────┼─────────────────────────────────────────────────┤
│ API              │ GraphQL (Spring for GraphQL)                    │
│ 언어             │ Kotlin 2.2.21                                   │
│ 프레임워크       │ Spring Boot 4.0.6                               │
│ 보안             │ Spring Security 6 (JWT 예정)                    │
│ DB (예정)        │ PostgreSQL — 현재는 H2 in-memory (스텁)         │
│ 검색 (예정)      │ Meilisearch                                     │
│ 메트릭           │ Micrometer → /actuator/prometheus → VictoriaMetrics │
│ 로그             │ Logback → loki4j → Loki                         │
│ 시각화           │ Grafana                                         │
│ Reverse Proxy    │ Nginx                                           │
│ 배포             │ 셀프호스팅                                      │
└──────────────────┴─────────────────────────────────────────────────┘
```

---

## 🏗️ 아키텍처

```
[Client]
   │  GraphQL Query/Mutation
   ▼
[Nginx] ──► [Spring Boot] ──┬──► [PostgreSQL]   (영속화, 예정)
                            ├──► [Meilisearch]  (검색, 예정)
                            └──► /actuator/prometheus
                                       │
[VictoriaMetrics] ◄────── scrape ──────┘
       │
       ▼
   [Grafana]  ◄──── push ──── [Loki] ◄── loki4j ── [Spring Boot]
```

---

## 🚀 빠른 시작

### 요구사항

- **JDK 26** (또는 `build.gradle.kts`의 toolchain 라인 수정해서 다른 버전 사용)
- 포트 **8080** 미사용 상태

### 실행

```bash
./gradlew bootRun
```

부팅 후:

- **GraphiQL UI**: http://localhost:8080/graphiql
- **GraphQL endpoint**: `POST http://localhost:8080/graphql`
- **Health**: http://localhost:8080/actuator/health
- **Metrics (Prometheus 포맷)**: http://localhost:8080/actuator/prometheus

---

## 🧪 GraphQL 예시

### 피드 조회 (`@BatchMapping`으로 N+1 방지)

```graphql
query {
  feed(limit: 3) {
    id
    content
    createdAt
    author {       # 3개 post의 author를 1번에 batch 로드
      id
      username
      displayName
    }
  }
}
```

### 내 정보 + 내 게시글

```graphql
query {
  me {
    id
    username
    posts(limit: 5) {
      id
      content
    }
    followerCount
  }
}
```

### 게시글 작성

```graphql
mutation {
  createPost(input: { content: "Hello from GraphQL" }) {
    id
    content
    author { username }
  }
}
```

> ⚠️ Stage 1에서는 author가 stub의 첫 user로 하드코딩됩니다. Stage 2에서 `SecurityContext`로 교체됩니다.

---

## 🗺️ 스키마

`src/main/resources/graphql/schema.graphqls`:

- **Query**: `me`, `user(id)`, `post(id)`, `feed(limit, offset)`
- **Mutation**: `createPost(input)`, `deletePost(id)`
- **Types**: `User`, `Post`
- **Input**: `CreatePostInput`
- **Scalar**: `DateTime` (ISO-8601, extended-scalars 기반)

---

## 📁 디렉토리 구조

```
src/main/
├── kotlin/com/mysns/main/
│   ├── MainApplication.kt
│   ├── config/
│   │   └── SecurityConfig.kt       # /graphql permitAll + @EnableMethodSecurity
│   └── graphql/
│       ├── GraphqlConfig.kt        # DateTime scalar 등록
│       ├── UserController.kt       # Query.me, user / User 필드 resolver
│       ├── PostController.kt       # Query.feed, post / Mutation / @BatchMapping(author)
│       ├── model/
│       │   ├── User.kt
│       │   ├── Post.kt
│       │   └── CreatePostInput.kt
│       └── stub/
│           ├── InMemoryUserStore.kt
│           └── InMemoryPostStore.kt
└── resources/
    ├── application.yaml
    └── graphql/
        └── schema.graphqls
```

---

## 🔐 인증/인가 아키텍처

GraphQL은 모든 요청이 `POST /graphql` 하나로 들어와서 URL 기반 Spring Security 모델이 맞지 않습니다. 본 프로젝트는 **3-Layer Authorization Model**을 채택합니다:

```
┌─────────┬──────────────────────────┬─────────────────────────────┐
│ Layer   │ 위치                     │ 역할                        │
├─────────┼──────────────────────────┼─────────────────────────────┤
│ Layer 1 │ HttpSecurity             │ JWT 파싱 → SecurityContext  │
│         │ (/graphql = permitAll)   │ URL 게이트키퍼 ❌           │
│ Layer 2 │ @PreAuthorize on Resolver│ 메서드 단위 인가            │
│ Layer 3 │ @SchemaMapping 필드 안   │ 민감 필드 마스킹            │
└─────────┴──────────────────────────┴─────────────────────────────┘
```

---

## 🛣️ 로드맵

```
┌────────┬──────────────────────────────────────────────────────────┐
│ Stage  │ 내용                                                     │
├────────┼──────────────────────────────────────────────────────────┤
│ 1 ✅   │ Schema + Resolver 스켈레톤, in-memory stub               │
│        │ SecurityConfig (permit + STATELESS + @EnableMethodSec)   │
│ 2      │ JwtProvider + JwtAuthFilter, login Mutation              │
│ 3      │ 각 Resolver에 @PreAuthorize 점진 적용                    │
│ 4      │ Refresh Token, 토큰 만료/회전 처리                       │
│ 5      │ JPA 엔티티 + PostgreSQL 연동 (in-memory stub 제거)       │
│ 6      │ Meilisearch 통합 (검색 GraphQL field 노출)               │
│ 7      │ Observability: VictoriaMetrics + Loki + Grafana 컨테이너 │
│ 8      │ Comment, Like, Follow 도메인 확장                        │
└────────┴──────────────────────────────────────────────────────────┘
```

---

## ⚙️ 빌드 설정 메모

- **JDK toolchain**: 26. Kotlin 2.2.21이 JVM target 26을 아직 지원하지 않아 bytecode target은 24로 명시.
- **JPA**: 의존성은 있지만 엔티티 없음. H2를 `runtimeOnly`로 추가해 빈 in-memory DB 자동 생성. PostgreSQL 연동 단계에서 h2 라인만 제거.
- **Actuator**: `health`, `prometheus`만 노출 (보안/RAM 양쪽 모두 고려).

---

## 📎 참고

- [Spring for GraphQL Docs](https://docs.spring.io/spring-graphql/reference/)
- [GraphQL Java Extended Scalars](https://github.com/graphql-java/graphql-java-extended-scalars)
- [VictoriaMetrics](https://docs.victoriametrics.com/)
- [Meilisearch](https://www.meilisearch.com/docs)
