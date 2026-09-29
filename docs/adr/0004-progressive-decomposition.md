# ADR-0004: Progressive Service Decomposition

| Field | Value |
|---|---|
| ADR | ADR-0004 |
| Title | Progressive service decomposition |
| Status | Accepted |
| Date | 2026-09-27 |
| Deciders | Project owner |

## Context

Target is microservices (ADR-0001), but full mesh before first users delays validation.

## Decision

Phase A ships **`identity-service` + modular `learning-service`**. Preserve internal packages aligned to future services. Split when scaling, ownership, or Kafka fan-out demands it.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Big-bang microservices | Pure early | Slow demo |
| Permanent monolith | Fast | Conflicts with ADR-0001 |
| Progressive (chosen) | Speed + end-state | Discipline required |

## Consequences

### Positive

- Enables 4-week MVP path  
- Docs stay target-aligned  

### Negative

- Risk of “temporary forever” without extraction triggers  

### Follow-ups

- Extraction triggers listed in SERVICES.md  
