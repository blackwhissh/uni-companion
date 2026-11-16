# Start / stop commands

## Product only

Starts Postgres + identity + learning + Vite (each in its own window).

```powershell
cd D:\Java\uni-companion
.\dev.ps1                         # AI = fake
.\dev.ps1 -Ai vertex              # real Vertex Gemini
.\dev.ps1 -Ai vertex -TunnelCors  # also Spring/Vite "qa" profile for public tunnels
```

Stops identity / learning / Vite (+ Postgres unless `-KeepPostgres`). Does **not** stop QA.

```powershell
cd D:\Java\uni-companion
.\dev-stop.ps1
.\dev-stop.ps1 -KeepPostgres      # leave Postgres running
```

| Process | URL |
|---|---|
| Web | http://localhost:5173 |
| identity-service | http://localhost:8081 |
| learning-service | http://localhost:8082 |
| Postgres (pgvector) | localhost:5433 |

## QA only

Product must already be running (usually with `-TunnelCors`). Starts orchestrator + Cloudflare tunnels.

```powershell
cd D:\Java\uni-companion-qa
.\start-qa.ps1 -QaTesters 4
```

Stops orchestrator (8787) + Cloudflare tunnels. Leaves the product running.

```powershell
cd D:\Java\uni-companion-qa
.\stop-qa.ps1
```

## Both

### Start (two terminals)

```powershell
# Terminal A — product (tunnel-ready)
cd D:\Java\uni-companion
.\dev.ps1 -Ai vertex -TunnelCors

# Terminal B — QA orch + tunnels
cd D:\Java\uni-companion-qa
.\start-qa.ps1 -QaTesters 4
```

### Start (one shot from QA repo)

```powershell
cd D:\Java\uni-companion-qa
.\start-qa.ps1 -StartProduct -Ai vertex -QaTesters 4
```

### Stop (QA first, then product)

```powershell
cd D:\Java\uni-companion-qa
.\stop-qa.ps1
cd D:\Java\uni-companion
.\dev-stop.ps1
```

## Grok (MCP tools)

Connect Grok Bot to `https://<tunnel>/mcp` with `Authorization: Bearer <ORCH_MCP_TOKEN>` (from `uni-companion-qa\.env`).

Typical flow:

1. `qa_start` — start/reset run (optional `baseUrl`; must be public HTTPS, not localhost)
2. `get_test_url` — URL Grok should open
3. Test the app, then `submit_test_result` with structured bugs (empty `bugs` = pass / done)
4. **You** review, then approve (see below) — Grok must **not** call `approve_fix`
5. Poll `qa_status` until phase is `awaiting_pull` / `awaiting_retest` / `done`
6. `request_retest` — retest brief; re-check bugs; `submit_test_result` again

| Tool | Who | Notes |
|---|---|---|
| `qa_start` | Grok | Start/reset QA run |
| `get_test_url` | Grok | Public test URL |
| `submit_test_result` | Grok | Store bugs (`awaiting_review` unless `AUTO_FIX=true`) |
| `qa_status` | Grok | Phase, PR, Cursor agent, open bugs |
| `request_retest` | Grok | After Cursor finishes |
| `approve_fix` | **You only** | `confirm: true` — dispatches Cursor Cloud fixer |

### Review / approve (you)

After Grok calls `submit_test_result`:

1. **Browser:** http://127.0.0.1:8787/review?token=\<ORCH_MCP_TOKEN\>
2. **Markdown:** `uni-companion-qa\state\LATEST-REVIEW.md`
3. **CLI** (from `uni-companion-qa`):

```powershell
cd D:\Java\uni-companion-qa
npm run qa -- review    # pretty-print bugs + write LATEST-REVIEW.md
npm run qa -- status    # active run JSON
npm run qa -- fix       # same as approve_fix (confirm)
```
