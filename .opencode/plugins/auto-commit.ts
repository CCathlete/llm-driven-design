import { tool } from "@opencode-ai/plugin/tool";

export default async ({ $ }) => {
  const writeTools = new Set(["edit", "write", "bash"]);

  async function tryCommit(scope: string) {
    const status = await $`git status --porcelain`.nothrow().quiet();
    const out = status.stdout?.toString().trim() || "";
    if (!out) return;
    const msg = `auto(${scope}): progress`;
    await $`git add -A && git commit -m ${msg}`.nothrow().quiet();
  }

  return {
    tool: {
      commit: tool({
        description:
          "Stage all tracked and untracked files via `git add -A` and commit with a descriptive message. Must be called as the final step after implementing all ITR steps.",
        args: {
          message: tool.schema
            .string()
            .describe(
              "Commit message following conventional commits: `impl(scope): description`",
            ),
        },
        async execute({ message }) {
          const result = await $`git add -A && git commit -m ${message}`
            .nothrow()
            .quiet();
          const stdout = result.stdout?.toString() || "";
          const stderr = result.stderr?.toString() || "";
          return {
            title:
              result.exitCode === 0 ? "Committed all changes" : "Commit result",
            output: stdout || stderr,
          };
        },
      }),
    },
    "tool.execute.after": async (input, _output) => {
      if (!writeTools.has(input.tool)) return;
      await tryCommit(input.tool);
    },
  };
};
