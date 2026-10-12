import fs from "node:fs";
import path from "node:path";
import { Agent, CursorAgentError } from "@cursor/sdk";
import type { Config } from "./config.js";
import { buildFixerPrompt, loadFixerPromptTemplate } from "./prompts.js";
import type { Bug, BugVerdict, FixResult } from "./types.js";

function extractPrUrl(text: string): string | undefined {
  const match = text.match(/https:\/\/github\.com\/[^\s)\]]+\/pull\/\d+/);
  return match?.[0];
}

export async function runCursorFix(input: {
  config: Config;
  bugs: Bug[];
  verdicts: BugVerdict[];
  loopCount: number;
  resumeAgentId?: string | null;
}): Promise<FixResult> {
  const template = loadFixerPromptTemplate(input.config.promptsDir);
  const prompt = buildFixerPrompt({
    template,
    bugs: input.bugs,
    verdicts: input.verdicts,
    baseUrl: input.config.baseUrl,
    loopCount: input.loopCount,
  });

  if (input.config.dryRun) {
    const promptPath = path.join(
      input.config.stateDir,
      `fixer-prompt-${Date.now()}.md`,
    );
    fs.mkdirSync(input.config.stateDir, { recursive: true });
    fs.writeFileSync(promptPath, prompt, "utf8");
    return {
      dryRun: true,
      status: "dry_run",
      summary: `Dry-run: wrote fixer prompt to ${promptPath}. Set CURSOR_API_KEY to dispatch a cloud agent.`,
      promptPath,
    };
  }

  const apiKey = input.config.cursorApiKey!;
  const model = { id: input.config.cursorModel };

  try {
    if (input.resumeAgentId) {
      await using agent = await Agent.resume(input.resumeAgentId, { apiKey });
      const run = await agent.send(prompt);
      console.error(`[cursor] resumed agent=${agent.agentId} run=${run.id}`);
      const result = await run.wait();
      const summary =
        typeof result.result === "string"
          ? result.result
          : JSON.stringify(result.result ?? result, null, 2);
      if (result.status === "error") {
        return {
          dryRun: false,
          agentId: agent.agentId,
          runId: run.id,
          status: "error",
          summary: summary || `Cursor run ${run.id} failed`,
        };
      }
      return {
        dryRun: false,
        agentId: agent.agentId,
        runId: run.id,
        status: "finished",
        summary,
        prUrl: extractPrUrl(summary),
      };
    }

    await using agent = await Agent.create({
      apiKey,
      model,
      cloud: {
        repos: [{ url: input.config.githubRepo, startingRef: "main" }],
        autoCreatePR: true,
        skipReviewerRequest: true,
      },
    });

    const run = await agent.send(prompt);
    console.error(`[cursor] created agent=${agent.agentId} run=${run.id}`);
    const result = await run.wait();
    const summary =
      typeof result.result === "string"
        ? result.result
        : JSON.stringify(result.result ?? result, null, 2);

    if (result.status === "error") {
      return {
        dryRun: false,
        agentId: agent.agentId,
        runId: run.id,
        status: "error",
        summary: summary || `Cursor run ${run.id} failed`,
      };
    }

    return {
      dryRun: false,
      agentId: agent.agentId,
      runId: run.id,
      status: "finished",
      summary,
      prUrl: extractPrUrl(summary),
    };
  } catch (err) {
    if (err instanceof CursorAgentError) {
      return {
        dryRun: false,
        status: "error",
        summary: `Cursor startup failed: ${err.message} (retryable=${err.isRetryable})`,
      };
    }
    throw err;
  }
}
