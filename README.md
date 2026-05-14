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
| 인증 방식 | JWT (HS512, body 응답 → BFF 쿠키 보관) |
| 아키텍처 | BFF 패턴 — Next.js BFF + Spring API |
| 상태 | 🟢 Stage 4 — Like/Comment/Bookmark/Share/Follow 전부 in-memory 실구현 |

---

## 🧱 기술 스택

```
┌──────────────────┬─────────────────────────────────────────────────┐
│ 레이어           │ 기술                                            │
├──────────────────┼─────────────────────────────────────────────────┤
│ API              │ GraphQL (Spring for GraphQL)                    │
│ 언어             │ Kotlin 2.2.21                                   │
│ 프레임워크       │ Spring Boot 4.0.6                               │
│ 인증             │ Spring Security 6 + JWT (jjwt 0.12)             │
│ Store (현재)     │ In-Memory (ConcurrentHashMap) — JPA 마이그 예정 │
│ BFF              │ Next.js 16.2 (App Router) — 쿠키/CSRF 관리      │
│ DB (예정)        │ PostgreSQL                                      │
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
[Browser] ──(쿠키)──► [Next.js BFF] ──(Authorization: Bearer)──► [Spring]
                          │                                          │
                          └─ /api/auth/login                          ├──► [PostgreSQL]   (예정)
                          └─ /api/auth/logout                         ├──► [Meilisearch]  (예정)
                          └─ /api/graphql (proxy)                     └──► /actuator/prometheus
                                                                              │
                                            [VictoriaMetrics] ◄── scrape ────┘
                                                   │
                                                   ▼
                                            [Grafana] ◄── push ── [Loki] ◄── loki4j ── [Spring]
```

---

## 🚀 빠른 시작

### 요구사항

- **JDK 26** (또는 `build.gradle.kts`의 toolchain 라인 수정)
- 포트 **8080** 미사용 상태

### 실행

```bash
./gradlew bootRun
```

부팅 후:

- **GraphiQL UI**: http://localhost:8080/graphiql
- **GraphQL endpoint**: `POST http://localhost:8080/graphql`
- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/prometheus

---

## 🧪 GraphQL 예시

### 로그인 (BFF 측이 호출, JWT는 응답 body로)

```graphql
mutation {
  login(input: { username: "alice", password: "password" }) {
    accessToken
    user { id username displayName avatarUrl }
  }
}
```

### 피드 (인스타 카드 한 장에 필요한 데이터 한 번에)

```graphql
query {
  feed(limit: 10) {
    id
    content
    imageUrls
    tag
    createdAt
    updatedAt
    likeCount
    commentCount
    shareCount
    viewerHasLiked
    viewerHasBookmarked
    author {
      id
      username
      displayName
      avatarUrl
      viewerIsFollowing
    }
  }
}
```

### 인터랙션

```graphql
mutation { likePost(postId: "6") { likeCount viewerHasLiked } }
mutation { bookmarkPost(postId: "6") { viewerHasBookmarked } }
mutation { sharePost(postId: "6") { shareCount } }
mutation { addComment(input: { postId: "6", content: "nice" }) { id author { username } } }
mutation { followUser(id: "2") { followerCount viewerIsFollowing } }
```

### 내 북마크 / 프로필 조회

```graphql
query { bookmarks(limit: 20) { id content author { username } } }
query { userByUsername(username: "bob") { id postCount followerCount posts(limit: 5) { id content } } }
```

---

## 🗺️ 도메인 / 스키마

`src/main/resources/graphql/schema.graphqls`:

- **Type**: `User`, `Post`, `Comment`, `AuthPayload`
- **Query**: `me`, `user`, `userByUsername`, `post`, `feed`, `bookmarks`
- **Mutation**:
  - Auth: `login`
  - Post: `createPost`, `updatePost`, `deletePost`
  - Interaction: `likePost`, `unlikePost`, `bookmarkPost`, `unbookmarkPost`, `sharePost`
  - Comment: `addComment`, `updateComment`, `deleteComment`, `likeComment`, `unlikeComment`
  - Follow: `followUser`, `unfollowUser`
- **Scalar**: `DateTime` (ISO-8601)

---

## 📁 디렉토리 구조

```
src/main/
├── kotlin/com/mysns/main/
│   ├── MainApplication.kt
│   ├── auth/
│   │   ├── AuthService.kt          # login flow (BCrypt 검증 + JWT 발급)
│   │   ├── JwtProvider.kt          # issue/parse access tokens
│   │   ├── JwtAuthFilter.kt        # Authorization: Bearer → SecurityContext
│   │   ├── DevAutoAuthFilter.kt    # devMode일 때 자동 인증
│   │   └── AuthenticatedUser.kt    # principal + currentUser()/requireCurrentUser()
│   ├── config/
│   │   ├── SecurityConfig.kt       # 3-Layer 모델, devMode 분기, CORS, PasswordEncoder
│   │   └── JwtProperties.kt        # mysns.jwt.* binding
│   └── graphql/
│       ├── GraphqlConfig.kt        # DateTime scalar 등록
│       ├── AuthController.kt       # login mutation
│       ├── UserController.kt       # user/me/userByUsername + follow + User 필드
│       ├── PostController.kt       # post/feed/bookmarks + CRUD + interactions + Post 필드
│       ├── CommentController.kt    # Comment CRUD + likes + Comment 필드
│       ├── model/
│       │   ├── User.kt, Post.kt, Comment.kt
│       │   ├── AuthPayload.kt, LoginInput.kt
│       │   ├── CreatePostInput.kt, UpdatePostInput.kt
│       │   └── AddCommentInput.kt
│       └── stub/
│           ├── UserCredentials.kt
│           ├── InMemoryUserStore.kt
│           ├── InMemoryPostStore.kt
│           ├── InMemoryLikeStore.kt
│           ├── InMemoryBookmarkStore.kt
│           ├── InMemoryCommentStore.kt
│           ├── InMemoryCommentLikeStore.kt
│           └── InMemoryFollowStore.kt
└── resources/
    ├── application.yaml
    └── graphql/schema.graphqls
```

---

## 🔐 인증/인가 아키텍처

GraphQL은 모든 요청이 `POST /graphql` 하나로 들어와서 URL 기반 Spring Security 모델이 맞지 않습니다. **3-Layer Authorization Model**:

```
┌─────────┬──────────────────────────┬─────────────────────────────┐
│ Layer   │ 위치                     │ 역할                        │
├─────────┼──────────────────────────┼─────────────────────────────┤
│ Layer 1 │ HttpSecurity             │ JWT 파싱 → SecurityContext  │
│         │ (/graphql = permitAll)   │ URL 게이트키퍼 ❌           │
│ Layer 2 │ @PreAuthorize on Resolver│ 메서드 단위 인가 ✅ Stage 3  │
│ Layer 3 │ @SchemaMapping 필드 안   │ 민감 필드 마스킹 (예정)     │
└─────────┴──────────────────────────┴─────────────────────────────┘
```

### Dev Mode

`mysns.security.dev-mode: true`이면 모든 요청이 `permitAll` 되고 첫 stub user(alice)로 자동 인증됩니다. 프로덕션 배포 전 반드시 `false`로.

---

## 🛣️ 로드맵

```
┌────────┬──────────────────────────────────────────────────────────┐
│ Stage  │ 내용                                                     │
├────────┼──────────────────────────────────────────────────────────┤
│ 1 ✅   │ Schema + Resolver 스켈레톤, in-memory stub               │
│        │ SecurityConfig (permit + STATELESS + @EnableMethodSec)   │
│ 2 ✅   │ JwtProvider + JwtAuthFilter, login Mutation, BCrypt      │
│ 3 ✅   │ 각 Resolver에 @PreAuthorize, SecurityContext 통합        │
│ 4 ✅   │ Like/Comment/Bookmark/Share/Follow 전부 in-memory 실구현 │
│        │ Post.imageUrls/tag/updatedAt, User.avatarUrl 추가        │
│ 5      │ JPA 엔티티 + PostgreSQL 연동 (in-memory store 교체)      │
│ 6      │ Refresh Token, 토큰 만료/회전 처리                       │
│ 7      │ Meilisearch 통합 (검색 GraphQL field 노출)               │
│ 8      │ Observability: VictoriaMetrics + Loki + Grafana 컨테이너 │
│ 9      │ Media upload (presigned URL, 실제 이미지 호스팅)         │
└────────┴──────────────────────────────────────────────────────────┘
```

---

## ⚙️ 빌드 설정 메모

- **JDK toolchain**: 26. Kotlin 2.2.21이 JVM target 26을 아직 지원하지 않아 bytecode target은 24로 명시.
- **JPA**: 의존성은 있지만 엔티티 없음 (현재 in-memory store만 사용). H2를 `runtimeOnly`로 추가해 부팅 시 빈 in-memory DB 자동 생성. Stage 5에서 본격 활용.
- **Actuator**: `health`, `prometheus`만 노출.

---

## 📎 참고

- [Spring for GraphQL Docs](https://docs.spring.io/spring-graphql/reference/)
- [GraphQL Java Extended Scalars](https://github.com/graphql-java/graphql-java-extended-scalars)
- [jjwt](https://github.com/jwtk/jjwt)
- [VictoriaMetrics](https://docs.victoriametrics.com/)
- [Meilisearch](https://www.meilisearch.com/docs)
