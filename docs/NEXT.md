# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.1.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Multi-turn study chat over published lectures (ChatGPT-like topic tutoring) |

## 1. Where the product is

Students can enroll in published courses, ask grounded questions in English or German, search across published lecture pages, generate lecture summaries, drill flashcards, take practice quizzes, track weak topics, build an exam-date study plan, opt into intent-based study-partner matching with consent-gated email exchange and one-click disconnect, keep private notes, and export or delete their personal data (`NFR-004`). Platform admins have an `/admin` panel. Course admins can retry failed PDF indexing.

## 2. This increment (build now)

| Step | Outcome | Use cases / NFRs |
|---|---|---|
| 1 | An enrolled student can start a multi-turn study chat scoped to selected published lectures (`POST /api/rag/chat/sessions`). | UC-020 |
| 2 | Follow-up messages reuse prior turns for clarification while every factual claim is re-grounded in fresh retrieval (`POST /api/rag/chat/sessions/{id}/messages`). | UC-020 |
| 3 | The course Q&A page becomes a ChatGPT-style study thread with starters, follow-ups, and “New chat”. | UC-020 |
| 4 | Study chats are included in personal-data export/purge and cleared on unenroll. | NFR-004 |

## 3. Rules for this increment

- Chat is never open-world: only selected published+ready materials are retrieved each turn.
- History is capped; factual claims must cite this turn’s excerpts.
- University-email verification is not part of this increment.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | University-email verification (`@uni-siegen.de`) (UC-004, FR-004) | Owner-owned later change |
| 2 | LMS material sync (`UC-060` / `FR-019`) | Needs institutional credentials |
| 3 | Streaming chat tokens | Nice-to-have after multi-turn lands |

## 5. Done when

- A student can chat about a topic, ask “explain simpler”, and get a grounded follow-up with citations.
- Changing lectures starts a fresh chat session.
- Chat sessions appear in `GET /api/me/data` and are purged on delete/unenroll.
