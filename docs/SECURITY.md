# Security and Privacy

| Field | Value |
|---|---|
| Document ID | DOC-SEC |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Tech lead |

## 1. Purpose

Baseline security and privacy controls for Uni Companion. Not a full DPIA.

## 2. Objectives

| ID | Objective |
|---|---|
| SO-1 | Protect credentials and JWTs |
| SO-2 | Prevent unauthorized access to course materials |
| SO-3 | Keep matching consent-based |
| SO-4 | Minimize data sent to Gemini |
| SO-5 | Preserve auditability via logs/correlation IDs |

## 3. Assets

| Asset | Sensitivity |
|---|---|
| Credentials | High |
| Canonical PDFs / chunks / embeddings | High |
| Profiles / match intents | Medium |
| Quiz/progress | Medium |
| Gemini prompts/responses | Medium (third party) |

## 4. Trust boundaries

```text
Browser → identity-service / learning-service
learning-service → PostgreSQL, local disk, Gemini API
```

Gemini is an **external processor**. Send only required chunks + question.

## 5. Access control matrix (Phase A)

| Action | Student | Course Admin | Platform Admin |
|---|---|---|---|
| Register/login | Yes | Yes | Yes |
| Enroll / study published ready materials | Yes | Yes (if enrolled or admin override for preview) | Yes |
| Upload canonical PDF | No | Yes (own course) | Yes (any course) |
| Publish/unpublish material | No | Yes (own course) | Yes (any course) |
| Delete material | No | Yes (own course) | Yes (any course) |
| Publish/unpublish course | No | Yes (own course) | Yes (any course) |
| Delete course | No | Yes (own course) | Yes (any course) |
| RAG / cards / quiz | If enrolled | If enrolled / admin preview policy | Admin preview policy |
| Matching opt-in | Yes | Optional | Optional |

**Hard rule:** Student study paths query only `READY ∧ PUBLISHED` materials for enrolled courses.

## 6. Controls

| Area | Control |
|---|---|
| AuthN | JWT Bearer; password hashing (bcrypt/argon2) |
| AuthZ | Role + enrollment + visibility checks |
| Secrets | Env / secret manager; never commit |
| Transport | HTTPS in non-local deployments |
| AI disclosure | UI states answers may be wrong (NFR-050) |
| Copyright | No public material marketplace; admin-sourced canonical files |
| Matching | Opt-in only |

## 7. Gemini-specific notes

| Topic | Guidance |
|---|---|
| Free tier | Expect rate limits; handle 429 gracefully |
| Data | Prefer settings/terms that limit training use where available |
| Logging | Avoid long-term storage of full prompts in app logs |

## 8. Open decisions

| Topic | Status |
|---|---|
| Formal DPIA | Not started |
| Invite-only enrollment vs open enroll | Open (Q-5 in use cases) |
| Admin preview of unpublished in RAG | TBD (default: admin may preview) |

## 9. References

- [Requirements](REQUIREMENTS.md) NFR-*
- [Use Cases](USE-CASES.md) UC-050, UC-018
- [Backend](BACKEND.md)
- [Execution Plan](EXECUTION-PLAN.md)
