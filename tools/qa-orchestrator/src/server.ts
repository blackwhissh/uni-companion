import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { createMcpExpressApp } from "@modelcontextprotocol/sdk/server/express.js";
import type { Request, Response, NextFunction } from "express";
import { z } from "zod";
import type { Config } from "./config.js";
import {
  createOrchestrator,
  fixActive,
  ingestAndAutoFix,
  statusPayload,
  type Orchestrator,
} from "./orchestrator.js";
import { BugReportSchema } from "./types.js";

function textResult(payload: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(payload, null, 2) }],
  };
}

function requireAuth(config: Config) {
  return (req: Request, res: Response, next: NextFunction) => {
    const header = req.header("authorization") ?? "";
    const token = header.toLowerCase().startsWith("bearer ")
      ? header.slice(7).trim()
      : (req.header("x-orch-token") ?? "").trim();
    if (!token || token !== config.orchToken) {
      res.status(401).json({ error: "unauthorized" });
      return;
    }
    next();
  };
}

function buildMcpServer(orch: Orchestrator): McpServer {
  const server = new McpServer({
    name: "uni-companion-qa-orchestrator",
    version: "0.1.0",
  });

  server.registerTool(
    "qa_start",
    {
      description:
        "Start or reset a QA run for the given website URL. Call this before testing.",
      inputSchema: {
        baseUrl: z
          .string()
          .url()
          .optional()
          .describe("Website URL under test (defaults to orchestrator QA_BASE_URL)"),
      },
    },
    async ({ baseUrl }) => {
      const run = orch.store.createRun({
        baseUrl: baseUrl ?? orch.config.baseUrl,
        bugs: [],
      });
      return textResult({
        runId: run.id,
        baseUrl: run.baseUrl,
        message:
          "QA run ready. Test the PUBLIC baseUrl (Grok cannot open localhost). Then call submit_test_result with structured bugs. Bugs wait for approve_fix unless AUTO_FIX=true.",
      });
    },
  );

  server.registerTool(
    "get_test_url",
    {
      description: "Return the URL Grok should open for testing / retesting.",
      inputSchema: {},
    },
    async () => {
      try {
        const run = orch.store.requireActiveRun();
        return textResult({
          url: run.baseUrl,
          runId: run.id,
          phase: run.phase,
          prUrl: run.prUrl,
        });
      } catch {
        return textResult({ url: orch.config.baseUrl, phase: "no_active_run" });
      }
    },
  );

  server.registerTool(
    "qa_status",
    {
      description:
        "Get fixer status: phase, PR URL, Cursor agent id, open bugs, last summary.",
      inputSchema: {},
    },
    async () => {
      try {
        const run = orch.store.requireActiveRun();
        return textResult(statusPayload(run));
      } catch (err) {
        return textResult({
          error: err instanceof Error ? err.message : String(err),
        });
      }
    },
  );

  server.registerTool(
    "submit_test_result",
    {
      description:
        "Submit structured QA bugs. By default this only stores the report (phase awaiting_review). Call approve_fix after a human reviews, unless AUTO_FIX=true. Empty bugs marks the run done.",
      inputSchema: {
        bugs: z
          .array(
            z.object({
              id: z.string(),
              severity: z.enum(["critical", "high", "medium", "low"]),
              title: z.string(),
              steps: z.array(z.string()),
              expected: z.string(),
              actual: z.string(),
              evidence: z.string().optional(),
            }),
          )
          .describe("Bug list. Empty array means all clear / pass."),
        baseUrl: z.string().url().optional(),
      },
    },
    async ({ bugs, baseUrl }) => {
      const parsed = BugReportSchema.parse({ bugs });
      const result = await ingestAndAutoFix(orch, {
        bugs: parsed.bugs,
        baseUrl,
        waitForFix: false,
      });
      return textResult({
        runId: result.run.id,
        phase: result.run.phase,
        fixer: result.fixer,
        dryRun: orch.config.dryRun,
        autoFix: orch.config.autoFix,
        message:
          result.fixer === "started"
            ? "Cursor fixer started in the background. Poll qa_status until phase is awaiting_pull or done, then retest get_test_url."
            : result.fixer === "skipped_done"
              ? "No bugs — run marked done."
              : result.fixer === "skipped_disabled"
                ? "Bugs stored (awaiting_review). Do NOT dispatch Cursor until a human calls approve_fix."
                : `Fixer result: ${result.fixer}`,
      });
    },
  );

  server.registerTool(
    "approve_fix",
    {
      description:
        "Human-approved dispatch: start the Cursor coding agent for the active run's open bugs. Call only after reviewing submit_test_result.",
      inputSchema: {
        confirm: z
          .literal(true)
          .describe("Must be true to dispatch. Prevents accidental fixes."),
      },
    },
    async ({ confirm }) => {
      if (confirm !== true) {
        return textResult({
          error: "Set confirm=true after human review to dispatch Cursor.",
        });
      }
      const { run, fix } = await fixActive(orch);
      return textResult({
        runId: run.id,
        phase: run.phase,
        dryRun: orch.config.dryRun,
        status: fix.status,
        agentId: fix.agentId,
        prUrl: fix.prUrl ?? run.prUrl,
        summary: fix.summary,
        message:
          fix.status === "dry_run"
            ? "Dry-run only (no CURSOR_API_KEY). Prompt written to state/."
            : fix.status === "error"
              ? "Cursor fix failed — see summary."
              : "Cursor fixer finished (or still running if background). Poll qa_status.",
      });
    },
  );

  server.registerTool(
    "request_retest",
    {
      description:
        "After Cursor finishes (phase awaiting_pull / awaiting_retest), get a retest brief for Grok.",
      inputSchema: {},
    },
    async () => {
      try {
        const run = orch.store.requireActiveRun();
        return textResult({
          url: run.baseUrl,
          runId: run.id,
          phase: run.phase,
          prUrl: run.prUrl,
          openBugIds: statusPayload(run).openBugIds,
          developerSummary: run.lastFixSummary,
          instructions: [
            "Open the PUBLIC test URL (not localhost).",
            "Re-check each previously reported bug.",
            "Do a short regression pass.",
            "Call submit_test_result again with remaining bugs (or empty if all fixed).",
            "Do not call approve_fix on false positives.",
          ],
        });
      } catch (err) {
        return textResult({
          error: err instanceof Error ? err.message : String(err),
        });
      }
    },
  );

  return server;
}

export async function startServer(config: Config): Promise<void> {
  const orch = createOrchestrator(config);
  const auth = requireAuth(config);
  // host 0.0.0.0 disables localhost-only Host checks so Cloudflare/ngrok Host headers work.
  // Auth is still enforced via ORCH_MCP_TOKEN on /mcp and /v1/*.
  const app = createMcpExpressApp({ host: "0.0.0.0" });

  app.get("/health", (_req, res) => {
    res.json({
      ok: true,
      autoFix: config.autoFix,
      dryRun: config.dryRun,
      baseUrl: config.baseUrl,
    });
  });

  // REST hooks (same automation as MCP — useful for curl / non-MCP clients)
  app.post("/v1/bugs", auth, async (req, res) => {
    try {
      const body = BugReportSchema.extend({
        baseUrl: z.string().url().optional(),
      }).parse(req.body);
      const result = await ingestAndAutoFix(orch, {
        bugs: body.bugs,
        baseUrl: body.baseUrl,
        waitForFix: false,
      });
      res.status(202).json({
        runId: result.run.id,
        phase: result.run.phase,
        fixer: result.fixer,
        dryRun: config.dryRun,
        autoFix: config.autoFix,
      });
    } catch (err) {
      res.status(400).json({
        error: err instanceof Error ? err.message : String(err),
      });
    }
  });

  app.post("/v1/fix", auth, async (_req, res) => {
    try {
      const { run, fix } = await fixActive(orch);
      res.json({
        runId: run.id,
        phase: run.phase,
        dryRun: config.dryRun,
        status: fix.status,
        agentId: fix.agentId,
        prUrl: fix.prUrl ?? run.prUrl,
        summary: fix.summary,
      });
    } catch (err) {
      res.status(400).json({
        error: err instanceof Error ? err.message : String(err),
      });
    }
  });

  app.get("/v1/status", auth, (_req, res) => {
    try {
      const run = orch.store.requireActiveRun();
      res.json(statusPayload(run));
    } catch (err) {
      res.status(404).json({
        error: err instanceof Error ? err.message : String(err),
      });
    }
  });

  app.get("/v1/test-url", auth, (_req, res) => {
    try {
      const run = orch.store.requireActiveRun();
      res.json({ url: run.baseUrl, runId: run.id, phase: run.phase, prUrl: run.prUrl });
    } catch {
      res.json({ url: config.baseUrl, phase: "no_active_run" });
    }
  });

  app.post("/mcp", auth, async (req, res) => {
    const server = buildMcpServer(orch);
    try {
      const transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: undefined,
      });
      await server.connect(transport);
      await transport.handleRequest(req, res, req.body);
      res.on("close", () => {
        void transport.close();
        void server.close();
      });
    } catch (err) {
      console.error("[mcp] error", err);
      if (!res.headersSent) {
        res.status(500).json({
          jsonrpc: "2.0",
          error: { code: -32603, message: "Internal server error" },
          id: null,
        });
      }
    }
  });

  app.get("/mcp", auth, (_req, res) => {
    res.status(405).json({
      jsonrpc: "2.0",
      error: { code: -32000, message: "Method not allowed." },
      id: null,
    });
  });

  app.delete("/mcp", auth, (_req, res) => {
    res.status(405).json({
      jsonrpc: "2.0",
      error: { code: -32000, message: "Method not allowed." },
      id: null,
    });
  });

  await new Promise<void>((resolve) => {
    app.listen(config.orchPort, "127.0.0.1", () => resolve());
  });

  console.error(`[orchestrator] listening on http://127.0.0.1:${config.orchPort}`);
  console.error(`[orchestrator] MCP endpoint:  POST /mcp`);
  console.error(`[orchestrator] REST submit:   POST /v1/bugs`);
  console.error(`[orchestrator] autoFix=${config.autoFix} dryRun=${config.dryRun}`);
  console.error(`[orchestrator] test URL: ${config.baseUrl}`);
  console.error(
    `[orchestrator] Expose MCP with a tunnel, then connect Grok Bot to https://<tunnel>/mcp with Authorization: Bearer <ORCH_MCP_TOKEN>`,
  );
  if (/localhost|127\.0\.0\.1/.test(config.baseUrl)) {
    console.error(
      `[orchestrator] WARNING: QA_BASE_URL is localhost — Grok Bot cannot open it. Tunnel Vite (port 5173) and set QA_BASE_URL to that https URL, or pass baseUrl to qa_start.`,
    );
  }
}
