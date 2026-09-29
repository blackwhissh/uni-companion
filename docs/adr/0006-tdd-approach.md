# ADR-0006: Test-Driven Development Approach

| Field | Value |
|---|---|
| ADR | ADR-0006 |
| Title | Test-Driven Development approach |
| Status | Accepted |
| Date | 2026-09-27 |
| Deciders | Project owner |

## Context

Uni Companion has critical access rules (enrollment, publish/unpublish, admin-only uploads) and AI-adjacent flows that are easy to break accidentally. The team wants quality without late test-only phases.

## Decision

Adopt **TDD (Red → Green → Refactor)** as the default approach for:

- Domain policies and authorization rules  
- REST API acceptance behavior for Phase A use cases  

Use fakes for Gemini in automated tests. Keep a thin manual/E2E layer for S-0/S-1 demos.

Full practice documented in [TESTING.md](../TESTING.md).

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Tests after feature complete | Faster perceived coding early | Regressions; weak authz coverage |
| TDD for everything including CSS | High discipline | Slow UI experimentation |
| TDD for domain/API (chosen) | Protects core rules; pragmatic UI | Requires habit and fixtures |

## Consequences

### Positive

- Safer publish/unpublish and RAG gating  
- Executable specification alongside use cases  
- Easier refactors when splitting `learning-service`  

### Negative

- Slightly slower first slice per feature  
- Need reliable test doubles for LLM/DB  

### Follow-ups

- Scaffold surefire/failsafe and Vitest in W1  
- Add NFR-060/061 to requirements  
- Make `mvn test` / `npm test` part of Definition of Done  
