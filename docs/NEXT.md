# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.5.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Playwright E2E smoke (senior plan; uni-email skipped) |

## 1. Where the product is

Full grounded study platform with Phase A hardening, OpenTelemetry, streaming study chat, and Playwright browser smoke:

- SSE study chat: `POST /api/rag/chat/sessions/{id}/messages/stream` (`token` → `done` / `error`)
- Playwright smoke: landing + student login → courses (+ bad-credentials)
- Runner: `bash scripts/e2e-smoke.sh` (Postgres + identity + learning/fake + Vite QA proxy)
- CI job `Playwright smoke` gates deploy with backend/frontend tests
- University-email verification is skipped for now

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | Remaining P1 (audit log, idempotency, readiness/liveness, FE error catalog) |
| 2 | LMS sync |
| — | University-email verification (deferred; revisit for campus rollout) |

## 3. Done when

- Playwright smoke runs locally via `scripts/e2e-smoke.sh`
- CI runs the same path on PRs
- Specs stay thin (happy path + one auth failure) — not a full UI suite
