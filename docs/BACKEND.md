# Backend Specification

| Field | Value |
|---|---|
| Document ID | DOC-BE |
| Version | 0.2.1 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Backend engineer |

## 1. Purpose

Engineering standards for Uni Companion Spring services.

## 2. Locked baseline

| Concern | Standard |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 |
| Build | Maven |
| Security | Spring Security + JWT |
| Persistence | Spring Data JPA + PostgreSQL |
| Vectors | pgvector |
| Migrations | Flyway |
| PDF | Apache PDFBox |
| LLM | Google Gemini via provider interfaces |
| API docs | springdoc-openapi |
| Async ingest | Spring `@Async` / application events / job entity (not Kafka in Phase A) |

## 3. Repository layout

See [EXECUTION-PLAN.md](EXECUTION-PLAN.md) §4.

## 4. Package structure

### identity-service

```text
com.unicompanion.identity.{api,application,domain,infrastructure,config}
```

### learning-service

```text
com.unicompanion.learning.{course,material,ingestion,rag,study,matching}
  each with api/application/domain/infrastructure as needed
```

## 5. API standards

- Base path `/api/...`
- Error body: `{ code, message, correlationId, details? }`
- Bean Validation on inputs
- No stack traces to clients
- ISO-8601 UTC timestamps
- UUID string IDs

## 6. Security standards

- Validate JWT on protected routes
- Role checks for admin material operations
- Enrollment checks for study/RAG
- Filter retrieval by `READY` + `PUBLISHED`
- Secrets via env only

## 7. AI integration

```text
EmbeddingModel  → Gemini embeddings implementation
ChatModel       → Gemini chat implementation
```

Application code depends on interfaces only ([ADR-0005](adr/0005-gemini-llm-provider.md)).

## 8. Ingestion pipeline (Phase A)

1. Persist material (`UNPUBLISHED`, `UPLOADED`)  
2. Store PDF under `STORAGE_LOCAL_PATH`  
3. Async job: extract (PDFBox) → chunk → embed → write pgvector  
4. Set `READY` or `FAILED`  
5. Publish via separate visibility API (`UC-018`)  

## 9. Profiles

| Profile | Use |
|---|---|
| `local` | Docker Postgres, local disk |
| `dev` | Shared non-prod (later) |
| `prod` | Hardened (later) |

## 10. Testing (TDD)

Phase A uses **Test-Driven Development**. Normative practice: [TESTING.md](TESTING.md), [ADR-0006](adr/0006-tdd-approach.md).

| Level | Focus | Tools |
|---|---|---|
| Unit (TDD-first) | Visibility, enrollment, roles, chunking | JUnit 5, AssertJ, Mockito |
| API (TDD-first) | AuthZ happy + negative paths | MockMvc / WebTestClient, Security Test |
| Integration | Ingest → READY; unpublished excluded from RAG | Testcontainers PostgreSQL |
| LLM | Fake `ChatModel` / `EmbeddingModel` in CI | Mockito; optional live smoke |

### Cycle

Red (failing acceptance test) → Green (minimal code) → Refactor → commit with tests.

### Security-sensitive paths (mandatory negative tests)

Upload, publish/unpublish, RAG query, enrollment-gated study.

## 11. References

- [Architecture](ARCHITECTURE.md)
- [Services](SERVICES.md)
- [Security](SECURITY.md)
- [Testing](TESTING.md)
- [Execution Plan](EXECUTION-PLAN.md)
- [ADR-0006](adr/0006-tdd-approach.md)
