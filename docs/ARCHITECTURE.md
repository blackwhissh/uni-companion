# System Architecture

| Field | Value |
|---|---|
| Document ID | DOC-ARCH |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Tech lead |

## 1. Purpose

Describe Phase A and target architecture for Uni Companion.

## 2. Architectural principles

| ID | Principle |
|---|---|
| P-1 | Canonical materials are admin-owned; students consume |
| P-2 | Study access requires enrollment ∧ READY ∧ PUBLISHED |
| P-3 | Database/schema per deployable service |
| P-4 | Sync HTTP for interactive UX; async for ingestion |
| P-5 | Progressive decomposition (Phase A modular monolith-ish learning service) |
| P-6 | Provider interfaces for LLM/embeddings (Gemini now) |
| P-7 | Privacy by default for matching and materials |

## 3. Phase A container view

```mermaid
C4Container
title Uni Companion — Phase A
Person(student, "Student")
Person(admin, "Course Admin")
Container(web, "Web App", "React Vite Tailwind", "Study + admin UI")
Container(identity, "identity-service", "Spring Boot", "Auth, JWT, roles")
Container(learning, "learning-service", "Spring Boot", "Course, materials, RAG, study, match")
ContainerDb(pg, "PostgreSQL", "pgvector", "identity + learning DBs/schemas")
System_Ext(gemini, "Google Gemini", "Embeddings + chat")
Rel(student, web, "HTTPS")
Rel(admin, web, "HTTPS")
Rel(web, identity, "REST/JWT")
Rel(web, learning, "REST/JWT")
Rel(identity, pg, "JDBC")
Rel(learning, pg, "JDBC")
Rel(learning, gemini, "HTTPS")
```

## 4. Target container view

```mermaid
C4Container
title Uni Companion — Target
Person(user, "Student / Admin")
Container(web, "Web App", "React")
Container(gw, "API Gateway", "Spring Cloud Gateway")
Container(identity, "identity-service", "Spring Boot")
Container(course, "course-service", "Spring Boot")
Container(ingestion, "ingestion-service", "Spring Boot")
Container(rag, "rag-service", "Spring Boot")
Container(study, "study-service", "Spring Boot")
Container(match, "matching-service", "Spring Boot")
ContainerDb(kafka, "Kafka", "Domain events")
ContainerDb(pg, "PostgreSQL + pgvector")
System_Ext(gemini, "LLM Provider")
Rel(user, web, "HTTPS")
Rel(web, gw, "HTTPS")
Rel(gw, identity, "HTTP")
Rel(gw, course, "HTTP")
Rel(gw, rag, "HTTP")
Rel(gw, study, "HTTP")
Rel(gw, match, "HTTP")
Rel(course, kafka, "Events")
Rel(ingestion, kafka, "Events")
Rel(rag, kafka, "Events")
Rel(study, kafka, "Events")
Rel(match, kafka, "Events")
Rel(identity, kafka, "Events")
```

## 5. Phase A vs target mapping

| Target service | Phase A home |
|---|---|
| api-gateway | Deferred — direct service URLs |
| identity-service | `identity-service` |
| course + ingestion + rag + study + matching | packages inside `learning-service` |
| notification-service | Deferred |
| Kafka | Deferred — job/status table |

## 6. Key runtime flows (Phase A)

### Material lifecycle

```text
Admin upload → UNPUBLISHED + PROCESSING
     → PDFBox extract → chunk → Gemini embed → pgvector
     → READY | FAILED
Admin publish → PUBLISHED
Student (enrolled) may study iff READY ∧ PUBLISHED
Admin unpublish → hidden from students + excluded from retrieval
```

### RAG query

```text
Student question
 → authorize enrollment
 → retrieve top-k chunks (published+ready only)
 → Gemini chat with excerpts
 → answer + citations
```

## 7. Quality tactics

| Attribute | Tactic |
|---|---|
| Security | JWT, role checks, enrollment checks, unpublished exclusion |
| Reliability | Ingest status + admin retry |
| Operability | Actuator health; correlation IDs in logs |
| Evolveability | Modular packages; LLM interfaces; ADR trail |
| Cost | Gemini free tier; cache embeddings; rate-limit generation |
| Verifiability | TDD for domain/API rules; fakes for Gemini ([TESTING.md](TESTING.md)) |

## 8. Decisions

| Topic | ADR |
|---|---|
| Microservices target | [ADR-0001](adr/0001-microservices-architecture.md) |
| Kafka deferred then adopted | [ADR-0002](adr/0002-kafka-progressive-adoption.md) |
| React Vite SPA | [ADR-0003](adr/0003-react-vite-frontend.md) |
| Progressive decomposition | [ADR-0004](adr/0004-progressive-decomposition.md) |
| Gemini provider | [ADR-0005](adr/0005-gemini-llm-provider.md) |
| TDD approach | [ADR-0006](adr/0006-tdd-approach.md) |

## 9. References

- [Services](SERVICES.md)
- [Backend](BACKEND.md)
- [Frontend](FRONTEND.md)
- [Messaging](MESSAGING.md)
- [Testing](TESTING.md)
- [Execution Plan](EXECUTION-PLAN.md)
- [Security](SECURITY.md)
