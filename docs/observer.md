# 관측(Observability) & 장애 알림 운영 가이드

mysns 백엔드의 메트릭·로그·트레이스 수집과 **장애 발생 시 Discord로 자동 알림**을 받는 방법을 정리한다.
대상 독자: 이 서버를 직접 운영/디버깅하는 사람(=나).

---

## 1. 전체 구조 한눈에

```
                         ┌─────────────────────── Spring 앱 (:8080) ───────────────────────┐
                         │  /actuator/prometheus (메트릭)        loki4j 비동기 push (로그)   │
                         └───────────┬─────────────────────────────────────┬───────────────┘
                  15s pull │ (VM이 가져감)                      직접 push │
                           ▼                                              ▼
                  ┌──────────────────┐                          ┌──────────────────┐
                  │ VictoriaMetrics  │  메트릭 저장(14d)         │      Loki        │  로그 저장
                  │   :8428          │                          │     :3100        │
                  └────────┬─────────┘                          └────────┬─────────┘
                           │                                              │
                           └──────────────┬───────────────────────────────┘
                                          ▼
                                 ┌──────────────────┐
                                 │     Grafana      │  대시보드 + 알림 평가(Unified Alerting)
                                 │   :3001          │
                                 └────────┬─────────┘
                                          │ 룰 firing 시 HTTP POST
                                          ▼
                                 ┌──────────────────┐
                                 │  Discord 웹훅     │  → 폰/PC로 장애 알림 도착
                                 └──────────────────┘
```

| 구성요소 | 역할 | 포트 | 비고 |
|---|---|---|---|
| VictoriaMetrics | 메트릭 저장 + 스크랩 | 8428 | Prometheus 호환, RAM 절약용으로 Prometheus 대신 선택. 리텐션 14d |
| Loki | 로그 저장 | 3100 | Promtail 안 씀 — 앱이 loki4j로 직접 push (컨테이너 1개 절약) |
| Grafana | 대시보드 + 알림 | **3001** | 3000은 Next.js BFF가 점유. admin/admin |
| 트레이스 | traceId/spanId | - | `micrometer-tracing-bridge-brave`가 MDC에 주입. 외부 exporter 없음 → 로그 상관용 |

> 위 3개 컨테이너는 모두 `docker-compose.yml`의 `profiles: ["observability"]`에 묶여 있어 **명시적으로 켤 때만** 뜬다.

---

## 2. 띄우고 끄기

```bash
# 관측 스택 전체 기동 (VM + Loki + Grafana)
docker compose --profile observability up -d

# 끄기 (데이터 볼륨은 유지)
docker compose --profile observability down

# 데이터까지 싹 초기화 (메트릭/로그/대시보드 설정 전부 삭제)
docker compose --profile observability down -v
```

- Grafana: http://localhost:3001 (admin / admin)
- VictoriaMetrics UI: http://localhost:8428/vmui
- 대시보드: Grafana → Dashboards → **mysns / overview** (HTTP RPS, p95 지연, JVM heap, threads/CPU, 최근 로그)

### 앱 로그를 Loki로 보내려면
로그 push는 Spring `loki` 프로파일이 켜져 있을 때만 동작한다(`logback-spring.xml`).

```bash
# loki 프로파일로 앱 실행 → 로그가 Loki(:3100)로 push 됨
SPRING_PROFILES_ACTIVE=loki ./gradlew bootRun
# (json 프로파일은 콘솔 로그를 JSON으로, 둘 다 주면 "loki,json")
```

프로파일 없이 실행하면 콘솔에 pretty 로그만 찍히고 Loki로는 안 간다.

> **Loki가 죽어도 앱은 안 멈춘다.** loki4j 어펜더는 백그라운드 스레드 + 바운드 버퍼로 비동기 전송하고, 버퍼가 차면 로그를 드롭한다. 즉 관측 스택 장애가 앱 응답에 영향을 주지 않는다(RAM 최소화 원칙과 일치).

---

## 3. 장애 알림 (Grafana → Discord)

### 3.1 알림은 왜 앱이 아니라 Grafana에서 보내나
유저용 알림(팔로우/좋아요/댓글, SSE)과 **이 운영 알림은 완전히 별개 채널**이다.
운영 알림을 앱 안에 넣으면 "앱이 죽었을 때 알림도 같이 죽는" 모순이 생긴다. 그래서 장애 감지는 반드시 앱 **바깥**(Grafana)에서 한다.

### 3.2 알림 상태(state) 이해
Grafana는 각 룰의 조건을 주기적으로 평가하며 상태가 단계적으로 바뀐다.

| 상태 | 의미 |
|---|---|
| `inactive` (Normal) | 조건 거짓 = 정상 |
| `pending` | 조건이 막 참이 됐고 `for` 시간 동안 계속 참인지 관찰 중 (한 번 깜빡인 노이즈는 무시) |
| `firing` | `for` 동안 계속 참 = 장애 확정 → **이때 Discord로 발송** |
| (resolved) | firing이던 게 정상 복구됨 → "해결됨" 알림 발송 |

`for`를 두는 이유: 네트워크 한 번 끊긴 걸로 알림 폭탄을 맞지 않기 위함.

### 3.3 알림 규칙 6개

provisioning: `observability/grafana/provisioning/alerting/rules.yaml` (코드로 관리, Grafana 재기동 시 자동 적용)

| uid | 제목 | 조건(요약) | for | 심각도 | 1차 조치 |
|---|---|---|---|---|---|
| `mysns_app_down` | 앱 다운 | `up{job=mysns-be}==0` | 1m | 🔴 critical | 컨테이너/프로세스 살아있나, 8080 헬스 확인 |
| `mysns_error_rate` | 5xx 에러율 > 5% | 5xx/전체 비율 > 0.05 | 5m | 🔴 critical | Loki에서 `level=ERROR` + traceId로 원인 추적 |
| `mysns_latency_p95` | 지연 p95 > 1s | http p95 > 1s | 10m | 🟡 warning | uri별 p95 패널 확인, 슬로우쿼리/N+1 의심 |
| `mysns_heap_pressure` | JVM Heap > 90% | heap used/max > 0.9 | 5m | 🟡 warning | 누수/캐시/`-Xmx` 점검 (RAM 최소화 프로젝트 핵심 신호) |
| `mysns_db_pool` | DB 풀 고갈 임박 | Hikari active/max > 0.9 | 3m | 🟡 warning | 장기 트랜잭션/커넥션 누수/슬로우쿼리 확인 |
| `mysns_error_logs` | ERROR 로그 급증 | 5분간 ERROR > 10건 | 0s | 🟡 warning | 해당 구간 Loki 로그 직접 확인 |

> 임계치를 바꾸려면 `rules.yaml`의 해당 룰 `evaluator.params` 또는 `for`를 수정 → `docker compose --profile observability up -d grafana`로 재적용.

### 3.4 Discord 웹훅 연결 (실제 알림 받으려면 이 한 단계 필수)

알림 발송지 URL은 코드에 하드코딩하지 않고 환경변수 `DISCORD_ALERT_WEBHOOK_URL`로 주입한다(Git에는 안 올라감).

1. Discord 채널 → 채널 설정 → **연동(Integrations)** → **웹훅** → 새 웹훅 → **웹훅 URL 복사**
2. 레포 루트 `.env` 파일에 추가:
   ```
   DISCORD_ALERT_WEBHOOK_URL=https://discord.com/api/webhooks/xxxxx/yyyyy
   ```
   (템플릿은 `.env.example` 참고. `.env`는 Git 무시 대상)
3. Grafana 재기동:
   ```bash
   docker compose --profile observability up -d grafana
   ```

관련 provisioning 파일:
- `contactpoints.yaml` — `discord-ops` 컨택포인트 (`url: ${DISCORD_ALERT_WEBHOOK_URL}`)
- `policies.yaml` — 모든 알림을 `discord-ops`로, group_wait 30s / repeat 4h (같은 장애를 4시간마다 한 번씩만 재알림)

---

## 4. 장애 났을 때 흐름 (런북)

```
①Discord 알림 수신
      ↓
②Grafana 대시보드(mysns/overview)로 범위 파악
      ↓  (RPS·지연·heap·CPU 중 뭐가 튀었나)
③Loki에서 해당 시각 로그 확인  →  로그의 traceId 로 같은 요청 추적
      ↓
④룰별 1차 조치(위 3.3 표) 수행
      ↓
⑤복구되면 Grafana가 자동으로 "Resolved" 알림 발송
```

자주 쓰는 LogQL (Grafana → Explore → Loki):
```logql
{app="mysns", level="ERROR"}                      # 에러 로그만
{app="mysns"} |= "<traceId>"                       # 특정 요청 추적
sum(count_over_time({app="mysns",level="ERROR"}[5m]))   # 에러 추이
```

---

## 5. 검증된 동작 (2026-06-15 기준)

실제로 앱을 끈 상태에서 스택을 띄워 end-to-end 확인 완료:

- 6개 룰 모두 provisioning 로드 + `health=ok`
- 앱(8080) 미기동 → `up==0` → `mysns_app_down`이 `pending`(1m) → **`firing`** 전환
- Grafana가 Discord 포맷 페이로드를 웹훅으로 실제 POST (`**Firing** ... severity=critical ... source 링크` 포함)

즉 **룰 평가 → firing → 컨택포인트 → 웹훅 배달**까지 전 구간 동작 확인됨. 실제 Discord 채널 수신은 `DISCORD_ALERT_WEBHOOK_URL`만 채우면 된다.

---

## 6. 알려진 사각지대 (TODO)

| 항목 | 내용 |
|---|---|
| Grafana 자체 SPOF | Grafana가 죽으면 알림도 죽는다. 외부 무료 uptime 모니터(UptimeRobot 등)가 `/actuator/health`를 핑해 "스택 통째 다운"을 별도 감지하도록 보완 필요 |
| RUNBOOK 상세화 | 위 4장은 요약. 룰별 상세 조치/과거 사례를 별도 문서로 확장 가능 |
| 외부 trace 백엔드 | 현재 traceId는 로그 상관용만. 분산 트레이싱이 필요해지면 Tempo 등 추가 검토 |
