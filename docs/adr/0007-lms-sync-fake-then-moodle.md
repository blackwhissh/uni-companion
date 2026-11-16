# ADR-0007: LMS Sync via Port + Fake, then Moodle

| Field | Value |
|---|---|
| ADR | ADR-0007 |
| Status | Accepted |
| Date | 2026-11-16 |

## Context

UC-060 / FR-019 need materials to stay aligned with campus LMS (Moodle/ILIAS) without forever re-uploading. Full campus OAuth and every LMS quirk is Phase C work; we still need a shippable sync path now.

## Decision

1. Introduce an `LmsClient` port (`listFiles` / `download`) selected by the course link’s `provider`.
2. Ship `FakeLmsClient` always (local/CI demo course `demo-moodle-course`).
3. Ship `MoodleLmsClient` behind `unicompanion.lms.moodle.enabled`, using a site webservice token and `core_course_get_contents` for PDF resources.
4. Upsert into existing `materials` with `(course, provider, external file id)` + checksum skip/update; keep synced files unpublished until an admin publishes.

## Alternatives considered

| Option | Why not |
|---|---|
| Wait for full Moodle OAuth before any sync | Blocks the materials upsert path and admin UX |
| Auto-publish on sync | Too aggressive for draft/wrong files |
| Replace Uni Companion materials store with LMS links only | Breaks offline ingest, citations, and study artifacts |

## Consequences

- Real Moodle needs `LMS_MOODLE_BASE_URL` + `LMS_MOODLE_TOKEN` (and enabled flag).
- ILIAS can add another `LmsClient` without schema churn.
- Token must never be stored on material rows; download URLs are resolved at sync time.
