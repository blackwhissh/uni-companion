# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.12.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Load smoke script (NFR-021 baseline) |

## 1. Where the product is

Mid-senior path through LMS, CSP, Moodle deploy wiring, plus a load-smoke helper:

- `scripts/load-smoke.sh` — concurrent authenticated `GET /api/courses` with p50/p95 reporting
- Documented in TESTING.md for NFR-021 baselines

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | University-email verification (deferred — revisit for campus rollout) |
| — | ILIAS `LmsClient` if needed |
| — | Heavier load/CI gates (k6) if capacity targets are set |
| — | Turn on Moodle in prod once campus token exists |

## 3. Done when

- `scripts/load-smoke.sh` is executable and documents CONCURRENCY/REQUESTS
- TESTING.md points at it for NFR-021
