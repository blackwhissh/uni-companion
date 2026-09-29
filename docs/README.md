# Documentation Portal

| Field | Value |
|---|---|
| Document ID | DOC-PORTAL |
| Version | 1.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |

## 1. Purpose

Entry point for Uni Companion documentation.

## 2. Reading order

1. [Vision](VISION.md)  
2. [Use Cases](USE-CASES.md)  
3. [Requirements](REQUIREMENTS.md)  
4. [Roadmap](ROADMAP.md)  
5. [Execution Plan](EXECUTION-PLAN.md) ← **build from here**  
6. [Testing / TDD](TESTING.md)  
7. [Architecture](ARCHITECTURE.md) → [Services](SERVICES.md) / [Frontend](FRONTEND.md) / [Backend](BACKEND.md) / [Messaging](MESSAGING.md)  
8. [Security](SECURITY.md)  
9. [ADRs](adr/README.md)  
10. [Glossary](GLOSSARY.md)  

## 3. Document index

| ID | Title | Path |
|---|---|---|
| DOC-VISION | Product Vision | [VISION.md](VISION.md) |
| DOC-UC | Functionalities and Use Cases | [USE-CASES.md](USE-CASES.md) |
| DOC-REQ | Requirements Specification | [REQUIREMENTS.md](REQUIREMENTS.md) |
| DOC-ROAD | Product Roadmap | [ROADMAP.md](ROADMAP.md) |
| DOC-EXEC | Execution Plan | [EXECUTION-PLAN.md](EXECUTION-PLAN.md) |
| DOC-TEST | Testing and TDD | [TESTING.md](TESTING.md) |
| DOC-ARCH | System Architecture | [ARCHITECTURE.md](ARCHITECTURE.md) |
| DOC-SVC | Service Catalog | [SERVICES.md](SERVICES.md) |
| DOC-FE | Frontend Specification | [FRONTEND.md](FRONTEND.md) |
| DOC-BE | Backend Specification | [BACKEND.md](BACKEND.md) |
| DOC-MSG | Messaging Specification | [MESSAGING.md](MESSAGING.md) |
| DOC-SEC | Security and Privacy | [SECURITY.md](SECURITY.md) |
| DOC-GLOSS | Glossary | [GLOSSARY.md](GLOSSARY.md) |
| DOC-ADR | Architecture Decision Records | [adr/README.md](adr/README.md) |

## 4. Document control

Every document includes: Document ID, Version, Status (`Draft` \| `In Review` \| `Approved` \| `Deprecated`), Last updated.

Material design changes that affect architecture should add or update an ADR.

## 5. Identifier prefixes

| Prefix | Meaning |
|---|---|
| `FR-` / `NFR-` | Requirements |
| `UC-` | Use case |
| `SVC-` | Service |
| `EVT-` | Event |
| `ADR-` | Decision |
| `T#` / `W#` / `M#` | Stack / workstream / milestone |
| Phase A/B/C | MVP / v1 / Later |

## 6. Documentation freeze (Phase A)

Before scaffolding code, treat as baseline:

- Use cases (admin materials + publish/unpublish)  
- Locked stack in Execution Plan  
- TDD approach ([TESTING.md](TESTING.md), ADR-0006)  
- ADRs 0001–0006  

Mark documents **Approved** after your review pass.
