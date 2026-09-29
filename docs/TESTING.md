# Testing and TDD

| Field | Value |
|---|---|
| Document ID | DOC-TEST |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Tech lead |

## 1. Purpose

Define the **Test-Driven Development (TDD)** approach and test strategy for Uni Companion. Testing is part of delivery, not an afterthought.

## 2. Decision

Uni Companion shall be built using **TDD** for domain rules and service APIs in Phase A, with supporting integration tests for critical paths (materials visibility, RAG access, authz).

Related: [ADR-0006](adr/0006-tdd-approach.md).

## 3. TDD cycle (Red → Green → Refactor)

For each vertical slice of behavior:

```text
1. RED     Write a failing test that expresses the requirement / use case
2. GREEN   Write the minimal production code to pass
3. REFACTOR  Clean structure; keep tests green
4. COMMIT  Behavior + tests together
```

### What “done” means for a feature

A feature is not done when the UI looks right. It is done when:

1. Automated tests covering the acceptance rule are green  
2. Manual S-0/S-1 checks pass where applicable  

## 4. What we drive with TDD first

Prefer TDD for **rules that must not regress**:

| Area | Example tests (drive first) | UC / FR |
|---|---|---|
| AuthZ roles | Student cannot upload/publish material | UC-012,018; FR-016 |
| Enrollment gate | Non-enrolled cannot query RAG | UC-020,050 |
| Visibility gate | `UNPUBLISHED` excluded from student list + retrieval | UC-018; FR-015 |
| Processing gate | Not `READY` ⇒ not studyable even if published | UC-013,018 |
| Matching | Opt-in persisted; active count uses real data | UC-040,041 |
| Identity | Register/login JWT claims include role | UC-001,002,005 |

### What we do **not** over-TDD early

| Area | Approach |
|---|---|
| Pixel-perfect CSS | Light component tests; manual visual check |
| Gemini prompt wording | Contract tests with **fakes/mocks**; few live smoke tests |
| One-off spike of PDFBox edge cases | Characterization tests after spike if needed |

## 5. Test pyramid

```text
        /\
       /  \      E2E / manual demo scripts (few)
      /----\
     / API  \    @SpringBootTest / MockMvc / WebTestClient
    /--------\
   / Unit     \  JUnit domain + pure functions (many)
  /------------\
```

| Layer | Backend | Frontend |
|---|---|---|
| Unit | Domain services, policies, chunking helpers | Pure utils, hooks with mocked API |
| Slice / API | Controller + security tests | Component tests (React Testing Library) |
| Integration | Testcontainers Postgres (+ pgvector as needed) | MSW or mocked learning/identity APIs |
| E2E | Optional Playwright later | S-0/S-1 manual in Phase A; automate later |

## 6. TDD workflow per workstream

| Workstream | First failing tests before implementation |
|---|---|
| W2 Identity | `register` creates STUDENT; login returns JWT; admin seed has COURSE_ADMIN |
| W3 Materials | upload forbidden for STUDENT; publish toggles visibility; student list filters |
| W4 RAG | retrieval query ignores unpublished; unenrolled → 403 |
| W5 Study | quiz attempt persisted; generate requires published+ready |
| W6 Matching | opt-in true/false; count endpoint returns enrollment-based number |

Gemini calls in unit/API tests use a **fake** `ChatModel` / `EmbeddingModel`. One optional `@EnabledIfEnvironmentVariable` smoke test may call real Gemini in local/CI when a key is present.

## 7. Tooling (Phase A)

### Backend

| Tool | Use |
|---|---|
| JUnit 5 | Unit + integration |
| AssertJ / Hamcrest | Assertions |
| Spring Security Test | AuthZ |
| MockMvc or WebTestClient | HTTP API |
| Testcontainers | PostgreSQL |
| Mockito | Fakes for Gemini, clock, etc. |

### Frontend

| Tool | Use |
|---|---|
| Vitest | Unit / component runner (Vite-native) |
| React Testing Library | Component behavior |
| MSW (optional) | API mocking |

### Commands (target)

```text
# backend
mvn test

# frontend
npm test
```

CI (when added) shall run unit + API tests on every push; integration/Testcontainers as pipeline allows.

## 8. Definition of Ready / Done (testing)

### Ready to implement a UC slice

- Acceptance rule written as test name(s) or Given/When/Then  
- Actor + auth expectation clear  

### Done

- Red→Green→Refactor completed  
- Negative authz cases covered for security-sensitive UCs  
- No skipped tests without ticket/reason  

## 9. Example Given/When/Then (materials)

```text
Given a course with a READY material that is UNPUBLISHED
And a student enrolled in that course
When the student lists materials
Then the material is not returned

Given the same material is PUBLISHED
When the student lists materials
Then the material is returned

Given the material is PUBLISHED but PROCESSING
When the student calls RAG query
Then the system does not use that material's chunks
```

Implement these as automated tests **before** wiring the happy-path UI.

## 10. Non-functional requirement

| ID | Requirement |
|---|---|
| NFR-060 | Phase A features shall be developed using TDD for domain and API acceptance rules as defined in this document. |
| NFR-061 | Security-sensitive paths (upload, publish, RAG, enroll) shall have automated negative tests. |

## 11. References

- [Execution Plan](EXECUTION-PLAN.md)
- [Requirements](REQUIREMENTS.md)
- [Backend](BACKEND.md)
- [Frontend](FRONTEND.md)
- [ADR-0006](adr/0006-tdd-approach.md)
- [Use Cases](USE-CASES.md)
