# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.6.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Remaining P1 operability (audit, idempotency, probes, FE errors) |

## 1. Where the product is

Full grounded study platform with hardening, OpenTelemetry, streaming study chat, Playwright smoke, plus remaining mid-senior P1 operability:

- Liveness vs readiness probes configured on identity + learning
- Audit events for privileged account/course/material actions
- `Idempotency-Key` on material upload/replace and study generate
- Shared FE `userFacingError` catalog

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | LMS sync |
| — | University-email verification (deferred; revisit for campus rollout) |
| — | Remaining P3 polish (CSP, load test, ADR hygiene) as needed |

## 3. Done when

- `/actuator/health/liveness` and `/readiness` return 200 in tests
- Grant/revoke course-admin writes `audit_events`
- Repeated generate with the same Idempotency-Key replays the first response
- FE maps status/code errors through the catalog
