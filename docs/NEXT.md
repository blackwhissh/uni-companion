# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.13.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Mid-senior path complete pending campus-dependent items |

## 1. Where the product is

Mid-senior hardening and LMS path on this stack branch:

| Area | Status |
|---|---|
| P1 operability (probes, audit, idempotency, FE errors) | Done |
| Streaming study chat + Playwright smoke | Done |
| LMS Fake sync + Moodle client + schedule + deploy env | Done |
| CSP / security headers | Done |
| Load-smoke baseline (NFR-021) | Done |

## 2. Blocked / deferred (need campus or product input)

| Item | Needs |
|---|---|
| Turn on Moodle in prod | Campus webservice token (`LMS_MOODLE_*`) |
| University-email verification | Explicitly deferred earlier; revisit for campus rollout |
| ILIAS `LmsClient` | Only if campus uses ILIAS instead of/in addition to Moodle |
| k6 CI gates | Capacity targets / SLOs |

## 3. Suggested next when unblocked

1. Enable Moodle in a staging `.env.prod` and sync one real course  
2. Or pick university-email verification for campus rollout  
3. Or set latency budgets from `scripts/load-smoke.sh` and add k6  
