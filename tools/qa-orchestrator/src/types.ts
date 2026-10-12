import { z } from "zod";

export const SeveritySchema = z.enum(["critical", "high", "medium", "low"]);

export const BugSchema = z.object({
  id: z.string().min(1),
  severity: SeveritySchema,
  title: z.string().min(1),
  steps: z.array(z.string()).min(1),
  expected: z.string().min(1),
  actual: z.string().min(1),
  evidence: z.string().optional(),
  /** Relative to package root, or absolute path to a .ts repro module. */
  repro: z.string().optional(),
});

export const BugReportSchema = z.object({
  bugs: z.array(BugSchema),
});

export type Bug = z.infer<typeof BugSchema>;
export type BugReport = z.infer<typeof BugReportSchema>;

export const VerdictStatusSchema = z.enum(["pass", "fail", "skipped_no_repro"]);
export type VerdictStatus = z.infer<typeof VerdictStatusSchema>;

export type BugVerdict = {
  id: string;
  status: VerdictStatus;
  detail?: string;
  screenshotPath?: string;
};

export type RunPhase =
  | "submitted"
  | "awaiting_review"
  | "retesting"
  | "fixing"
  | "awaiting_pull"
  | "awaiting_retest"
  | "done"
  | "stopped_max_loops"
  | "error";

export type QaRun = {
  id: string;
  baseUrl: string;
  phase: RunPhase;
  loopCount: number;
  bugsJson: string;
  openBugIdsJson: string;
  lastVerdictsJson: string | null;
  lastFixSummary: string | null;
  cursorAgentId: string | null;
  cursorRunId: string | null;
  prUrl: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
};

export type FixResult = {
  dryRun: boolean;
  agentId?: string;
  runId?: string;
  status: "finished" | "error" | "dry_run";
  summary: string;
  prUrl?: string;
  promptPath?: string;
};
