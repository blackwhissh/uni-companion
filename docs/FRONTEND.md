# Frontend Specification

| Field | Value |
|---|---|
| Document ID | DOC-FE |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Frontend engineer |

## 1. Purpose

Specify the React client for Uni Companion (Phase A focus).

## 2. Stack (locked)

| Concern | Choice |
|---|---|
| Library | React |
| Tooling | Vite |
| Styling | Tailwind CSS |
| Routing | React Router |
| Server state | TanStack Query |
| Language | English first |
| Auth client | JWT in memory (prefer) or sessionStorage; Authorization Bearer header |

## 3. Structure

```text
apps/web/src/
  app/                 # providers, router, layouts
  shared/              # UI primitives, api clients
  features/
    auth/
    profile/
    admin-courses/     # create, publish course, upload, publish/unpublish material
    courses/           # enroll, course home
    study-qa/
    flashcards/
    quizzes/
    matching/
```

## 4. Screens (Phase A)

| ID | Audience | Route (draft) | UC |
|---|---|---|---|
| FE-S01 | All | `/login`, `/register` | UC-001,002 |
| FE-S02 | Student | `/profile` | UC-003 |
| FE-S10 | Admin | `/admin/courses` | UC-010,014 |
| FE-S11 | Admin | `/admin/courses/:id/materials` | UC-012,013,018 |
| FE-S20 | Student | `/courses` | UC-011 |
| FE-S21 | Student | `/courses/:id` | UC-015,040,041 |
| FE-S22 | Student | `/courses/:id/qa` | UC-020 |
| FE-S23 | Student | `/courses/:id/flashcards` | UC-021 |
| FE-S24 | Student | `/courses/:id/quiz` | UC-022 |

## 5. UX rules

| ID | Rule |
|---|---|
| UX-1 | Student demo path ≤ 5 minutes after materials published |
| UX-2 | Show AI disclaimer on Q&A / generated content |
| UX-3 | Admin clearly sees processing vs visibility (two statuses) |
| UX-4 | Unpublished materials never appear in student study UI |
| UX-5 | Matching is explicit opt-in |
| UX-6 | Active counts are real only |

## 6. Integration

| Item | Detail |
|---|---|
| Identity base URL | e.g. `http://localhost:8081` |
| Learning base URL | e.g. `http://localhost:8082` |
| Env | `VITE_IDENTITY_API_URL`, `VITE_LEARNING_API_URL` |
| Long jobs | Poll material processing status |

## 7. Out of scope (FE Phase A)

- Native mobile  
- i18n full DE pack  
- SSE streaming answers  
- Professor self-serve onboarding UI (seeded admin is enough)

## 8. Testing (TDD-aligned)

Frontend follows the same delivery discipline where behavior is non-trivial. Details: [TESTING.md](TESTING.md).

| Layer | Practice | Tools |
|---|---|---|
| Component | Write failing RTL test for role-gated UI (e.g. student does not see Upload) before implementing | Vitest, React Testing Library |
| Hook / API client | Mock identity/learning clients; assert headers and error mapping | Vitest |
| Visual / Tailwind | Manual check; no TDD requirement for styling alone | — |
| E2E | Manual S-0/S-1 in Phase A; Playwright optional later | — |

Admin vs student route guards and “unpublished hidden” list behavior are **TDD candidates** on the client; server tests remain the source of truth for authz.

## 9. References

- [Use Cases](USE-CASES.md)
- [Execution Plan](EXECUTION-PLAN.md)
- [Architecture](ARCHITECTURE.md)
- [Testing](TESTING.md)
- [ADR-0003](adr/0003-react-vite-frontend.md)
- [ADR-0006](adr/0006-tdd-approach.md)
