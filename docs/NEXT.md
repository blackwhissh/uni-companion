# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.4.0 |
| Status | Draft |
| Last updated | 2026-11-04 |
| Owner | Project owner |
| Based on | Contact requests that reveal an email only after accept |

## 1. Where the product is

Study-partner matching can name classmates and share an account email only after accept. Course admins can upload and publish PDFs. Replacing a published PDF now deletes the old chunks, indexes the new file, and retires flashcards and quizzes built from the old file. Students study the new text once it is indexed. A failed replacement stays unpublished from study until a later file indexes.

## 2. This increment (build now)

| Step | Outcome | Use cases |
|---|---|---|
| 1 | A course admin replaces the PDF on an existing material. The material id and publish flag stay. | UC-016 |
| 2 | Old chunks are removed before the new file is indexed. Answers use the new text. | UC-016 |
| 3 | Saved flashcards and quizzes for that file are retired so the next study pass is built from the new text. | UC-016 |
| 4 | A replacement that cannot be read leaves no chunks, so students cannot study the stale or broken file. | UC-016 |

## 3. Rules for this increment

- Only the course owner or a platform admin can replace a file.
- The new file must be a PDF. The title stays unless a new title is sent.
- Visibility is not changed by a replacement. Study still requires the file to be indexed and published.
- Students and other course admins cannot replace someone else's file.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | Search and short summaries over published materials (UC-023, UC-024) | Retrieval already exists; this is a new study surface |
| 2 | University-email verification and handing Course Admin to a professor (UC-004, UC-052) | Required before a real staff handoff |

## 5. Done when

- Replacing a published PDF keeps the material id and drops the old chunk text from Q&A.
- The downloaded file is the replacement.
- A saved flashcard deck for that file is no longer the active deck.
- A failed replacement is hidden from the student list.
- The course console can replace a file and show that the new PDF is indexed.
