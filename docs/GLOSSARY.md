# Glossary

| Field | Value |
|---|---|
| Document ID | DOC-GLOSS |
| Version | 0.1.0 |
| Status | Draft |
| Last updated | 2026-09-27 |

## Domain

| Term | Definition |
|---|---|
| **Uni Companion** | This product/system |
| **Tenant** | Institutional scope (initially `uni-siegen`) |
| **Course** | Module/class context |
| **Canonical material** | Admin/professor-uploaded PDF that feeds the shared course KB |
| **Processing status** | `UPLOADED` / `PROCESSING` / `READY` / `FAILED` |
| **Visibility** | `UNPUBLISHED` / `PUBLISHED` (admin-controlled) |
| **Grounded answer** | LLM answer constrained by retrieved course chunks |
| **Enrollment** | Student membership granting access to published ready materials |
| **Intent** | Collaboration goal (study group, project, thesis, language) |
| **Course Admin** | Role allowed to manage course materials and visibility for courses they own |
| **Platform Admin (`ADMIN`)** | Role allowed to unpublish any course (and other platform-wide admin actions) |

## Technical

| Term | Definition |
|---|---|
| **RAG** | Retrieval-Augmented Generation |
| **Chunk** | Text segment used for embedding/retrieval |
| **Embedding** | Vector representation of text |
| **pgvector** | PostgreSQL extension for vector similarity |
| **Gemini** | Google generative AI API used in Phase A |
| **JWT** | JSON Web Token used for API auth in Phase A |
| **TDD** | Test-Driven Development (Red → Green → Refactor) |
| **Kafka** | Target async event backbone (deferred in Phase A) |
| **ADR** | Architecture Decision Record |
| **MoSCoW** | Must / Should / Could / Won't |

## Identifiers

| Prefix | Meaning |
|---|---|
| `FR-` / `NFR-` | Requirements |
| `UC-` | Use case |
| `SVC-` | Service |
| `EVT-` | Event |
| `ADR-` | Decision record |
| `DOC-` | Document |
| `T#` | Tech stack decision |
| `W#` / `M#` | Workstream / milestone |
