# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.7.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | LMS sync foundation (UC-060 first slice) |

## 1. Where the product is

Full grounded study platform through P1 operability, plus a first LMS sync slice:

- Course LMS link (`PUT/GET /api/courses/{id}/lms`)
- Sync (`POST .../lms/sync`) imports PDFs via `LmsClient` (Fake provider today)
- Materials keyed by `(course, provider, external file id)` with checksum skip/update
- Admin materials page: link + Sync now

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | Real Moodle (or ILIAS) `LmsClient` + secrets |
| — | University-email verification (deferred; revisit for campus rollout) |
| — | Remaining P3 polish (CSP, load test, ADR hygiene) as needed |

## 3. Done when

- Linking `demo-moodle-course` and syncing imports two unpublished PDFs
- Re-sync with unchanged checksums skips both files
- Students cannot link/sync; audit records `LMS_SYNC`
