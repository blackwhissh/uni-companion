# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.11.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Moodle deploy env wiring |

## 1. Where the product is

Mid-senior path through LMS + CSP, with prod compose ready for campus Moodle secrets:

- `docker-compose.prod.yml` + `.env.prod.example` expose `LMS_MOODLE_*` and `LMS_SYNC_*`
- Defaults keep Moodle and scheduled sync **off** until a token is supplied

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | Load test / remaining ADR hygiene |
| — | University-email verification (deferred) |
| — | ILIAS `LmsClient` if needed |
| — | Turn on Moodle in prod once campus token exists |

## 3. Done when

- Prod learning service receives `LMS_MOODLE_*` / `LMS_SYNC_*` from compose
- Example env documents how to enable without committing secrets
