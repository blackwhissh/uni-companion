# QA Orchestrator

Handoff with a **review gate** (recommended):

```text
Grok Bot tests a PUBLIC site URL
    → submit_test_result  (stores bugs, phase awaiting_review)
    → you review
    → approve_fix        (starts Cursor Cloud)
    → Grok polls qa_status / request_retest
    → Grok retests
```

Set `AUTO_FIX=true` only if you want to skip the review step.

## Setup

```powershell
cd tools/qa-orchestrator
npm install
npx playwright install chromium
copy .env.example .env
```

In `.env` set:

- `CURSOR_API_KEY`
- `ORCH_MCP_TOKEN`
- `QA_BASE_URL` — **must be reachable by Grok** (not `localhost`)
- `AUTO_FIX=false` (recommended)

## Public test URL (required for Grok)

Grok Bot runs in the cloud. It cannot open `http://localhost:5173`.

1. Start Uni Companion (`.\dev.ps1` from repo root).
2. Tunnel the web app:

```powershell
cloudflared tunnel --url http://127.0.0.1:5173
```

3. Put that `https://….trycloudflare.com` into `QA_BASE_URL`, **or** tell Grok:

> Call qa_start with baseUrl https://….trycloudflare.com

Keep a **separate** tunnel for the orchestrator MCP on port `8787`.

## Daily use

```powershell
npm run qa -- serve
# other terminal: cloudflared tunnel --url http://127.0.0.1:8787
```

### MCP tools

| Tool | What it does |
|---|---|
| `qa_start` | Start/reset run; set public `baseUrl` |
| `get_test_url` | URL Grok should open |
| `submit_test_result` | Store bugs (or mark done if empty) |
| `approve_fix` | **Human gate** — start Cursor (`confirm: true`) |
| `qa_status` | Phase / PR / summary |
| `request_retest` | Retest brief after a fix |

### REST

- `POST /v1/bugs` — submit report
- `POST /v1/fix` — approve/dispatch Cursor
- `GET /v1/status`, `GET /v1/test-url`

## Optional CLI

`submit`, `retest`, `fix`, `status`, `run` still work for local debugging.
