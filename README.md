# sqs_template

Spring Boot, local DB, LocalStack SQS, and Prometheus study project.

이 프로젝트는 **느린 작업을 API 요청 경로에서 분리하면 응답 시간이 어떻게 줄어드는지** 확인하기 위한 작은 실습입니다.

## What This App Shows

같은 리포트 생성 작업을 두 방식으로 제공합니다.

```text
POST /sync/reports
  -> DB 저장
  -> 3초짜리 리포트 생성 대기
  -> DB 업데이트
  -> 응답

POST /reports
  -> DB 저장(PENDING)
  -> SQS 메시지 발행
  -> 바로 응답
  -> 백그라운드 worker가 SQS 메시지를 처리하고 DB 업데이트
```

즉, `POST /sync/reports`는 느리고 `POST /reports`는 빠른 구조입니다.

## Spec

- [Async Report SQS Study Spec](docs/async-report-sqs-spec.md)

## Local Stack

Docker Compose로 아래 컴포넌트를 실행합니다.

- PostgreSQL: report 상태 저장
- LocalStack SQS: 로컬 SQS 큐
- Prometheus: Spring Boot metrics scrape
- Grafana: dashboard 연습용

## Run

먼저 로컬 인프라를 켭니다.

```bash
docker compose up -d
```

Spring Boot 앱을 실행합니다.

```bash
./gradlew bootRun
```

앱이 뜨면 아래 주소를 확인할 수 있습니다.

```text
Spring Boot: http://localhost:8081
Prometheus:  http://localhost:9090
Grafana:     http://localhost:3000
```

Grafana 기본 계정은 `admin / admin`입니다.

## Run With Real AWS SQS

AWS Console에서 만든 큐를 쓰려면 `aws` profile로 실행합니다.

현재 설정된 실제 SQS 큐:

```text
region: ap-northeast-2
queue type: Standard
queue name: report-requested-queue
queue url: https://sqs.ap-northeast-2.amazonaws.com/491013322019/report-requested-queue
```

인증 정보는 코드에 넣지 않고 `.env` 파일로 설정합니다.

```bash
cp .env.example .env
```

그리고 `.env` 파일 안에 값을 채웁니다.

```properties
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=your-access-key-id
AWS_SECRET_ACCESS_KEY=your-secret-access-key

# 임시 자격 증명을 쓰는 경우에만 입력합니다.
AWS_SESSION_TOKEN=

REPORT_REQUESTED_QUEUE_NAME=report-requested-queue
REPORT_REQUESTED_QUEUE_URL=https://sqs.ap-northeast-2.amazonaws.com/491013322019/report-requested-queue
```

실제 AWS SQS를 쓰더라도 DB는 아직 로컬 PostgreSQL을 사용합니다.

```bash
docker compose up -d postgres prometheus grafana
./gradlew bootRun --args='--spring.profiles.active=aws'
```

`aws` profile에서는 LocalStack endpoint를 사용하지 않고 실제 AWS SQS 큐로 메시지를 보냅니다.

## API Examples

비동기 리포트 생성:

```bash
curl -i -X POST http://localhost:8081/reports \
  -H 'Content-Type: application/json' \
  -d '{"title":"normal-report","type":"SUMMARY"}'
```

응답은 `202 Accepted`이고, 리포트 상태는 처음에 `PENDING`입니다.

```json
{
  "reportId": "uuid",
  "status": "PENDING"
}
```

리포트 조회:

```bash
curl http://localhost:8081/reports/{reportId}
```

동기 리포트 생성:

```bash
curl -i -X POST http://localhost:8081/sync/reports \
  -H 'Content-Type: application/json' \
  -d '{"title":"normal-report","type":"SUMMARY"}'
```

이 요청은 리포트 생성이 끝날 때까지 약 3초를 기다립니다.

## Compare Response Time

터미널에서 `time`으로 단순 비교할 수 있습니다.

```bash
time curl -s -X POST http://localhost:8081/sync/reports \
  -H 'Content-Type: application/json' \
  -d '{"title":"normal-report","type":"SUMMARY"}'
```

```bash
time curl -s -X POST http://localhost:8081/reports \
  -H 'Content-Type: application/json' \
  -d '{"title":"normal-report","type":"SUMMARY"}'
```

기대 결과:

- `/sync/reports`: 약 3초
- `/reports`: 보통 수십~수백 ms

## Scenario Script

Python 표준 라이브러리만 사용하는 시나리오 스크립트가 있습니다.

```bash
python3 scripts/report_sqs_scenario.py --duration-seconds 1800 --interval-seconds 10
```

이 스크립트는 한 라운드마다 아래 요청을 순서대로 실행합니다.

```text
POST /reports
  -> 202 응답 시간 측정
  -> GET /reports/{id} polling
  -> worker 처리 완료 시간 확인

POST /sync/reports
  -> 201 응답 시간 측정
  -> 요청 시간이 처리 시간과 거의 같은지 확인
```

결과는 `docs/test-runs/` 아래에 JSON과 Markdown으로 저장됩니다. 중간에 `Ctrl-C` 또는 종료 신호가 들어와도 현재까지의 partial report를 남기도록 작성했습니다.

## Observed Test Run

2026-06-04에 로컬 PostgreSQL과 실제 AWS SQS(`report-requested-queue`)를 사용해서 짧은 검증을 진행했습니다.

20초 smoke test 결과:

| mode | count | success | failed | avg response ms | p95 response ms | avg processing ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| async | 3 | 3 | 0 | 97.4 | 211.54 | 3009.33 |
| sync | 3 | 3 | 0 | 3035.48 | 3039.58 | 3009.67 |

30분 시나리오는 중간에 중단했고, 중단 시점까지 콘솔에서 확인한 샘플은 아래와 같습니다.

| mode | observed count | success | failed | avg response ms | p95 response ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| async | 11 | 11 | 0 | 38.11 | 49.31 |
| sync | 10 | 10 | 0 | 3030.42 | 3037.57 |

이 결과에서 봐야 할 포인트는 `async` 처리 시간이 사라진 것이 아니라, API 응답 경로에서 분리되었다는 점입니다. `POST /reports`는 DB 저장과 SQS 발행까지만 하고 빠르게 응답했고, 실제 3초짜리 리포트 생성은 worker가 뒤에서 처리했습니다.

## Test Titles

| title | behavior |
| --- | --- |
| `normal-report` | 3초 후 성공 |
| `slow-report` | 5초 후 성공 |
| `fail-report` | 실패 상태로 저장 |

## Metrics

Prometheus endpoint:

```text
http://localhost:8081/actuator/prometheus
```

확인해볼 만한 metric:

```text
http_server_requests_seconds
reports_requested_total
reports_completed_total
report_processing_seconds
sqs_messages_received_total
sqs_messages_failed_total
```
