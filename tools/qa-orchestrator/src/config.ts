import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const envPath = path.join(packageRoot, ".env");
dotenv.config({ path: envPath, override: true });

function boolEnv(name: string, fallback: boolean): boolean {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return fallback;
  return ["1", "true", "yes", "on"].includes(raw.toLowerCase());
}

function intEnv(name: string, fallback: number): number {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return fallback;
  const n = Number.parseInt(raw, 10);
  return Number.isFinite(n) ? n : fallback;
}

function resolveBaseUrl(): string {
  // Prefer QA_BASE_URL so a global BASE_URL (e.g. LLM API) cannot leak in.
  const fromQa = process.env.QA_BASE_URL?.trim();
  if (fromQa) return fromQa;
  const fromEnvFile = process.env.BASE_URL?.trim();
  // Only honor bare BASE_URL when it comes from this package's .env (override load),
  // or looks like a local/dev web origin — never an */v1 API root.
  if (fromEnvFile && !/\/v1\/?$/i.test(fromEnvFile) && !/api\./i.test(fromEnvFile)) {
    return fromEnvFile;
  }
  return "http://localhost:5173";
}

export type Config = {
  packageRoot: string;
  cursorApiKey: string | undefined;
  baseUrl: string;
  githubRepo: string;
  maxLoops: number;
  cursorModel: string;
  dryRun: boolean;
  dbPath: string;
  artifactsDir: string;
  promptsDir: string;
  reprosDir: string;
  stateDir: string;
  orchToken: string;
  orchPort: number;
  autoFix: boolean;
};

export function loadConfig(overrides: Partial<Config> = {}): Config {
  const dryRunForced = boolEnv("DRY_RUN", false);
  const cursorApiKey = process.env.CURSOR_API_KEY?.trim() || undefined;
  const stateDir = path.join(packageRoot, "state");

  return {
    packageRoot,
    cursorApiKey,
    baseUrl: resolveBaseUrl(),
    githubRepo:
      process.env.GITHUB_REPO?.trim() || "https://github.com/blackwhissh/uni-companion",
    maxLoops: intEnv("MAX_LOOPS", 3),
    cursorModel: process.env.CURSOR_MODEL?.trim() || "composer-2.5",
    dryRun: dryRunForced || !cursorApiKey,
    dbPath: path.join(stateDir, "runs.db"),
    artifactsDir: path.join(stateDir, "artifacts"),
    promptsDir: path.join(packageRoot, "prompts"),
    reprosDir: path.join(packageRoot, "src", "playwright", "repros"),
    stateDir,
    orchToken: process.env.ORCH_MCP_TOKEN?.trim() || "dev-token-change-me",
    orchPort: intEnv("ORCH_MCP_PORT", 8787),
    autoFix: boolEnv("AUTO_FIX", false),
    ...overrides,
  };
}
