# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.5.0 |
| Status | Draft |
| Last updated | 2026-11-04 |
| Owner | Project owner |
| Based on | Search and short summaries over published lectures |

## 1. Where the product is

Enrolled students can search published lectures and open a short summary of one lecture. Search returns pages from indexed, published PDFs and does not call the answer model. A summary uses only the chosen lecture. Unpublished files stay out of both. Replacing a published PDF still drops the old text, re-indexes the new file, and retires saved flashcards and quizzes. Study-partner matching still shares an email only after a contact request is accepted.

## 2. This increment (build now)

| Step | Outcome | Use cases |
|---|---|---|
| 1 | An enrolled student searches across published lectures and opens a matching page. | UC-023 |
| 2 | Search with no matching page returns an empty list. Unpublished text never appears. | UC-023 |
| 3 | An enrolled student asks for a short summary of one published lecture. | UC-024 |
| 4 | The summary cites pages from that lecture only. An unpublished lecture cannot be summarized. | UC-024 |

## 3. Rules for this increment

- Search and summaries require enrollment. An unpublished course stays hidden from students.
- Search covers every ready, published material unless the request names a subset. Every named material must be ready and published.
- A summary is computed for one material. It is not stored.
- Bilingual explain (UC-025) stays out of this increment.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | University-email verification and handing Course Admin to a professor (UC-004, UC-052) | Required before a real staff handoff |

## 5. Done when

- A search for a sentence in one lecture ranks that lecture first and omits unpublished text.
- A blank search is rejected. A student who is not enrolled cannot search.
- A summary of one lecture contains its sentence and omits another lecture.
- Summarizing an unpublished lecture is rejected.
- The course home links to search and to the summary on the same page.
