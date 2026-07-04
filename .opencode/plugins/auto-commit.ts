import { tool } from "@opencode-ai/plugin/tool";

export default async ({ $ }) => {
  const writeTools = new Set(["edit", "write", "bash"]);

  async function tryCommit(tool: string, scope: string, action: string) {
    const status = await $`git status --porcelain`.nothrow().quiet();
    const out = status.stdout?.toString().trim() || "";
    if (!out) return;
    const fileList = out
      .split("\n")
      .map(l => {
        const raw = l.slice(0, 2);
        const path = l.slice(3);
        const label = raw === "??" ? "new file:" : raw.includes("M") ? "modified:" : raw.includes("A") ? "new file:" : raw.includes("D") ? "deleted:" : "modified:";
        return `      ${label}   ${path}`;
      })
      .join("\n");
    const body = `${tool}(${scope}): ${action}\n\nChanges to be committed:\n${fileList}`;
    await $`git add -A && git commit -m ${body}`.nothrow().quiet();
  }

  function describe(input: any): [string, string, string] {
    const args = input.input || input;
    if (input.tool === "write") {
      const path = args?.filePath || "";
      const name = path.split("/").pop() || "file";
      return ["write", name, "update"];
    }
    if (input.tool === "edit") {
      const path = args?.filePath || "";
      const name = path.split("/").pop() || "file";
      return ["edit", name, "modify"];
    }
    if (input.tool === "bash") {
      const cmd = args?.command || "";
      const short = cmd.length > 60 ? cmd.slice(0, 60) + "…" : cmd;
      return ["bash", short, "run"];
    }
    return [input.tool || "change", "work", "progress"];
  }

  return {
    tool: {
      commit: tool({
        description:
          "Stage all tracked and untracked files via `git add -A` and commit with a descriptive message.",
        args: {
          message: tool.schema
            .string()
            .describe("Commit message"),
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
      const [tool, scope, action] = describe(input);
      await tryCommit(tool, scope, action);
    },
  };
};
