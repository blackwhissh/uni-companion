# Requirements Specification

| Field | Value |
|---|---|
| Document ID | DOC-REQ |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Project owner |

## 1. Purpose

Normative functional and non-functional requirements for Uni Companion. Trace to [USE-CASES.md](USE-CASES.md) and [EXECUTION-PLAN.md](EXECUTION-PLAN.md).

## 2. Conventions

| Keyword | Meaning |
|---|---|
| **shall** | Mandatory for the stated phase |
| **should** | Strongly recommended |
| **may** | Optional |
| MoSCoW | Must / Should / Could / Won't |

## 3. Scope

### In scope (Phase A → B)

- Roles: Student, Course Admin (Professor in B)
- Admin upload of canonical PDFs; publish/unpublish per material
- Student enroll + grounded study + matching seed
- Stack per Execution Plan (Gemini, Postgres/pgvector, JWT, etc.)

### Out of scope

See Vision NG-* and Use Cases §14.

## 4. Functional requirements

### 4.1 Identity

| ID | Requirement | Priority | Phase | UC |
|---|---|---|---|---|
| FR-001 | The system shall allow student registration and authentication. | Must | A | UC-001,002 |
| FR-002 | The system shall support student profile updates. | Must | A | UC-003 |
| FR-003 | The system shall support roles including `STUDENT` and `COURSE_ADMIN`. | Must | A | UC-005 |
| FR-004 | The system should verify university email domains. | Should | B | UC-004 |
| FR-005 | The system should support granting Course Admin to professors/Fachschaft. | Should | B | UC-052 |

### 4.2 Courses and materials

| ID | Requirement | Priority | Phase | UC |
|---|---|---|---|---|
| FR-010 | Course Admins shall create and publish/unpublish courses. | Must | A | UC-010,014 |
| FR-011 | Students shall enroll in published courses. | Must | A | UC-011 |
| FR-012 | Course Admins shall upload PDF materials for a course. | Must | A | UC-012 |
| FR-013 | The system shall expose material processing status (`UPLOADED`, `PROCESSING`, `READY`, `FAILED`). | Must | A | UC-013 |
| FR-014 | Course Admins shall publish and unpublish each material independently of processing status. | Must | A | UC-018 |
| FR-015 | Students shall access study features only for materials that are `READY` and `PUBLISHED` while enrolled. | Must | A | UC-015,050 |
| FR-016 | Non-admins shall not upload or change visibility of canonical materials. | Must | A | UC-012,018 |
| FR-017 | Course Admins should replace materials with re-ingestion. | Should | B | UC-016 |
| FR-018 | Students may upload private notes that never enter the shared KB by default. | Could | C | UC-017 |
| FR-019 | The system may sync materials from an LMS. | Could | C | UC-060 |

### 4.3 Study AI

| ID | Requirement | Priority | Phase | UC |
|---|---|---|---|---|
| FR-020 | The system shall answer questions using RAG over allowed course materials. | Must | A | UC-020 |
| FR-021 | The system should return citations when available. | Should | A | UC-020 |
| FR-022 | The system shall generate flashcards from allowed materials. | Must | A | UC-021 |
| FR-023 | The system shall generate and score quizzes from allowed materials. | Must | A | UC-022 |
| FR-024 | The system should provide semantic search across lectures. | Should | B | UC-023 |
| FR-025 | The system should provide material summaries. | Should | B | UC-024 |
| FR-026 | The system should support DE/EN explanations. | Should | B | UC-025 |

### 4.4 Progress

| ID | Requirement | Priority | Phase | UC |
|---|---|---|---|---|
| FR-030 | The system shall persist quiz attempts. | Must | A | UC-022 |
| FR-031 | The system should expose progress and weak topics. | Should | B | UC-030 |
| FR-032 | The system may provide study plans. | Could | B/C | UC-031 |

### 4.5 Matching

| ID | Requirement | Priority | Phase | UC |
|---|---|---|---|---|
| FR-040 | Students shall opt in to study-partner discovery per course. | Must | A | UC-040 |
| FR-041 | The system shall show real active/enrolled counts only. | Must | A | UC-041 |
| FR-042 | Students should set collaboration intents. | Should | B | UC-042 |
| FR-043 | The system should suggest peers by course + intent. | Should | B | UC-043 |
| FR-044 | Students should request contact with accept/decline. | Should | B | UC-044 |
| FR-045 | The system may use graph-based complementary matching. | Could | C | UC-045 |

### 4.6 Platform

| ID | Requirement | Priority | Phase |
|---|---|---|---|
| FR-050 | Phase A may deploy identity + modular learning-service. | May | A |
| FR-051 | Target architecture shall allow microservice extraction and Kafka integration. | Must | Target |
| FR-052 | LLM/embedding providers shall be behind replaceable interfaces. | Must | A |

## 5. Non-functional requirements

| ID | Requirement | Priority |
|---|---|---|
| NFR-001 | Secrets shall not be committed to source control. | Must |
| NFR-002 | Canonical materials shall not be publicly listable without authz. | Must |
| NFR-003 | Matching visibility shall be opt-in. | Must |
| NFR-004 | The system should support GDPR-oriented minimization and deletion before wide release. | Should |
| NFR-005 | Prompts to Gemini shall include only necessary retrieved context. | Should |
| NFR-010 | Ingestion failures shall be visible and reprocessable by admin. | Must |
| NFR-011 | When Kafka is introduced, consumers shall be idempotent on `eventId`. | Must |
| NFR-012 | Services should expose health endpoints. | Should |
| NFR-013 | Requests should propagate a correlation ID in logs. | Should |
| NFR-020 | Upload HTTP shall not block on full ingestion completion. | Must |
| NFR-021 | Interactive Q&A latency targets shall be measured after baseline. | Should |
| NFR-030 | Each deployable service shall own its schema/database. | Must |
| NFR-031 | Schema changes shall use Flyway. | Must |
| NFR-032 | Public HTTP APIs should be documented with OpenAPI. | Should |
| NFR-040 | UI shall be English-first; structure should allow DE later. | Should |
| NFR-050 | UI shall disclose that answers are AI-generated and may be wrong. | Must |
| NFR-051 | Features shall not market unauthorized redistribution of lecture PDFs. | Must |
| NFR-060 | Phase A domain and API acceptance rules shall be developed using TDD (Red→Green→Refactor) per [TESTING.md](TESTING.md). | Must |
| NFR-061 | Security-sensitive paths (upload, publish/unpublish, RAG, enrollment gates) shall have automated negative tests. | Must |

## 6. Assumptions

| ID | Assumption |
|---|---|
| A-1 | Pilot Course Admin can legally provide PDFs for enrolled pilot students. |
| A-2 | Gemini API key available for development. |
| A-3 | Docker available for Postgres locally. |
| A-4 | Initial tenant is Universität Siegen. |

## 7. References

- [Vision](VISION.md)
- [Use Cases](USE-CASES.md)
- [Execution Plan](EXECUTION-PLAN.md)
- [Security](SECURITY.md)
