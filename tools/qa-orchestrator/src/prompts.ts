import fs from "node:fs";
import path from "node:path";
import type { Bug, BugVerdict } from "./types.js";

export function loadFixerPromptTemplate(promptsDir: string): string {
  return fs.readFileSync(path.join(promptsDir, "fixer.md"), "utf8");
}

export function buildFixerPrompt(input: {
  template: string;
  bugs: Bug[];
  verdicts: BugVerdict[];
  baseUrl: string;
  loopCount: number;
}): string {
  const failing = input.bugs.filter((b) =>
    input.verdicts.some((v) => v.id === b.id && v.status === "fail"),
  );
  const skipped = input.bugs.filter((b) =>
    input.verdicts.some((v) => v.id === b.id && v.status === "skipped_no_repro"),
  );
  const targets = [...failing, ...skipped];

  const payload = {
    baseUrl: input.baseUrl,
    loopCount: input.loopCount,
    bugs: targets,
    playwrightVerdicts: input.verdicts.filter((v) =>
      targets.some((b) => b.id === v.id),
    ),
  };

  return `${input.template.trim()}

## Bug payload (JSON)

\`\`\`json
${JSON.stringify(payload, null, 2)}
\`\`\`
`;
}
