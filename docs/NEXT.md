# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.2.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Mid-senior hardening: async ingest, safer tokens, denylist, rate limits, springdoc |

## 1. Where the product is

Full grounded study platform on `main`: multi-turn study chat, search/summaries, flashcards/quizzes/plans, matching with consent-gated email, private notes, GDPR export/delete, platform admin, and OpenAPI via springdoc.

Recent hardening:
- PDF ingest runs asynchronously in production (`unicompanion.ingest.async=true`)
- Access tokens are memory-only (refresh cookie restores session)
- Refresh revocation is persisted in Postgres
- AI endpoints are rate-limited
- `/v3/api-docs` + Swagger UI come from springdoc (generated from controllers)

## 2. This increment (done)

| Step | Outcome |
|---|---|
| 1 | Async PDF indexing after upload/replace/retry |
| 2 | Memory-only access tokens |
| 3 | DB-backed refresh-token denylist |
| 4 | Rate limits on `/api/rag/**` and study generate |
| 5 | springdoc OpenAPI 3 + Swagger UI |

## 3. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | University-email verification | Owner-owned later change |
| 2 | OpenTelemetry traces/metrics | Ops depth after hardening |
| 3 | Streaming study chat | Product polish |
| 4 | LMS sync | Needs institutional credentials |

## 4. Done when

- Upload returns while indexing continues in the background (prod)
- Logout invalidates refresh tokens across restarts
- Burst AI calls return 429
- `/v3/api-docs` and `/swagger-ui.html` stay in sync with controllers
