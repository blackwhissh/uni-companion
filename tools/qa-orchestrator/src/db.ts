import fs from "node:fs";
import path from "node:path";
import Database from "better-sqlite3";
import type { Bug, BugVerdict, QaRun, RunPhase } from "./types.js";

export class RunStore {
  private readonly db: Database.Database;

  constructor(dbPath: string) {
    fs.mkdirSync(path.dirname(dbPath), { recursive: true });
    this.db = new Database(dbPath);
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS runs (
        id TEXT PRIMARY KEY,
        base_url TEXT NOT NULL,
        phase TEXT NOT NULL,
        loop_count INTEGER NOT NULL DEFAULT 0,
        bugs_json TEXT NOT NULL,
        open_bug_ids_json TEXT NOT NULL,
        last_verdicts_json TEXT,
        last_fix_summary TEXT,
        cursor_agent_id TEXT,
        cursor_run_id TEXT,
        pr_url TEXT,
        error_message TEXT,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS meta (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      );
    `);
  }

  close(): void {
    this.db.close();
  }

  setActiveRunId(runId: string | null): void {
    if (runId === null) {
      this.db.prepare(`DELETE FROM meta WHERE key = 'active_run_id'`).run();
      return;
    }
    this.db
      .prepare(
        `INSERT INTO meta (key, value) VALUES ('active_run_id', ?)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
      )
      .run(runId);
  }

  getActiveRunId(): string | null {
    const row = this.db
      .prepare(`SELECT value FROM meta WHERE key = 'active_run_id'`)
      .get() as { value: string } | undefined;
    return row?.value ?? null;
  }

  createRun(input: { baseUrl: string; bugs: Bug[] }): QaRun {
    const id = `run_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
    const now = new Date().toISOString();
    const openIds = input.bugs.map((b) => b.id);
    this.db
      .prepare(
        `INSERT INTO runs (
          id, base_url, phase, loop_count, bugs_json, open_bug_ids_json,
          created_at, updated_at
        ) VALUES (?, ?, ?, 0, ?, ?, ?, ?)`,
      )
      .run(
        id,
        input.baseUrl,
        "submitted",
        JSON.stringify(input.bugs),
        JSON.stringify(openIds),
        now,
        now,
      );
    this.setActiveRunId(id);
    return this.getRun(id)!;
  }

  getRun(id: string): QaRun | null {
    const row = this.db.prepare(`SELECT * FROM runs WHERE id = ?`).get(id) as
      | Record<string, unknown>
      | undefined;
    return row ? mapRow(row) : null;
  }

  requireActiveRun(): QaRun {
    const id = this.getActiveRunId();
    if (!id) throw new Error("No active run. Submit a bugs.json first.");
    const run = this.getRun(id);
    if (!run) throw new Error(`Active run ${id} not found in database.`);
    return run;
  }

  updateRun(
    id: string,
    patch: Partial<{
      phase: RunPhase;
      loopCount: number;
      baseUrl: string;
      openBugIds: string[];
      lastVerdicts: BugVerdict[];
      lastFixSummary: string | null;
      cursorAgentId: string | null;
      cursorRunId: string | null;
      prUrl: string | null;
      errorMessage: string | null;
    }>,
  ): QaRun {
    const current = this.getRun(id);
    if (!current) throw new Error(`Run ${id} not found`);

    const next = {
      baseUrl: patch.baseUrl ?? current.baseUrl,
      phase: patch.phase ?? current.phase,
      loopCount: patch.loopCount ?? current.loopCount,
      openBugIdsJson:
        patch.openBugIds !== undefined
          ? JSON.stringify(patch.openBugIds)
          : current.openBugIdsJson,
      lastVerdictsJson:
        patch.lastVerdicts !== undefined
          ? JSON.stringify(patch.lastVerdicts)
          : current.lastVerdictsJson,
      lastFixSummary:
        patch.lastFixSummary !== undefined
          ? patch.lastFixSummary
          : current.lastFixSummary,
      cursorAgentId:
        patch.cursorAgentId !== undefined
          ? patch.cursorAgentId
          : current.cursorAgentId,
      cursorRunId:
        patch.cursorRunId !== undefined ? patch.cursorRunId : current.cursorRunId,
      prUrl: patch.prUrl !== undefined ? patch.prUrl : current.prUrl,
      errorMessage:
        patch.errorMessage !== undefined ? patch.errorMessage : current.errorMessage,
      updatedAt: new Date().toISOString(),
    };

    this.db
      .prepare(
        `UPDATE runs SET
          base_url = ?,
          phase = ?,
          loop_count = ?,
          open_bug_ids_json = ?,
          last_verdicts_json = ?,
          last_fix_summary = ?,
          cursor_agent_id = ?,
          cursor_run_id = ?,
          pr_url = ?,
          error_message = ?,
          updated_at = ?
        WHERE id = ?`,
      )
      .run(
        next.baseUrl,
        next.phase,
        next.loopCount,
        next.openBugIdsJson,
        next.lastVerdictsJson,
        next.lastFixSummary,
        next.cursorAgentId,
        next.cursorRunId,
        next.prUrl,
        next.errorMessage,
        next.updatedAt,
        id,
      );

    return this.getRun(id)!;
  }
}

function mapRow(row: Record<string, unknown>): QaRun {
  return {
    id: String(row.id),
    baseUrl: String(row.base_url),
    phase: row.phase as RunPhase,
    loopCount: Number(row.loop_count),
    bugsJson: String(row.bugs_json),
    openBugIdsJson: String(row.open_bug_ids_json),
    lastVerdictsJson: (row.last_verdicts_json as string | null) ?? null,
    lastFixSummary: (row.last_fix_summary as string | null) ?? null,
    cursorAgentId: (row.cursor_agent_id as string | null) ?? null,
    cursorRunId: (row.cursor_run_id as string | null) ?? null,
    prUrl: (row.pr_url as string | null) ?? null,
    errorMessage: (row.error_message as string | null) ?? null,
    createdAt: String(row.created_at),
    updatedAt: String(row.updated_at),
  };
}

export function parseBugs(run: QaRun): Bug[] {
  return JSON.parse(run.bugsJson) as Bug[];
}

export function parseOpenBugIds(run: QaRun): string[] {
  return JSON.parse(run.openBugIdsJson) as string[];
}

export function parseVerdicts(run: QaRun): BugVerdict[] {
  if (!run.lastVerdictsJson) return [];
  return JSON.parse(run.lastVerdictsJson) as BugVerdict[];
}
