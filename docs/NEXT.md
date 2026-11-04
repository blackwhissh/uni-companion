# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.7.0 |
| Status | Draft |
| Last updated | 2026-11-04 |
| Owner | Project owner |
| Based on | A study plan from quiz gaps and an exam date |

## 1. Where the product is

Enrolled students can set an exam date and get a plan that places missed quiz questions on the days before it. A later correct answer drops that question from the plan. Q&A and summaries can be written in English or German, and the cited pages stay the published text. Search, PDF replacement, and contact requests are unchanged.

University-email verification stays with the owner for a later change.

## 2. This increment (build now)

| Step | Outcome | Use cases |
|---|---|---|
| 1 | An enrolled student saves an exam date for a course. | UC-031 |
| 2 | With no quizzes yet, the plan asks for a quiz today and a review on the exam date. | UC-031 |
| 3 | A still-missed question is scheduled before the exam and links back to Q&A. | UC-031 |
| 4 | Another student cannot read that plan. A past date is rejected. | UC-031 |

## 3. Rules for this increment

- The plan is per student and per course. It requires enrollment.
- Steps are derived from quiz progress each time the plan is read. Only the exam date is stored.
- An exam date is today through one year ahead.
- The plan does not call the answer model.
- University-email verification is not part of this increment.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | University-email verification and handing Course Admin to a professor (UC-004, UC-052) | The owner will add university mail later. Handoff waits on that verification. |

## 5. Done when

- Saving an exam date before any quiz returns a quiz step today and a review on the exam date.
- After a missed question, the plan names that prompt on a day before the exam.
- A classmate does not see the plan.
- A past exam date is rejected.
- The course home opens the plan, and a gap links to Q&A.
