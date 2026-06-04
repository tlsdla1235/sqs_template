# Async Report SQS Study Spec

## 1. What We Are Building

This project is a small Spring Boot study application that demonstrates why SQS is useful for slow work.

The application will expose two report creation flows:

- Sync flow: the API waits until report generation is finished.
- Async flow: the API stores a request in the database, sends a message to SQS, and returns immediately.

The main goal is to compare response time between the two approaches.

```text
Sync:
Client -> Spring Boot -> DB insert -> slow report generation -> DB update -> response

Async:
Client -> Spring Boot -> DB insert(PENDING) -> SQS publish -> response
                                      |
                                      v
                              Worker consumes message
                              -> slow report generation
                              -> DB update(COMPLETED / FAILED)
```

## 2. Study Goals

- Understand how SQS separates request handling from slow background work.
- Show that async API response time is shorter than sync API response time.
- Practice local development with Spring Boot, Docker DB, LocalStack SQS, and Prometheus.
- Learn basic retry and failure handling through report processing status.
- Keep Lambda optional until the SQS flow is clear.

## 3. Local Architecture

### Required Components

- Spring Boot API server
- Local DB through Docker, recommended: PostgreSQL
- LocalStack SQS through Docker
- Prometheus for metrics scraping

### Optional Components

- Grafana for dashboard visualization
- AWS Lambda or LocalStack Lambda as a later worker replacement

```text
docker-compose
  - postgres
  - localstack
  - prometheus
  - grafana(optional)

Spring Boot
  - REST API
  - Report service
  - SQS producer
  - SQS consumer(worker)
  - Actuator Prometheus endpoint
```

## 4. Core Scenario

The user requests report generation.

For study purposes, report generation will be intentionally slow, for example 3 seconds. The generated report can be simple text stored in a database column.

Example report result:

```text
Report title: daily-summary
Type: SUMMARY
Generated at: 2026-06-04T10:00:00
```

The important part is not the report content. The important part is that slow work happens outside the async API response path.

## 5. API Specification

### 5.1 Create Report Asynchronously

```http
POST /reports
```

Request:

```json
{
  "title": "daily-summary",
  "type": "SUMMARY"
}
```

Response:

```http
202 Accepted
```

```json
{
  "reportId": "uuid",
  "status": "PENDING"
}
```

Behavior:

- Insert report row with `PENDING`.
- Publish `ReportRequested` message to SQS.
- Return immediately without waiting for report generation.

### 5.2 Create Report Synchronously

```http
POST /sync/reports
```

Request:

```json
{
  "title": "daily-summary",
  "type": "SUMMARY"
}
```

Response:

```http
201 Created
```

```json
{
  "reportId": "uuid",
  "status": "COMPLETED",
  "result": "Report title: daily-summary..."
}
```

Behavior:

- Insert report row.
- Wait for slow report generation.
- Update report row to `COMPLETED`.
- Return after all work is done.

This endpoint exists only to compare response time with the async endpoint.

### 5.3 Get Report

```http
GET /reports/{reportId}
```

Response:

```json
{
  "reportId": "uuid",
  "title": "daily-summary",
  "type": "SUMMARY",
  "status": "COMPLETED",
  "result": "Report title: daily-summary...",
  "errorMessage": null,
  "requestedAt": "2026-06-04T10:00:00",
  "startedAt": "2026-06-04T10:00:01",
  "completedAt": "2026-06-04T10:00:04",
  "processingMillis": 3000
}
```

### 5.4 List Reports

```http
GET /reports
```

Optional query params:

```text
status=PENDING|PROCESSING|COMPLETED|FAILED
```

## 6. Database Specification

Table: `reports`

| Column | Type | Description |
| --- | --- | --- |
| id | UUID | Primary key |
| title | VARCHAR | Report title |
| type | VARCHAR | Report type |
| status | VARCHAR | PENDING, PROCESSING, COMPLETED, FAILED |
| result | TEXT | Generated report result |
| error_message | TEXT | Failure reason |
| requested_at | TIMESTAMP | Request creation time |
| started_at | TIMESTAMP | Worker start time |
| completed_at | TIMESTAMP | Worker completion time |
| processing_millis | BIGINT | Background processing duration |

## 7. SQS Specification

### Queue

```text
report-requested-queue
```

### Optional DLQ

```text
report-requested-dlq
```

### Message: ReportRequested

```json
{
  "reportId": "uuid",
  "requestedAt": "2026-06-04T10:00:00"
}
```

### Consumer Behavior

1. Receive SQS message.
2. Load report by `reportId`.
3. If report is already `COMPLETED`, ignore the message.
4. Update status to `PROCESSING`.
5. Simulate slow report generation.
6. Update status to `COMPLETED`.
7. If an error occurs, update status to `FAILED`.

## 8. Failure Test Rules

To keep testing simple, special titles can trigger behavior:

| Title | Behavior |
| --- | --- |
| `slow-report` | Sleep for 5 seconds |
| `fail-report` | Throw an error and mark report as FAILED |
| `normal-report` | Complete normally after 3 seconds |

## 9. Metrics Specification

Expose metrics through:

```http
GET /actuator/prometheus
```

Recommended custom metrics:

```text
reports_requested_total
reports_completed_total{result="success"}
reports_completed_total{result="failure"}
report_processing_seconds
sqs_messages_received_total
sqs_messages_failed_total
```

Built-in metrics to compare:

```text
http_server_requests_seconds
```

Expected observation:

- `POST /sync/reports` takes around 3 seconds.
- `POST /reports` returns quickly, usually under a few hundred milliseconds.
- `report_processing_seconds` still shows the real background work duration.

## 10. Lambda Decision

Lambda is optional for the first version.

Recommended implementation order:

1. Use Spring Boot SQS consumer as the first worker.
2. Verify DB status changes and Prometheus metrics locally.
3. Replace or duplicate the worker with Lambda later.

Possible Lambda flow:

```text
Spring Boot API -> SQS -> Lambda worker -> DB update
```

Using Lambda too early adds IAM, packaging, deployment, and networking concerns. For this study project, Lambda should be introduced only after the local SQS flow is working.

## 11. Implementation Milestones

### Milestone 1: Basic Spring Boot API

- Create Spring Boot project.
- Add health check and Prometheus actuator endpoint.
- Add report create/list/get APIs without SQS.

### Milestone 2: Local DB

- Add PostgreSQL to Docker Compose.
- Add report entity, repository, and migration.
- Store reports in DB.

### Milestone 3: Sync vs Async Comparison

- Add `POST /sync/reports`.
- Add `POST /reports`.
- Make sync endpoint wait for slow work.
- Make async endpoint return after DB insert and SQS publish.

### Milestone 4: LocalStack SQS

- Add LocalStack to Docker Compose.
- Create `report-requested-queue`.
- Add SQS producer.
- Add Spring Boot SQS consumer.

### Milestone 5: Metrics

- Add custom report and SQS metrics.
- Add Prometheus scrape config.
- Compare sync and async API latency.

### Milestone 6: Optional Lambda

- Add Lambda worker for SQS messages.
- Keep Spring Boot API unchanged.
- Compare Spring consumer and Lambda worker behavior.

## 12. Success Criteria

- Async report API returns before report generation finishes.
- Report status changes from `PENDING` to `PROCESSING` to `COMPLETED`.
- Failed report can be observed as `FAILED`.
- Prometheus shows API latency and background processing duration separately.
- The project can run locally with Docker and Spring Boot.
