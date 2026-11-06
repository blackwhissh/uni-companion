# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.0.0 |
| Status | Draft |
| Last updated | 2026-11-06 |
| Owner | Project owner |
| Based on | Private student notes (UC-017) and OpenAPI 3.1 specifications (NFR-032) |

## 1. Where the product is

Students can enroll in published courses, ask grounded questions in English or German, search across published lecture pages, generate lecture summaries, drill flashcards, take practice quizzes, track weak topics, build an exam-date study plan, opt into intent-based study-partner matching with consent-gated email exchange and one-click disconnect, and export or delete their personal data (`NFR-004`). Platform admins have an `/admin` panel to view accounts and course health, grant or revoke `COURSE_ADMIN`, and hand off course ownership (`UC-052`). Course admins can retry failed PDF indexing in one click (`NFR-010`).

University-email verification stays with the owner for a later change.

## 2. This increment (build now)

| Step | Outcome | Use cases / NFRs |
|---|---|---|
| 1 | An enrolled student can create, list, edit, and delete private course notes (`GET/POST /api/notes/courses/{courseId}`, `PUT/DELETE /api/notes/courses/{courseId}/{noteId}`). | UC-017, FR-018 |
| 2 | Private student notes remain strictly isolated from the shared canonical course knowledge base and cannot be read or modified by classmates or admins. | UC-017, FR-018, UC-050 |
| 3 | Personal data export (`GET /api/me/data`) includes the student's private notes, and account/course departure (`DELETE /api/me/data`, unenroll) cleans them up. | NFR-004, UC-017 |
| 4 | Both `identity-service` and `learning-service` expose OpenAPI 3.1 specifications at `GET /v3/api-docs`. | NFR-032 |

## 3. Rules for this increment

- Private notes require enrollment in the course (`enrollments`) and are keyed by `(id, course_id, user_id)` in `student_notes`.
- Private notes never enter `material_chunks`, RAG retrieval, flashcard generation, or quiz generation.
- `GET /v3/api-docs` is publicly readable (`permitAll`) on both `identity-service` (`:8081`) and `learning-service` (`:8082`) and documents each service's HTTP endpoints, security scheme, and schemas.
- University-email verification is not part of this increment.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | University-email verification (`@uni-siegen.de`) (UC-004, FR-004) | The owner will add university mail later. |
| 2 | LMS material sync (`UC-060` / `FR-019`) | Requires institutional Moodle/ILIAS credentials. |

## 5. Done when

- An enrolled student can add, edit, and delete a private note on the course home page, and another student or admin cannot see it.
- Private notes are included in `GET /api/me/data` and purged on `DELETE /api/me/data`.
- `GET /v3/api-docs` returns valid OpenAPI 3.1.0 JSON on both `identity-service` and `learning-service`.
