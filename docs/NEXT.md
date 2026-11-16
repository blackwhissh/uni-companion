# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.10.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | CSP / security headers (P3) |

## 1. Where the product is

LMS sync path complete through scheduled pulls, plus browser/API security headers:

- Caddy SPA: CSP allowing self + Google Fonts + `blob:` PDF previews
- Identity + learning APIs: `default-src 'none'` CSP, nosniff, deny framing, referrer + permissions policy

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | Wire campus Moodle secrets in deploy |
| — | Load test / ADR hygiene as needed |
| — | University-email verification (deferred) |
| — | ILIAS `LmsClient` if needed |

## 3. Done when

- Caddyfile sets CSP / nosniff / frame deny for the SPA
- Actuator health responses include API CSP + X-Frame-Options DENY in tests
