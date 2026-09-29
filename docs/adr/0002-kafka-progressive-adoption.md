# ADR-0002: Kafka Progressive Adoption

| Field | Value |
|---|---|
| ADR | ADR-0002 |
| Title | Kafka progressive adoption |
| Status | Accepted |
| Date | 2026-09-27 |
| Deciders | Project owner |

## Context

Ingestion should not block upload HTTP. Target architecture uses Kafka for cross-service events. Phase A has only two deployables.

## Decision

1. **Phase A:** async ingestion via Spring jobs / status entities — **no Kafka**.  
2. **Later:** adopt Apache Kafka when services split or fan-out requires it.  
3. Document target topics in [MESSAGING.md](../MESSAGING.md).

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Kafka from day 1 | Matches target early | Ops overhead before need |
| Sync-only ingest | Simple | Timeouts, poor UX |
| Progressive (chosen) | Fast MVP + clear end state | Temporary dual mental model |

## Consequences

### Positive

- Faster Phase A  
- Clear adoption triggers  

### Negative

- Migration effort when introducing Kafka  

### Follow-ups

- First Kafka events: material.uploaded / completed / visibility.changed  
