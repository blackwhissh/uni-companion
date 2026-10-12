import fs from "node:fs";
import path from "node:path";
import type { Config } from "./config.js";
import { runCursorFix } from "./cursor.js";
import {
  parseBugs,
  parseOpenBugIds,
  parseVerdicts,
  RunStore,
} from "./db.js";
import {
  failingBugIds,
  openBugIdsFromVerdicts,
  runRetests,
} from "./playwright/runner.js";
import { BugReportSchema, type Bug, type BugVerdict, type QaRun } from "./types.js";

export type Orchestrator = {
  store: RunStore;
  config: Config;
};

export function createOrchestrator(config: Config): Orchestrator {
  fs.mkdirSync(config.stateDir, { recursive: true });
  fs.mkdirSync(config.artifactsDir, { recursive: true });
  return { store: new RunStore(config.dbPath), config };
}

export function submitBugs(
  orch: Orchestrator,
  bugs: Bug[],
  baseUrl?: string,
): QaRun {
  return orch.store.createRun({
    baseUrl: baseUrl ?? orch.config.baseUrl,
    bugs,
  });
}

export function submitReport(
  orch: Orchestrator,
  reportPath: string,
  baseUrl?: string,
): QaRun {
  const raw = JSON.parse(fs.readFileSync(reportPath, "utf8"));
  const report = BugReportSchema.parse(raw);
  return submitBugs(orch, report.bugs, baseUrl);
}

/** Ingest Grok/QA bugs and immediately start Cursor (unless autoFix is off or no bugs). */
export async function ingestAndAutoFix(
  orch: Orchestrator,
  input: { bugs: Bug[]; baseUrl?: string; waitForFix?: boolean },
): Promise<{
  run: QaRun;
  fixer: "started" | "skipped_done" | "skipped_disabled" | "completed" | "error";
  fix?: Awaited<ReturnType<typeof runCursorFix>>;
}> {
  const report = BugReportSchema.parse({ bugs: input.bugs });
  let run = submitBugs(orch, report.bugs, input.baseUrl);

  if (report.bugs.length === 0) {
    run = orch.store.updateRun(run.id, { phase: "done", openBugIds: [] });
    return { run, fixer: "skipped_done" };
  }

  if (!orch.config.autoFix) {
    run = orch.store.updateRun(run.id, { phase: "awaiting_review" });
    return { run, fixer: "skipped_disabled" };
  }

  if (input.waitForFix) {
    const { run: after, fix } = await fixActive(orch);
    return {
      run: after,
      fixer: fix.status === "error" ? "error" : "completed",
      fix,
    };
  }

  run = orch.store.updateRun(run.id, { phase: "fixing" });

  // Background: Grok should not wait for a long Cursor cloud run.
  void fixActive(orch)
    .then(({ run: after, fix }) => {
      console.error(
        `[orchestrator] auto-fix finished run=${after.id} phase=${after.phase} status=${fix.status}`,
      );
      if (after.prUrl) console.error(`[orchestrator] PR: ${after.prUrl}`);
    })
    .catch((err) => {
      console.error(
        `[orchestrator] auto-fix crashed:`,
        err instanceof Error ? err.message : err,
      );
      try {
        orch.store.updateRun(run.id, {
          phase: "error",
          errorMessage: err instanceof Error ? err.message : String(err),
        });
      } catch {
        // ignore
      }
    });

  return { run, fixer: "started" };
}

export function statusPayload(run: QaRun) {
  return {
    id: run.id,
    phase: run.phase,
    baseUrl: run.baseUrl,
    loopCount: run.loopCount,
    openBugIds: parseOpenBugIds(run),
    cursorAgentId: run.cursorAgentId,
    cursorRunId: run.cursorRunId,
    prUrl: run.prUrl,
    lastVerdicts: parseVerdicts(run),
    lastFixSummary: run.lastFixSummary,
    errorMessage: run.errorMessage,
    updatedAt: run.updatedAt,
  };
}

export async function retestActive(orch: Orchestrator): Promise<{
  run: QaRun;
  verdicts: BugVerdict[];
}> {
  const run = orch.store.requireActiveRun();
  const bugs = bugsForRetest(run);
  orch.store.updateRun(run.id, { phase: "retesting" });

  const config = { ...orch.config, baseUrl: run.baseUrl };
  const verdicts = await runRetests(bugs, config);
  const openIds = openBugIdsFromVerdicts(verdicts);

  const allPassed =
    verdicts.length > 0 &&
    verdicts.every((v) => v.status === "pass") &&
    openIds.length === 0;

  // skipped_no_repro keeps the run open for Cursor, but cannot prove DONE alone
  const onlySkippedOrPass =
    openIds.length > 0 && failingBugIds(verdicts).length === 0;
  const hasFail = failingBugIds(verdicts).length > 0;

  let phase: QaRun["phase"] = "awaiting_retest";
  if (allPassed) {
    phase = "done";
  } else if (hasFail || onlySkippedOrPass) {
    phase = "awaiting_retest";
  }

  const updated = orch.store.updateRun(run.id, {
    phase,
    openBugIds: openIds.length > 0 ? openIds : [],
    lastVerdicts: verdicts,
    errorMessage: null,
  });

  return { run: updated, verdicts };
}

export async function fixActive(orch: Orchestrator): Promise<{
  run: QaRun;
  fix: Awaited<ReturnType<typeof runCursorFix>>;
}> {
  const run = orch.store.requireActiveRun();
  const bugs = parseBugs(run);
  const verdicts = parseVerdicts(run);
  const openIds = parseOpenBugIds(run);

  const targetBugs =
    openIds.length > 0 ? bugs.filter((b) => openIds.includes(b.id)) : bugs;

  if (targetBugs.length === 0) {
    const updated = orch.store.updateRun(run.id, { phase: "done" });
    return {
      run: updated,
      fix: {
        dryRun: true,
        status: "dry_run",
        summary: "No open bugs to fix.",
      },
    };
  }

  // If we have never retested, invent skipped/fail placeholders so Cursor still gets context
  const effectiveVerdicts =
    verdicts.length > 0
      ? verdicts
      : targetBugs.map(
          (b): BugVerdict => ({
            id: b.id,
            status: "fail",
            detail: "No Playwright retest yet; treating as open from submitted report.",
          }),
        );

  orch.store.updateRun(run.id, {
    phase: "fixing",
    loopCount: run.loopCount + 1,
  });

  const fix = await runCursorFix({
    config: orch.config,
    bugs: targetBugs,
    verdicts: effectiveVerdicts,
    loopCount: run.loopCount + 1,
    resumeAgentId: run.cursorAgentId,
  });

  if (fix.status === "error") {
    const updated = orch.store.updateRun(run.id, {
      phase: "error",
      lastFixSummary: fix.summary,
      cursorAgentId: fix.agentId ?? run.cursorAgentId,
      cursorRunId: fix.runId ?? run.cursorRunId,
      errorMessage: fix.summary,
    });
    return { run: updated, fix };
  }

  const updated = orch.store.updateRun(run.id, {
    phase: "awaiting_pull",
    lastFixSummary: fix.summary,
    cursorAgentId: fix.agentId ?? run.cursorAgentId,
    cursorRunId: fix.runId ?? run.cursorRunId,
    prUrl: fix.prUrl ?? run.prUrl,
    errorMessage: null,
  });

  return { run: updated, fix };
}

export async function runLoop(
  orch: Orchestrator,
  reportPath: string,
  options: { skipPullPrompt?: boolean; baseUrl?: string } = {},
): Promise<QaRun> {
  let run = submitReport(orch, reportPath, options.baseUrl);

  for (;;) {
    const { run: afterRetest, verdicts } = await retestActive(orch);
    run = afterRetest;

    const fails = failingBugIds(verdicts);
    const open = openBugIdsFromVerdicts(verdicts);

    if (run.phase === "done" || (fails.length === 0 && open.length === 0)) {
      return orch.store.updateRun(run.id, { phase: "done", openBugIds: [] });
    }

    // First pass: skipped_no_repro only → still send to Cursor once
    if (fails.length === 0 && open.length > 0 && run.loopCount >= 1) {
      // After at least one fix loop, cannot verify skipped bugs → stop rather than spin
      return orch.store.updateRun(run.id, {
        phase: "stopped_max_loops",
        errorMessage:
          "Remaining bugs have no Playwright repro; add repros under src/playwright/repros/ to auto-verify.",
      });
    }

    if (run.loopCount >= orch.config.maxLoops) {
      return orch.store.updateRun(run.id, { phase: "stopped_max_loops" });
    }

    const { run: afterFix, fix } = await fixActive(orch);
    run = afterFix;

    if (fix.status === "error") {
      return run;
    }

    if (!options.skipPullPrompt) {
      console.error(
        "\n[orchestrator] Cursor finished. Pull/merge the PR (or restart local app), then press Enter to retest…",
      );
      if (run.prUrl) console.error(`[orchestrator] PR: ${run.prUrl}`);
      await waitForEnter();
    }

    orch.store.updateRun(run.id, { phase: "awaiting_retest" });
  }
}

function bugsForRetest(run: QaRun): Bug[] {
  const bugs = parseBugs(run);
  const openIds = parseOpenBugIds(run);
  if (openIds.length === 0) return bugs;
  const filtered = bugs.filter((b) => openIds.includes(b.id));
  return filtered.length > 0 ? filtered : bugs;
}

function waitForEnter(): Promise<void> {
  return new Promise((resolve) => {
    process.stdin.resume();
    process.stdin.once("data", () => {
      process.stdin.pause();
      resolve();
    });
  });
}

export function formatStatus(run: QaRun): string {
  return JSON.stringify(statusPayload(run), null, 2);
}

export function resolveReportPath(packageRoot: string, input: string): string {
  if (path.isAbsolute(input)) return input;
  const fromCwd = path.resolve(process.cwd(), input);
  if (fs.existsSync(fromCwd)) return fromCwd;
  const fromPkg = path.resolve(packageRoot, input);
  if (fs.existsSync(fromPkg)) return fromPkg;
  throw new Error(`Bug report not found: ${input}`);
}
