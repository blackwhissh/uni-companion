# ADR-0001: Microservices as Target Architecture

| Field | Value |
|---|---|
| ADR | ADR-0001 |
| Title | Microservices as target architecture |
| Status | Accepted |
| Date | 2026-09-27 |
| Deciders | Project owner |

## Context

Uni Companion combines interactive study (RAG), asynchronous ingestion, and matching. These differ in scaling and ownership. The project also aims at CISS-relevant system decomposition.

## Decision

Adopt **microservices** as the **target** architecture (identity, course, ingestion, rag, study, matching, later gateway/notifications), each owning its data.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Permanent modular monolith | Simpler ops | Weaker isolation / thesis fit |
| Microservices target (chosen) | Clear boundaries, scale paths | Higher complexity if done day one |
| Serverless-only | Elastic | Weaker fit for chosen Java domain style |

## Consequences

### Positive

- Clear service catalog and extraction path  
- Aligns with future Kafka integration  

### Negative

- Must not over-split before MVP (see ADR-0004)  

### Follow-ups

- Maintain package boundaries inside `learning-service`  
