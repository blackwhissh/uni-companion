# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.9.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Scheduled LMS sync |

## 1. Where the product is

LMS sync with Fake + optional Moodle, plus optional scheduled pull:

- Manual Sync now on admin materials
- `unicompanion.lms.sync.scheduled-enabled` + cron (default every 6h when enabled)
- Scheduled runs audit as course owner with correlation id `scheduled-lms-sync`

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | Wire campus Moodle secrets in deploy |
| — | ILIAS `LmsClient` if needed |
| — | University-email verification (deferred) |
| — | Remaining P3 polish (CSP, load test) as needed |

## 3. Done when

- Enabled links are discoverable via `listEnabledLinks`
- `syncScheduled` imports Fake demo PDFs and writes audit with `scheduled-lms-sync`
- Scheduler bean only loads when `scheduled-enabled=true` (off in tests)
