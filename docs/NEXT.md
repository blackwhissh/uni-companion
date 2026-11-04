# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.6.0 |
| Status | Draft |
| Last updated | 2026-11-04 |
| Owner | Project owner |
| Based on | Grounded explanations in English or German |

## 1. Where the product is

Enrolled students can ask a grounded question or summarize one published lecture in English or German. The cited pages stay the published text. Search still ranks pages from indexed, published PDFs. Replacing a published PDF drops the old text and retires saved flashcards and quizzes. Study-partner matching shares an email only after a contact request is accepted.

University-email verification stays with the owner for a later change.

## 2. This increment (build now)

| Step | Outcome | Use cases |
|---|---|---|
| 1 | An enrolled student chooses English or German for a Q&A answer. | UC-025 |
| 2 | A German question with no choice is still explained in German. An English choice overrides that. | UC-025 |
| 3 | A lecture summary can be written in English or German. | UC-025 |
| 4 | The source sentence remains in the answer. An unknown language is rejected. | UC-025 |

## 3. Rules for this increment

- The explanation language is English or German. Omitting it on a question follows the question.
- Omitting it on a summary uses English.
- Citations and excerpts stay the published page text.
- University-email verification is not part of this increment.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | A simple study plan from quiz gaps (UC-031) | Uses progress that already exists; optional exam-week aid |
| 2 | University-email verification and handing Course Admin to a professor (UC-004, UC-052) | The owner will add university mail later. Handoff waits on that verification. |

## 5. Done when

- An English question with German selected returns German headings and the source sentence.
- A German question with English selected returns English headings and the source sentence.
- A German question with no language selected is explained in German.
- A German summary contains the lecture sentence and a German lead-in.
- An unknown language code is rejected.
- Q&A and the summary form both offer English and Deutsch.
