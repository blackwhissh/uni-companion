# Product Roadmap

| Field | Value |
|---|---|
| Document ID | DOC-ROAD |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Project owner |

## 1. Purpose

Sequence product delivery across Phases A–C. Details of Phase A execution live in [EXECUTION-PLAN.md](EXECUTION-PLAN.md).

## 2. Phase overview

```text
A MVP  →  B Campus v1  →  C Institutional / advanced
```

| Phase | Intent | Primary actors |
|---|---|---|
| **A** | Vertical demo, one course | Project owner as admin + classmates |
| **B** | Cohort-ready campus product | Professors/Fachschaft + verified students |
| **C** | Scale & intelligence | LMS, multi-course, advanced matching/agents |

## 3. Phase A — MVP

**Outcome:** Admin publishes materials; students enroll and study with Gemini RAG; matching opt-in works.

| Capability | FR / UC |
|---|---|
| Auth + roles | FR-001–003 |
| Course create/publish | FR-010 |
| Material upload + process + publish/unpublish | FR-012–016 |
| Enroll + study home | FR-011,015 |
| Q&A, cards, quiz | FR-020–023,030 |
| Matching opt-in + count | FR-040–041 |
| Access control | FR-015,016; NFR-002 |

**Exit:** Milestones M1–M5 in Execution Plan.

## 4. Phase B — Campus v1

| Capability | FR / UC |
|---|---|
| Uni-email verification | FR-004 |
| Professor admin handoff | FR-005 |
| Material replace | FR-017 |
| Search, summaries, DE/EN | FR-024–026 |
| Progress / weak topics | FR-031 |
| Intents, suggestions, contact | FR-042–044 |
| Matching privacy controls | NFR-003 (UI) |
| Optional API gateway | Architecture target |

## 5. Phase C — Later

| Capability | FR / UC |
|---|---|
| Private student notes | FR-018 |
| Study plans / agents | FR-032 |
| Graph matching | FR-045 |
| LMS sync | FR-019 |
| Kafka service mesh | FR-051 |
| Multi-uni tenancy | Vision (deferred) |

## 6. Won't (near term)

| Item | Why |
|---|---|
| Student canonical PDF uploads | NG-3 |
| Public material marketplace | NG-2 |
| Full microservices + Kafka before M5 | Execution Plan / ADR-0004 |
| LMS replacement | NG-5 |

## 7. References

- [Vision](VISION.md)
- [Use Cases](USE-CASES.md)
- [Requirements](REQUIREMENTS.md)
- [Execution Plan](EXECUTION-PLAN.md)
