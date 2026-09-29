# ADR-0005: Google Gemini as Phase A LLM Provider

| Field | Value |
|---|---|
| ADR | ADR-0005 |
| Title | Google Gemini as Phase A LLM provider |
| Status | Accepted |
| Date | 2026-09-27 |
| Deciders | Project owner |

## Context

RAG, flashcards, and quizzes need embeddings and chat. OpenAI API is paid. The project prefers low cost for MVP.

## Decision

Use **Google Gemini API** (free tier first) for embeddings and chat in Phase A. Access only through `EmbeddingModel` / `ChatModel` interfaces so providers can be swapped.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| OpenAI | Strong DX | Paid API |
| Azure OpenAI | Enterprise options | Paid / setup |
| Ollama local | Free, private | Hardware-dependent quality/ops |
| Gemini (chosen) | Free tier suited to MVP | Rate limits; vendor lock if not abstracted |

## Consequences

### Positive

- Low cash cost for pilot  
- Enough capability for grounded Q&A demos  

### Negative

- Free-tier quotas / 429s  
- Must monitor ToS / data handling  

### Follow-ups

- `.env.example` with `GEMINI_API_KEY`  
- Graceful rate-limit handling  
- Re-evaluate before production scale  
