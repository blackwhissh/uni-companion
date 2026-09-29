# Messaging Specification

| Field | Value |
|---|---|
| Document ID | DOC-MSG |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Backend engineer |

## 1. Purpose

Define asynchronous integration. **Phase A does not require Kafka.** This document specifies (a) Phase A job-based async and (b) the target Kafka catalog for extraction.

Related: [ADR-0002](adr/0002-kafka-progressive-adoption.md).

## 2. Phase A — job-based async

| Concern | Approach |
|---|---|
| Trigger | Material upload persists row + file, then schedules ingest job |
| Progress | Material `processingStatus` column |
| Fan-out | In-process; same DB |
| Failure | `FAILED` + admin retry endpoint |
| Idempotency | Job id / material id guards |

No broker in Docker Compose for Phase A.

## 3. Target — Apache Kafka

When `learning-service` splits, adopt Kafka for cross-service facts.

### Principles

| ID | Rule |
|---|---|
| M-1 | Events are facts that already happened |
| M-2 | Stable `eventId` for idempotent consumers |
| M-3 | Additive payload evolution |
| M-4 | Consumer-owned retry/DLQ |
| M-5 | Broker has no business logic |

### Envelope

```json
{
  "eventId": "uuid",
  "eventType": "material.uploaded",
  "occurredAt": "2026-09-27T12:00:00Z",
  "producer": "course-service",
  "tenant": "uni-siegen",
  "correlationId": "req-…",
  "schemaVersion": 1,
  "payload": {}
}
```

### Topic catalog (draft)

| EVT | Topic | Producer | Consumers |
|---|---|---|---|
| EVT-012 | `unicompanion.course.material.uploaded` | course | ingestion |
| EVT-020 | `unicompanion.ingestion.material.started` | ingestion | course |
| EVT-021 | `unicompanion.ingestion.material.completed` | ingestion | course, rag, study |
| EVT-022 | `unicompanion.ingestion.material.failed` | ingestion | course |
| EVT-018 | `unicompanion.course.material.visibility.changed` | course | rag, study |
| EVT-011 | `unicompanion.course.enrollment.created` | course | matching |
| EVT-031 | `unicompanion.study.quiz.completed` | study | matching (later) |
| EVT-040 | `unicompanion.match.request.created` | matching | notification |

**Note:** Visibility changes must exclude unpublished materials from retrieval even before Kafka (Phase A enforces in DB queries).

## 4. Adoption criteria for Kafka

Introduce when at least one is true:

- Ingestion runs as a separate service  
- Multiple consumers need material lifecycle fan-out  
- Operational need for replay/DLQ across nodes  

## 5. References

- [Architecture](ARCHITECTURE.md)
- [Services](SERVICES.md)
- [Execution Plan](EXECUTION-PLAN.md)
- [ADR-0002](adr/0002-kafka-progressive-adoption.md)
