# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.4.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Streaming study chat (senior plan P2) |

## 1. Where the product is

Full grounded study platform with Phase A hardening, OpenTelemetry, and streaming study chat:

- SSE endpoint `POST /api/rag/chat/sessions/{id}/messages/stream` (`token` → `done` / `error`)
- Vertex uses `streamGenerateContent`; Fake/chunked fallbacks keep CI deterministic
- Web study chat renders progressive tutor tokens, then replaces with the saved grounded session

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | University-email verification |
| 2 | Playwright E2E smoke |
| 3 | Remaining P1 (audit log, idempotency, readiness/liveness, FE error catalog) |
| 4 | LMS sync |

## 3. Done when

- Study chat answers appear token-by-token in the UI
- Non-stream `POST …/messages` still works
- Backend + Vitest cover the stream path
- Export/collector not required for tests
