# ADR-0003: React Vite SPA Frontend

| Field | Value |
|---|---|
| ADR | ADR-0003 |
| Title | React Vite SPA frontend |
| Status | Accepted |
| Date | 2026-09-27 |
| Deciders | Project owner |

## Context

Need a student/admin web client against Spring APIs. Stack preference: React. SSR not required for MVP.

## Decision

Build `apps/web` as a **React Vite SPA** with **Tailwind CSS**, React Router, and TanStack Query. English UI first.

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Next.js | SSR/ecosystem | Unnecessary for Spring API MVP |
| React Vite SPA (chosen) | Simple FE/BE split | Two runtimes locally |
| Server-rendered Java UI | One language | Weaker SPA study UX |

## Consequences

### Positive

- Clear separation from backend  
- Fast UI iteration  

### Negative

- CORS and dual base URLs in Phase A (no gateway yet)  

### Follow-ups

- Gateway later can unify origin  
