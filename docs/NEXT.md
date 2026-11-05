# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.9.0 |
| Status | Draft |
| Last updated | 2026-11-05 |
| Owner | Project owner |
| Based on | GDPR personal data export and account self-deletion (NFR-004) |

## 1. Where the product is

Students can enroll in published courses, ask grounded questions in English or German, search across published lecture pages, generate lecture summaries, drill flashcards, take practice quizzes, track weak topics, build an exam-date study plan, and opt into intent-based study-partner matching with consent-gated email exchange and one-click disconnect. Platform admins have an `/admin` panel to view accounts and course health, grant or revoke `COURSE_ADMIN`, and hand off course ownership (`UC-052`). Course admins can retry failed PDF indexing in one click (`NFR-010`).

University-email verification stays with the owner for a later change.

## 2. This increment (build now)

| Step | Outcome | Use cases / NFRs |
|---|---|---|
| 1 | An authenticated user can export a machine-readable JSON copy of their personal study and matching data (`GET /api/me/data` in learning-service + profile record from identity-service). | NFR-004 |
| 2 | An authenticated student can delete their personal learning data (`DELETE /api/me/data`) and account (`DELETE /api/me`), revoking their session and removing enrollments, quiz attempts, study plans, match opt-ins, and contact requests. | NFR-004 |
| 3 | The Profile page (`/profile`) offers **Download my data** and **Delete account** with a confirmation dialog, while protecting seeded platform/course admin accounts that still own courses from accidental self-deletion. | NFR-004, UC-003 |

## 3. Rules for this increment

- Personal data deletion in `learning-service` (`DELETE /api/me/data`) removes the caller's rows in `contact_requests` (as requester or recipient), `match_opt_ins` (cascading `match_intents`), `study_plans`, `quiz_attempts` (cascading `quiz_attempt_items`), `quiz_progress`, `flashcard_deck_progress`, `study_delivery_events`, `study_version_reports`, and `enrollments`. Shared course flashcards and quizzes stay intact for the course.
- Account deletion in `identity-service` (`DELETE /api/me`) rejects self-deletion for `ADMIN` accounts (`409 CONFLICT`) so the platform cannot be locked out of its operator account, and deletes the caller's `user_roles` and `users` rows while clearing refresh cookies.
- University-email verification is not part of this increment.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | University-email verification (`@uni-siegen.de`) (UC-004, FR-004) | The owner will add university mail later. |
| 2 | Student private notes isolated from canonical KB (UC-017, FR-018) | Phase C capability. |
| 3 | OpenAPI / Swagger endpoint documentation (NFR-032) | API documentation polish. |

## 5. Done when

- Calling `GET /api/me/data` returns the caller's enrollments, study plans, quiz attempts, match opt-ins, and contact requests without leaking another student's records.
- Calling `DELETE /api/me/data` followed by `DELETE /api/me` removes all personal records for the student and invalidates their login.
- On `/profile`, clicking **Download my data** downloads a JSON export and **Delete account** confirms, deletes personal data across both services, and signs the student out.
