# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.8.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | Moodle LmsClient (token webservice) |

## 1. Where the product is

LMS sync foundation plus optional Moodle adapter:

- `FakeLmsClient` always available (`demo-moodle-course`)
- `MoodleLmsClient` behind `LMS_MOODLE_ENABLED` + base URL + token
- Admin materials page can choose Fake or Moodle provider
- ADR-0007 records Fake-then-Moodle decision

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | Wire campus Moodle secrets / scheduled sync |
| — | ILIAS `LmsClient` if needed |
| — | University-email verification (deferred) |
| — | Remaining P3 polish (CSP, load test) as needed |

## 3. Done when

- Moodle client lists PDF resources from `core_course_get_contents` (unit-tested)
- Download appends webservice token without storing it on materials
- FE can select Moodle provider and numeric course id
