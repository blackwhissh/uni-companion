# ADR-0007: Page chunks and the support cutoff

| Field | Value |
|---|---|
| ADR | ADR-0007 |
| Title | Page chunks and the support cutoff |
| Status | Accepted |
| Date | 2026-11-03 |
| Deciders | Nikoloz Kiladze |

## Context

Students must see the page an answer came from. The fake embedding is a hash, not a meaning model: its cosine score makes unrelated pages look close because every dimension adds the index. A nearest-neighbour list alone will quote the wrong page.

## Decision

1. **One chunk per PDF page.** PDFBox reads each page’s text. Empty pages are dropped. A page is not split. The page number is the citation a student can check in the PDF. A slide is one idea; cutting it would cite a fragment that lost its heading.
2. **A page may support an answer only when it shares the question’s words.** At least one distinctive term (length 4 or more, not a generic word such as “topic”), or a number, or two shared terms. Otherwise the answer is a refusal and the chat model is not given that page.
3. **Cosine distance is an extra gate only for Gemini embeddings.** `c.embedding <=> query` is cosine distance (0 is identical, 2 is opposite). Vertex uses a maximum distance of **0.55** (cosine similarity at least 0.45). Below that, neighbours of short lecture pages are usually a different topic. The fake profile leaves the cutoff at **0** so the hash embedding cannot delete a page that the word gate would keep.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Fixed-size windows (512 tokens, overlap) | Familiar RAG default | Breaks the page citation; a window can start mid-sentence |
| Cosine cutoff on every profile | One rule | The fake hash embedding would refuse real questions or keep the wrong page |
| No cutoff, trust the model | Fewer refusals | The model quotes the nearest wrong page (the sharding question against a consensus slide) |

## Consequences

### Positive

- A wrong nearest page is refused instead of paraphrased.
- The citation is a page the student can open.

### Negative

- A question that uses none of the page’s words is refused even when a human could infer the answer.
- A very long page is one embedding, so a single aside can dominate the vector.

### Follow-ups

- If a lecture PDF is a long script rather than slides, revisit splitting only at headings, still keeping the page number.
