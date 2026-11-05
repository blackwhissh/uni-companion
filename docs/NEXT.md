# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.8.0 |
| Status | Draft |
| Last updated | 2026-11-05 |
| Owner | Project owner |
| Based on | Platform admin panel, course handoff, failed PDF retry, and matching controls |

## 1. Where the product is

Students can enroll in published courses, ask grounded questions in English or German, search across published lecture pages, generate lecture summaries, drill flashcards, take practice quizzes, track weak topics, build an exam-date study plan, and opt into intent-based study-partner matching with consent-gated email exchange. Platform admins have an `/admin` panel to view accounts and course health and grant or revoke `COURSE_ADMIN`.

University-email verification stays with the owner for a later change.

## 2. This increment (build now)

| Step | Outcome | Use cases / NFRs |
|---|---|---|
| 1 | A platform admin or course owner can transfer course ownership to another course admin (`PATCH /api/courses/{id}/owner`). | UC-052 |
| 2 | A course admin or platform admin can retry failed PDF indexing in one click (`POST /api/materials/{id}/retry`) without re-uploading the file. | NFR-010, UC-013 |
| 3 | Students can cancel an outgoing pending contact request or disconnect an accepted study-partner connection (`DELETE /api/match/courses/{courseId}/contacts/{requestId}`). | UC-044, UC-051 |
| 4 | A user on the Not Allowed screen can refresh their session permissions immediately after being granted a role, and route code-splitting keeps the initial web bundle below Vite's size warning threshold. | UC-052, FE |

## 3. Rules for this increment

- Only the current course owner or a platform admin (`ADMIN`) may transfer ownership of a course, and the new owner ID is required.
- Only a material in `FAILED` processing status may be retried via `/api/materials/{id}/retry`; retrying clears the failure reason, resets status to `UPLOADED`, and re-runs extraction over the stored PDF.
- Either participant (`requester` or `recipient`) may remove a pending or accepted contact record in a course they belong to, immediately revoking shared email visibility.
- University-email verification is not part of this increment.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | University-email verification (`@uni-siegen.de`) (UC-004, FR-004) | The owner will add university mail later. |
| 2 | Account self-deletion & GDPR data export (NFR-004) | Next privacy milestone before wide campus release. |
| 3 | Student private notes isolated from canonical KB (UC-017, FR-018) | Phase C capability. |
| 4 | OpenAPI / Swagger endpoint documentation (NFR-032) | API documentation polish. |

## 5. Done when

- A platform admin can reassign a course's owner from the Admin panel and the new owner can manage the course and its materials.
- A failed PDF material shows a "Retry indexing" action in the admin materials console and transitions back to `READY` when extraction succeeds.
- A student can cancel an outgoing contact request or disconnect an accepted match from the study-partner panel.
- All backend (`mvn test`) and frontend (`npm test` and `npm run build`) checks pass without bundle size warnings.
