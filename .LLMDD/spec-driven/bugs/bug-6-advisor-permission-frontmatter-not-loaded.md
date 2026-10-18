# bug-6: Advisor permission frontmatter is never loaded

- Status: Open
- Created: 2026-10-18
- Resolved: -
- Bug ID: bug-6-advisor-permission-frontmatter-not-loaded

## Bug detail

Symptoms: the Advisor agent declares `permission: edit: deny` plus a
read-only bash allowlist with `"*": ask` in its YAML frontmatter, but the
file is never loaded as an agent definition, so that permission block is
inert. The Advisor's only real runtime restriction is prompt text.

The agent file lives at `.LLMDD/agents/advisor.md`. opencode loads agent
definitions from `.opencode/agents/*.md`. That directory holds only one
real file, `verifier.md`; `advisor.md` is reachable by path only through
the `llmdd` directory symlink, which is not a registered agent path. No
`agents.paths` entry adds `.LLMDD/agents`, and there is no `opencode.json`
at repo root. The second symlink is broken outright.

What makes this hard to see: `advisor.md` is deliberately mirrored as a
hardlink twin, so both `.LLMDD/agents/advisor.md` and
`.opencode/agents/llmdd/advisor.md` resolve to the same inode (75534 as of
2026-10-18) with identical bytes. The mirror exists precisely so the file
*looks* present in an agent directory, and it is exactly one level too
deep - `agents/llmdd/advisor.md` rather than `agents/advisor.md`. So every
path-based check passes while the scanner, which does not recurse into
`llmdd/`, never opens the file. Path reachability and agent discovery are
not the same property, and this bug is the gap between them.

Reproduction (from repo root, read-only):

```sh
ls .opencode/agents/*.md
# EXPECTED: advisor.md listed alongside verifier.md
# ACTUAL: only ./.opencode/agents/verifier.md

cat .opencode/opencode.json
# EXPECTED: an agents/permission declaration wiring .LLMDD/agents in
# ACTUAL: {instructions, skills.paths} only - no agents, no permission block

ls -la .opencode/agents/
# EXPECTED: every symlink resolves
# ACTUAL: llmdd-agents -> ../.LLMDD/agents is BROKEN; from .opencode/agents/
#   that resolves to .opencode/.LLMDD/agents, which does not exist.
#   (correct target is ../../.LLMDD/agents, as the llmdd symlink already uses)

ls opencode.json 2>/dev/null
# ACTUAL: absent at repo root

ls -i .LLMDD/agents/advisor.md .opencode/agents/llmdd/advisor.md
# ACTUAL: same inode (75534) - the hardlink twin exists and is byte-identical,
#   but at agents/llmdd/advisor.md, one directory deeper than the scan path
```

Environment: repo checkout at 4436647 on `llmdd-v9`; identical content on
`origin/main`. opencode v2.0.30, Linux.

Suspected cause: the permission block was authored as a property of the
agent markdown file, but agent discovery is directory-scoped and no
`agents.paths` entry was added when `.LLMDD/agents/` became the canonical
home for the agent files. The `llmdd` symlink keeps the path readable,
which masked the fact that nothing ever parses the frontmatter.

Impact: `edit: deny` does not deny. The write path that bug-5 identified
as re-opened by `bash: allow` is fully open, and the frontmatter reads as
if it were closed. A future maintainer auditing `advisor.md` sees a
deterministic gate and concludes the Advisor cannot write. It can.

Related: bug-5 (the original unenforced-approval report, still Open),
bug-7 (global autoaccept), bug-8 (advisory-only approval gate). All four
are facets of RULE.ADV.APPROVAL_REQUIRED having no enforcement point.

## Why the existing T3 test did not catch this

The T3 proof in `bugfix-feature-5-enforce-advisor-read-only-approval` was
executed by stripping the agent body to a temp copy and confirming that
`edit: deny` still appeared in the frontmatter. That is a test of file
*content*, and it passes regardless of whether any loader reads the file -
circular with respect to the failure mode. A discovery test must assert
the effective permission config (what the runtime resolves after merging
global and project config), not the presence of a permission-looking line
in a markdown file.

## Resolution status

Open. No fix merged. Parked by the Designer on 2026-10-18 - tracked, not
scheduled.

## Fix summary (Resolved only)

Pending.

## Tests proving resolution (Resolved only)

Pending. Suggested: (1) static assertion that `advisor.md` is discoverable
- e.g. `.opencode/agents/*.md` resolves to a real advisor.md path and no
symlink under `.opencode/agents/` is dangling; (2) negative e2e test - an
Advisor session attempting an `edit` call without a token is denied by the
harness (permission error surfaced to the user), not merely by the model
declining; (3) CI lint that fails when a declared agent file is not
reachable from an agent search path.
