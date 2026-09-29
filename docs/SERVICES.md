# Service Catalog

| Field | Value |
|---|---|
| Document ID | DOC-SVC |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Tech lead |

## 1. Purpose

Service identities for Phase A and the target catalog.

## 2. Phase A deployables

| ID | Service | Dev port | Responsibility |
|---|---|---|---|
| SVC-002 | `identity-service` | 8081 | Users, credentials, roles, JWT, profiles |
| SVC-090 | `learning-service` | 8082 | Course, materials, ingestion, RAG, study, matching seed |

> SVC-090 is a Phase A aggregate. Internal packages map to SVC-003…007.

## 3. Target deployables

| ID | Service | Dev port | Phase A mapping |
|---|---|---|---|
| SVC-001 | `api-gateway` | 8080 | Deferred |
| SVC-002 | `identity-service` | 8081 | Same |
| SVC-003 | `course-service` | 8082 | learning/course + material metadata |
| SVC-004 | `ingestion-service` | 8083 | learning/ingestion |
| SVC-005 | `rag-service` | 8084 | learning/rag |
| SVC-006 | `study-service` | 8085 | learning/study |
| SVC-007 | `matching-service` | 8086 | learning/matching |
| SVC-008 | `notification-service` | 8087 | Deferred |

## 4. Phase A service specs

### SVC-002 identity-service

**Owns:** users, password hashes, roles, profiles  

**APIs:** register, login, `/api/me`, profile patch  

**Emits (later):** user.registered, profile.updated  

### SVC-090 learning-service

**Owns:** courses, enrollments, materials, chunks/vectors, flashcards, quizzes, match opt-ins  

**Admin APIs:** create/publish course; upload material; publish/unpublish material; ingest status  

**Student APIs:** enroll; list published ready materials; RAG query; cards; quiz; match opt-in; active count  

**Rules:**

- Only `COURSE_ADMIN` for that course may upload/change visibility  
- Retrieval filters: enrollment + `READY` + `PUBLISHED`  

## 5. Data ownership

| Deployable | Store |
|---|---|
| identity-service | `identity` DB/schema |
| learning-service | `learning` DB/schema + local PDF directory + pgvector |

No shared mutable tables across deployables. Share user IDs via JWT claims / logical IDs.

## 6. Extraction triggers (to split learning-service)

- Independent scaling of ingestion vs Q&A  
- Separate release cadence  
- Kafka adoption for material.uploaded / completed  
- Multiple developers owning subdomains  

## 7. References

- [Architecture](ARCHITECTURE.md)
- [Backend](BACKEND.md)
- [Messaging](MESSAGING.md)
- [Execution Plan](EXECUTION-PLAN.md)
