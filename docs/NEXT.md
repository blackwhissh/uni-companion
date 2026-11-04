# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 0.3.0 |
| Status | Draft |
| Last updated | 2026-11-04 |
| Owner | Project owner |
| Based on | Collaboration intents and partner suggestions |

## 1. Where the product is

Students can opt in per course, choose a collaboration intent, and see classmates who share that intent. A contact request now shares the account email only after the other student accepts. Pending and declined requests do not include an email. Leaving the course, or opting out, drops pending requests. An email that was already accepted stays available to those two students.

## 2. This increment (build now)

| Step | Outcome | Use cases |
|---|---|---|
| 1 | An opted-in student can ask a suggested classmate to connect. | UC-044 |
| 2 | The recipient accepts or declines. Accept reveals each person's account email to the other person only. | UC-044 |
| 3 | Anyone else, including a classmate who has not opted in, cannot read those emails. | UC-051 |

## 3. Rules for this increment

- A request is allowed only when both students are opted in and share an intent.
- The requester cannot accept their own request.
- Opting out or leaving the course deletes pending requests. Accepted emails remain.
- The address is the account email on the access token, saved with the opt-in. It is not taken from the request body.

## 4. After this increment

| Order | Capability | Why it waits |
|---|---|---|
| 1 | Replace a published PDF and re-ingest it (UC-016) | Stops stale chunks without changing matching |
| 2 | Search and short summaries over published materials (UC-023, UC-024) | Retrieval already exists; this is a new study surface |
| 3 | University-email verification and handing Course Admin to a professor (UC-004, UC-052) | Required before a real staff handoff |

## 5. Done when

- A pending or declined request response contains no email address.
- After accept, each student sees the other's address and not a third student's view of it.
- Opting out removes a pending request and keeps an accepted address.
- The course home hides the address until Accept, then shows it.
