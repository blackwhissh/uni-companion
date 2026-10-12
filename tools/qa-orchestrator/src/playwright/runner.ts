import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { chromium, type Page } from "playwright";
import type { Config } from "../config.js";
import type { Bug, BugVerdict } from "../types.js";

export type ReproModule = {
  run: (page: Page, baseUrl: string) => Promise<void>;
};

function resolveReproPath(bug: Bug, config: Config): string | null {
  if (bug.repro) {
    const candidate = path.isAbsolute(bug.repro)
      ? bug.repro
      : path.join(config.packageRoot, bug.repro);
    return fs.existsSync(candidate) ? candidate : null;
  }

  const byId = path.join(config.reprosDir, `${bug.id}.ts`);
  if (fs.existsSync(byId)) return byId;

  const byIdSpec = path.join(config.reprosDir, `${bug.id}.spec.ts`);
  if (fs.existsSync(byIdSpec)) return byIdSpec;

  return null;
}

async function loadRepro(modulePath: string): Promise<ReproModule> {
  const mod = (await import(pathToFileURL(modulePath).href)) as ReproModule;
  if (typeof mod.run !== "function") {
    throw new Error(`Repro ${modulePath} must export async function run(page, baseUrl)`);
  }
  return mod;
}

export async function runRetests(
  bugs: Bug[],
  config: Config,
): Promise<BugVerdict[]> {
  fs.mkdirSync(config.artifactsDir, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 720 },
  });
  const verdicts: BugVerdict[] = [];

  try {
    for (const bug of bugs) {
      const reproPath = resolveReproPath(bug, config);
      if (!reproPath) {
        verdicts.push({
          id: bug.id,
          status: "skipped_no_repro",
          detail: `No Playwright repro at repros/${bug.id}.ts (or bug.repro path)`,
        });
        continue;
      }

      const page = await context.newPage();
      const screenshotPath = path.join(
        config.artifactsDir,
        `${bug.id}-${Date.now()}.png`,
      );

      try {
        const repro = await loadRepro(reproPath);
        await repro.run(page, config.baseUrl);
        verdicts.push({
          id: bug.id,
          status: "pass",
          detail: `Passed via ${path.relative(config.packageRoot, reproPath)}`,
        });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        try {
          await page.screenshot({ path: screenshotPath, fullPage: true });
        } catch {
          // ignore screenshot failures
        }
        verdicts.push({
          id: bug.id,
          status: "fail",
          detail: message,
          screenshotPath: fs.existsSync(screenshotPath) ? screenshotPath : undefined,
        });
      } finally {
        await page.close();
      }
    }
  } finally {
    await context.close();
    await browser.close();
  }

  return verdicts;
}

export function openBugIdsFromVerdicts(verdicts: BugVerdict[]): string[] {
  return verdicts
    .filter((v) => v.status === "fail" || v.status === "skipped_no_repro")
    .map((v) => v.id);
}

export function failingBugIds(verdicts: BugVerdict[]): string[] {
  return verdicts.filter((v) => v.status === "fail").map((v) => v.id);
}
