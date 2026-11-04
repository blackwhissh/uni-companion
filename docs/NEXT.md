# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-11-04 |
| Owner | Project owner |
| Based on | Phase A code on `main` after the Round 6 QA fixes |

## 1. Where the product is

Phase A study tools are in the product: auth, courses, publish/unpublish, grounded Q&A, shared flashcard and quiz pools, and persisted quiz scores. Matching opt-in and an honest active count exist on the learning API, but the course home still tells students that matching is not available. Quiz attempts store only a score, so a student cannot see which questions they still miss.

Phase B in the [roadmap](ROADMAP.md) is the campus v1. This plan sequences the first useful slice of that phase and the leftover Phase A matching screen. Later rows stay backlog until this slice is in use.

## 2. This increment (build now)

| Step | Outcome | Use cases |
|---|---|---|
| 1 | Course home replaces the matching placeholder with an explicit consent step. Opt-in adds the student to the real opted-in count. Opt-out removes them immediately. Name and email are not shown to classmates. | UC-040, UC-041, UC-051 |
| 2 | Leaving a course deletes that student's opt-in, so they disappear from the count. | UC-051, NFR-003 |
| 3 | Each submitted quiz stores the outcome of every question. A course progress page shows the student's own attempts, best and latest scores, and the prompts they still miss. A later correct answer drops that prompt from the gap list. | UC-030, FR-031 |
| 4 | From a gap, the student can open Q&A with that prompt filled in, or return to the quiz. | UC-030 |

Out of this increment: suggesting named classmates, sharing contact details, university-email verification, and replacing a PDF.

## 3. Rules for this increment

- The active count is the number of current opt-ins for that course. It is never padded.
- Discoverability is off until the student confirms the consent dialog.
- Progress lists only the signed-in student's attempts for that course.
- A weak topic is a quiz prompt whose latest answer from that student was wrong. Wording is not rewritten by a model.
- Unenrolled students cannot read progress or change opt-in.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | Collaboration intents and partner suggestions (UC-042, UC-043) | Needs the consent step above before any classmate is listed |
| 2 | Contact requests that reveal an email only after accept (UC-044) | Contact sharing is a separate consent from the count |
| 3 | Replace a published PDF and re-ingest it (UC-016) | Stops stale chunks without changing the study loop |
| 4 | Search and short summaries over published materials (UC-023, UC-024) | Retrieval already exists; this is a new study surface |
| 5 | University-email verification and handing Course Admin to a professor (UC-004, UC-052) | Required before a real staff handoff, not before the classmate demo |

## 5. Done when

- `MatchApiTests` shows that unenroll drops the student from the active count.
- `StudyApiTests` shows private progress, a missed prompt, and that a later correct answer clears it.
- Course home tests opt in only after the consent dialog, and opt out again.
- The progress page tests render scores, a missed prompt, and the Q&A link.
