# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.2.0 |
| Status | Draft |
| Last updated | 2026-11-04 |
| Owner | Project owner |
| Based on | Consented study-partner counts and quiz progress |

## 1. Where the product is

Students can opt in to a per-course study-partner count, see their own quiz scores and missed prompts, and open Q&A on a missed prompt. Email is not shared. Classmates are not listed until they choose a collaboration intent.

## 2. This increment (build now)

| Step | Outcome | Use cases |
|---|---|---|
| 1 | An opted-in student chooses one or more intents: study group, project, thesis, language practice. | UC-042 |
| 2 | Suggestions list other opted-in classmates in that course who share at least one intent. The row shows display name and the shared intents. | UC-043 |
| 3 | A student who has not opted in gets an empty list and does not see names. Leaving the course removes intents with the opt-in. | UC-051 |
| 4 | Suggestion payloads do not include email. | UC-044 still later |

## 3. Rules for this increment

- No shared intent means no suggestion. The list is never padded.
- Display name is the name saved with the opt-in. A blank name is not listed.
- Suggestions from another course stay in that course.
- Email, and any request to reveal it, stays out of this increment.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | Contact requests that reveal an email only after accept (UC-044) | Naming a classmate is a separate consent from sharing an address |
| 2 | Replace a published PDF and re-ingest it (UC-016) | Stops stale chunks without changing matching |
| 3 | Search and short summaries over published materials (UC-023, UC-024) | Retrieval already exists; this is a new study surface |
| 4 | University-email verification and handing Course Admin to a professor (UC-004, UC-052) | Required before a real staff handoff |

## 5. Done when

- Partner suggestion tests keep a classmate with no shared intent off the list, and drop someone who unenrolls.
- An enrolled student who has not opted in cannot read names.
- The suggestion JSON has no email.
- The course home shows a suggestion only after opt-in and an intent is selected.
