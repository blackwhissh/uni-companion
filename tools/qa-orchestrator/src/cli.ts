#!/usr/bin/env node
import { loadConfig } from "./config.js";
import {
  createOrchestrator,
  fixActive,
  formatStatus,
  ingestAndAutoFix,
  resolveReportPath,
  retestActive,
  runLoop,
  submitReport,
} from "./orchestrator.js";
import { BugReportSchema } from "./types.js";
import fs from "node:fs";

function usage(): never {
  console.error(`Usage:
  npm run qa -- serve
  npm run qa -- submit <bugs.json> [baseUrl]
  npm run qa -- retest [baseUrl]
  npm run qa -- fix
  npm run qa -- status
  npm run qa -- run <bugs.json> [baseUrl] [--yes]

  # Full flags (npm on Windows may strip --flags): npx tsx src/cli.ts …

Commands:
  serve    Start MCP + REST server (Grok submits bugs → Cursor auto-starts)
  submit   Store a bug report and set it as the active run
  retest   Run Playwright repros for open bugs
  fix      Dispatch (or dry-run) a Cursor Cloud fix for open bugs
  status   Print active run JSON
  run      submit → retest → fix → wait for Enter → retest … until done/max loops

Options:
  [baseUrl]        Optional app URL positional (also QA_BASE_URL / package .env)
  --base-url=URL   When invoking via npx tsx
  -b URL           Same
  --yes / -y       In run mode, do not wait for Enter between fix and retest
`);
  process.exit(64);
}

function parseArgs(argv: string[]) {
  const flags = new Map<string, string | boolean>();
  const positionals: string[] = [];

  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]!;
    if (a === "--yes" || a === "-y") {
      flags.set("yes", true);
      continue;
    }
    if (a === "--base-url" || a === "-b") {
      const v = argv[++i];
      if (!v) throw new Error(`${a} requires a value`);
      flags.set("base-url", v);
      continue;
    }
    if (a.startsWith("--base-url=")) {
      flags.set("base-url", a.slice("--base-url=".length));
      continue;
    }
    if (a.startsWith("--")) {
      throw new Error(`Unknown flag: ${a}`);
    }
    positionals.push(a);
  }

  return { flags, positionals };
}

function looksLikeUrl(value: string | undefined): value is string {
  return Boolean(value && /^https?:\/\//i.test(value));
}

async function main(): Promise<void> {
  const argv = process.argv.slice(2);
  if (argv.length === 0 || argv[0] === "-h" || argv[0] === "--help") {
    usage();
  }

  const command = argv[0]!;
  const { flags, positionals } = parseArgs(argv.slice(1));
  const baseUrlOverride =
    (typeof flags.get("base-url") === "string"
      ? String(flags.get("base-url"))
      : undefined) ??
    (command === "retest" && looksLikeUrl(positionals[0])
      ? positionals[0]
      : undefined) ??
    (looksLikeUrl(positionals[1]) ? positionals[1] : undefined);

  const config = loadConfig(
    baseUrlOverride ? { baseUrl: baseUrlOverride } : {},
  );
  const orch = createOrchestrator(config);

  try {
    switch (command) {
      case "serve": {
        const { startServer } = await import("./server.js");
        await startServer(config);
        // never resolves; keep process alive
        await new Promise(() => {});
        break;
      }
      case "submit": {
        const file = positionals[0];
        if (!file || looksLikeUrl(file)) usage();
        const reportPath = resolveReportPath(config.packageRoot, file);
        if (config.autoFix) {
          const report = BugReportSchema.parse(
            JSON.parse(fs.readFileSync(reportPath, "utf8")),
          );
          const result = await ingestAndAutoFix(orch, {
            bugs: report.bugs,
            baseUrl: baseUrlOverride,
            waitForFix: false,
          });
          console.log(
            JSON.stringify(
              {
                id: result.run.id,
                phase: result.run.phase,
                baseUrl: result.run.baseUrl,
                fixer: result.fixer,
                dryRun: config.dryRun,
              },
              null,
              2,
            ),
          );
        } else {
          const run = submitReport(orch, reportPath, baseUrlOverride);
          console.log(
            JSON.stringify(
              {
                id: run.id,
                phase: run.phase,
                baseUrl: run.baseUrl,
                dryRun: config.dryRun,
              },
              null,
              2,
            ),
          );
        }
        break;
      }
      case "retest": {
        if (baseUrlOverride) {
          const active = orch.store.requireActiveRun();
          orch.store.updateRun(active.id, { baseUrl: baseUrlOverride });
        }
        const { run, verdicts } = await retestActive(orch);
        console.log(
          JSON.stringify({ runId: run.id, phase: run.phase, verdicts }, null, 2),
        );
        break;
      }
      case "fix": {
        const { run, fix } = await fixActive(orch);
        console.log(
          JSON.stringify(
            {
              runId: run.id,
              phase: run.phase,
              loopCount: run.loopCount,
              dryRun: fix.dryRun,
              status: fix.status,
              agentId: fix.agentId,
              runCursorId: fix.runId,
              prUrl: fix.prUrl ?? run.prUrl,
              promptPath: fix.promptPath,
              summary: fix.summary,
            },
            null,
            2,
          ),
        );
        if (fix.status === "error") process.exitCode = 2;
        break;
      }
      case "status": {
        const run = orch.store.requireActiveRun();
        console.log(formatStatus(run));
        break;
      }
      case "run": {
        const file = positionals[0];
        if (!file || looksLikeUrl(file)) usage();
        const reportPath = resolveReportPath(config.packageRoot, file);
        const run = await runLoop(orch, reportPath, {
          skipPullPrompt: Boolean(flags.get("yes")),
          baseUrl: baseUrlOverride,
        });
        console.log(formatStatus(run));
        if (run.phase === "error") process.exitCode = 2;
        else if (run.phase === "stopped_max_loops") process.exitCode = 3;
        break;
      }
      case "discover": {
        console.error(
          "discover is reserved for a future cheap LLM+Playwright explorer. For v1, produce bugs.json manually or from Grok Bot, then: npm run qa -- submit <file>",
        );
        process.exitCode = 1;
        break;
      }
      default:
        usage();
    }
  } finally {
    orch.store.close();
  }
}

main().catch((err) => {
  console.error(err instanceof Error ? err.message : err);
  process.exit(1);
});
