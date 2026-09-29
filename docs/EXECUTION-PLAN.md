# Execution Plan

| Field | Value |
|---|---|
| Document ID | DOC-EXEC |
| Version | 0.2.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Project owner |
| Based on | [USE-CASES.md](USE-CASES.md) v0.2.1+, locked tech stack, [TESTING.md](TESTING.md) |

## 1. Purpose

Define **how** Uni Companion Phase A (MVP) will be built: locked stack, topology, workstreams, week plan, milestones, and exit criteria. Phase B/C are sequenced only at a high level.

## 2. Objectives

| Horizon | Objective |
|---|---|
| **Phase A** | One course, admin-owned PDFs, publish/unpublish, enrolled students can Q&A / cards / quiz / matching opt-in |
| **Phase B** | Real professor admins, verification, richer matching |
| **Phase C** | LMS sync, advanced intelligence |

**Phase A demo (S-1):** student completes enroll → study → match opt-in in ≤ 5 minutes (after admin prep S-0).

## 3. Locked technology stack

Decided in project chat. Changes require updating this document.

| ID | Topic | Decision |
|---|---|---|
| T1 | Frontend | React **Vite** SPA |
| T2 | Styling | **Tailwind CSS** |
| — | FE libs | React Router, TanStack Query |
| T3 | Java | **21** LTS |
| T4 | Build | **Maven** |
| T5 | MVP topology | `identity-service` + modular `learning-service` |
| T6 | API gateway | **Deferred** (call services directly in local MVP) |
| T7 | Auth | **JWT** (Bearer) |
| T8 | Kafka | **Deferred** in Phase A; async ingest via Spring jobs / status table |
| T9 | PDF storage | **Local disk** |
| T10 | PDF extract | **Apache PDFBox** |
| T11 | LLM / embeddings | **Google Gemini** (free tier first; interface for swap later) |
| T12 | DB / vectors | **PostgreSQL + pgvector** |
| T13 | Q&A API | **REST** first (SSE streaming later) |
| T14 | Repo | **Monorepo** `apps/web` + `services/*` |
| T15 | Local infra | **Docker Compose** (Postgres + pgvector image) |
| T16 | UI language | **English** first |

### Target vs Phase A

| Concern | Phase A | Later |
|---|---|---|
| Services | identity + learning (modular packages) | Split ingestion / rag / study / matching; gateway |
| Events | In-process / DB job status | Apache Kafka topic catalog |
| Storage | Local filesystem | MinIO/S3 |
| Streaming answers | No | SSE optional |

## 4. Repository layout (to scaffold)

```text
uni-companion/
  apps/
    web/                      # React Vite + Tailwind
  services/
    identity-service/         # auth, users, roles, JWT
    learning-service/         # course, materials, ingest, RAG, study, matching seed
  docs/
  docker-compose.yml          # PostgreSQL (+ pgvector)
  README.md
```

### `learning-service` internal modules (packages)

Keep extractable boundaries even while one deployable:

```text
com.unicompanion.learning
  course/          # courses, enrollments, publish course
  material/        # upload, ingest status, publish/unpublish material
  ingestion/       # PDFBox, chunk, embed (Gemini), pgvector write
  rag/             # retrieve + grounded Q&A (Gemini chat)
  study/           # flashcards, quizzes, attempts
  matching/        # opt-in, active count
```

## 5. Material state model (Phase A)

Two independent dimensions:

| Dimension | Values | Meaning |
|---|---|---|
| **Processing** | `UPLOADED` → `PROCESSING` → `READY` \| `FAILED` | Ingestion pipeline |
| **Visibility** | `UNPUBLISHED` \| `PUBLISHED` | Admin publish/unpublish |

**Student study rule:** material is usable in Q&A / cards / quiz / list **only if** `READY` **and** `PUBLISHED`, and student is **enrolled** (or actor is Course Admin).

## 6. Phase A scope (Must)

| UC | Capability | Actor |
|---|---|---|
| UC-005 | Bootstrap Course Admin | Operator / A-CADM |
| UC-001, UC-002, UC-003 | Register, login, profile | A-STU |
| UC-010, UC-014 | Create + publish course | A-CADM |
| UC-012, UC-013 | Upload PDF + processing status | A-CADM |
| UC-018 | Publish / unpublish material | A-CADM |
| UC-011, UC-015 | Enroll + open course | A-STU |
| UC-020, UC-021, UC-022 | Q&A, flashcards, quiz | A-STU |
| UC-040, UC-041 | Matching opt-in + active count | A-STU |
| UC-050 | Enrolled-only + published-only access | A-SYS |

**Out of Phase A:** Kafka mesh, gateway, professor self-serve onboarding, LMS sync, SSE, student canonical uploads, graph matching.

## 7. Workstreams

| ID | Stream | Deliverables | Primary UCs |
|---|---|---|---|
| W0 | Docs & freeze | This plan + stack locked + TDD approach | — |
| W1 | Scaffold | Monorepo, Compose Postgres, empty Spring apps, Vite app, **test runners (JUnit/Vitest)** | — |
| W2 | Identity | TDD: register/login/JWT/roles → then UI | UC-001,002,003,005 |
| W3 | Courses & materials | TDD: authz + visibility filters → upload/ingest/publish APIs → admin UI | UC-010–015,018,050 |
| W4 | RAG | TDD: retrieval gates → Gemini via fakes → real Gemini wiring | UC-020 |
| W5 | Study | TDD: generate/persist rules → cards/quiz UI | UC-021,022 |
| W6 | Matching seed | TDD: opt-in + count → UI | UC-040,041 |
| W7 | Demo harden | Fill gaps, S-0/S-1 manual, fix failing tests | S-0, S-1 |

## 8. Week plan (indicative)

| Week | Focus | Exit check |
|---|---|---|
| **1** | W0–W2 | Admin + student can register/login; JWT works; seed Course Admin |
| **2** | W3 | Admin uploads PDF → `READY`; can publish/unpublish; student enrolls and sees only published ready materials |
| **3** | W4–W5 | Grounded Q&A + flashcards + quiz on published material |
| **4** | W6–W7 | Matching opt-in + count; full S-0/S-1 demo with ≥1 classmate |

Adjust dates as needed; order of streams is fixed more than calendar.

## 9. Implementation sequence (detailed)

### Step 1 — Scaffold

- Create Maven multi-module or sibling services
- Vite React app with Tailwind, router, Query
- `docker-compose.yml` with Postgres + pgvector
- `.env.example` for `GEMINI_API_KEY`, DB URL, JWT secret
- Flyway baseline in both services

### Step 2 — Identity

- Tables: users, roles
- Endpoints: register, login, `GET /api/me`, patch profile
- Seed script: one `COURSE_ADMIN` (+ optional dual-role student)
- React: login/register screens, auth header injection

### Step 3 — Courses & materials

- Tables: courses, enrollments, materials (processing + visibility columns), ingestion_jobs
- Admin: create course, publish course, upload multipart PDF → disk path
- Async ingest: PDFBox → chunks → Gemini embeddings → pgvector
- Admin: publish / unpublish material (visibility only; does not delete vectors in MVP — unpublished excluded from retrieval)
- Student: list published courses, enroll, course home
- Enforce UC-050 on every material/study path

### Step 4 — RAG

- Retrieve top-k chunks for `courseId` where material `READY`+`PUBLISHED`
- Prompt Gemini with excerpts + question
- Return answer + citation metadata (materialId, page/chunk if available)
- React Q&A screen

### Step 5 — Study

- Generate flashcards / quiz via Gemini from retrieved or sampled chunks
- Persist decks, quizzes, attempts
- React review + quiz UI

### Step 6 — Matching seed

- Per-course opt-in boolean
- `active-count` = enrolled (and/or opted-in — decide in impl; document in API)
- React toggle + count on course home

### Step 7 — Demo

- Runbook: start Compose, services, web, set Gemini key, seed admin, S-0 then S-1
- Fix auth holes; empty/error states for `FAILED` ingest and unpublished materials

## 10. API sketch (Phase A)

Base URLs (local): identity `:8081`, learning `:8082` (exact ports in Compose/README later).

| Method | Path | Service | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | identity | |
| POST | `/api/auth/login` | identity | returns JWT |
| GET | `/api/me` | identity | |
| PATCH | `/api/me/profile` | identity | |
| POST | `/api/courses` | learning | admin |
| PATCH | `/api/courses/{id}/visibility` | learning | publish course |
| POST | `/api/courses/{id}/enroll` | learning | student |
| POST | `/api/courses/{id}/materials` | learning | admin upload |
| PATCH | `/api/materials/{id}/visibility` | learning | publish/unpublish |
| GET | `/api/courses/{id}/materials` | learning | student: published+ready only |
| POST | `/api/rag/query` | learning | enrolled |
| POST | `/api/study/flashcards/generate` | learning | |
| GET | `/api/study/flashcards` | learning | |
| POST | `/api/study/quizzes/generate` | learning | |
| POST | `/api/study/quizzes/{id}/submit` | learning | |
| PUT | `/api/match/courses/{id}/opt-in` | learning | |
| GET | `/api/match/courses/{id}/active-count` | learning | |

## 11. Environment & secrets

| Variable | Purpose |
|---|---|
| `GEMINI_API_KEY` | Embeddings + chat |
| `JWT_SECRET` | Token signing |
| `DATABASE_URL` / Spring datasource | Postgres |
| `STORAGE_LOCAL_PATH` | PDF directory |

Never commit secrets. Document in `.env.example` at scaffold time.

## 12. Test strategy — TDD (mandatory)

Normative detail: [TESTING.md](TESTING.md) · [ADR-0006](adr/0006-tdd-approach.md).

### Approach

Build Phase A with **Red → Green → Refactor**:

1. Write failing automated test for the acceptance rule  
2. Implement minimal code to pass  
3. Refactor; keep green  
4. Only then polish UI if needed  

### Pyramid (Phase A)

| Layer | What | Tools |
|---|---|---|
| Unit | Roles, enrollment, READY∧PUBLISHED gates, chunking | JUnit 5 |
| API | Happy + **negative** authz paths | MockMvc / WebTestClient |
| Integration | Upload → READY; unpublished excluded from RAG | Testcontainers |
| LLM | Fake `ChatModel` / `EmbeddingModel` in CI | Mockito |
| Frontend | Role-gated UI behavior | Vitest + RTL |
| Manual | S-0 / S-1 demo script | Checklist |

### Definition of Done (per UC slice)

- Acceptance tests green  
- Negative tests for security-sensitive paths where applicable  
- Manual demo step updated if user-visible  

### Milestone test gates

| Milestone | Extra exit criterion |
|---|---|
| M2 | Identity API tests green |
| M3 | Visibility/authz test suite green |
| M4 | RAG/study gate tests green (fakes OK) |
| M5 | Full `mvn test` + `npm test` green + S-1 manual |

## 13. Milestones & exit criteria

| Milestone | Exit criteria |
|---|---|
| **M1 Scaffold** | Web + both services start; DB migrates |
| **M2 Auth** | Seed admin + student JWT flows work |
| **M3 Materials** | Publish/unpublish respected in student UI and RAG |
| **M4 Study** | Q&A + cards + quiz on one published PDF |
| **M5 Demo** | S-1 ≤ 5 min; ≥ 5 classmates invited (target) |

**Phase A done when M5 met** and no P0 auth/data leaks on materials.

## 14. Phase B / C (backlog only)

| Phase | Items |
|---|---|
| **B** | Uni-email verify; professor admin handoff; material replace; search/summaries/DE-EN; intents + suggestions + contact; optional gateway |
| **C** | Kafka extraction; LMS sync; graph matching; agents; MinIO; SSE |

Do not start B features until M5 unless blocking.

## 15. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Gemini free-tier limits | Cache embeddings; rate-limit generate; swap provider via interface |
| Poor PDF text extraction | Prefer text-based slides; show clear `FAILED` + retry |
| Scope creep (microservices/Kafka early) | This plan forbids full mesh in Phase A |
| Skipping tests under demo pressure | DoD requires green tests; TDD before UI polish |
| Copyright / trust | Admin-only canonical upload; enrolled + published access |
| Empty matching | Seed classmates via invite; honest counts only |

## 16. Immediate next actions

1. Confirm this plan (minor edits OK).  
2. Scaffold monorepo + Compose + empty services/web **with test tooling**.  
3. Implement W2 Identity **via TDD**.  
4. Obtain Gemini API key and record setup in runbook.  

## 17. References

- [USE-CASES.md](USE-CASES.md)  
- [TESTING.md](TESTING.md)  
- [Documentation portal](README.md)  
- Project [README](../README.md)  

## 18. Document history

| Version | Date | Notes |
|---|---|---|
| 0.1.0 | 2026-09-27 | Initial execution plan; stack locked (Gemini + all rec) |
| 0.2.0 | 2026-09-27 | TDD approach mandatory; link TESTING.md / ADR-0006 |
