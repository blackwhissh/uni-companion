# Next development plan

| Field | Value |
|---|---|
| Document ID | DOC-NEXT |
| Version | 1.3.0 |
| Status | Draft |
| Last updated | 2026-11-16 |
| Owner | Project owner |
| Based on | OpenTelemetry traces/metrics for learning + identity |

## 1. Where the product is

Full grounded study platform with Phase A hardening (async ingest, safer tokens, denylist, rate limits, springdoc) plus OpenTelemetry:

- `spring-boot-starter-opentelemetry` on both services
- Custom spans: `learning.pdf.ingest`, `learning.rag.query`, `learning.rag.chat.message`, `learning.study.flashcards.generate`, `learning.study.quiz.generate`
- Export off by default; enable with `OTEL_EXPORT_ENABLED=true` and optional `docker compose --profile otel up`

## 2. After this increment

| Order | Capability |
|---|---|
| 1 | Streaming study chat |
| 2 | University-email verification |
| 3 | Playwright E2E smoke |
| 4 | LMS sync |

## 3. Done when

- Services start with OTEL wired
- AI/ingest paths emit named observations
- Tests do not require a collector
- Optional Grafana LGTM profile is documented
