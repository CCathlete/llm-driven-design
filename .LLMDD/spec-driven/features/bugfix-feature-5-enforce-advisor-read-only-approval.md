# bugfix-feature-5: Enforce advisor read-only approval

- Status: Proposed
- Created: 2026-10-16
- Feature ID: bugfix-feature-5-enforce-advisor-read-only-approval

## Synopsis

Motivation: bug-5 (`bug-5-advisor-writes-without-approval`) — the Advisor
role is specified as analyze-only / never writes application code, but the
constraint lives only in prompt text plus `edit: deny`, while frontmatter
still grants blanket `bash: allow`. Bash is a write-capable channel, so an
Advisor session can perform code changes without explicit, specific
Designer approval, silently breaking the triad guarantee that design
sessions are side-effect-free until the Designer approves CU content. This
feature adds deterministic enforcement: least-privilege Advisor
capabilities by default plus a machine-checkable approval grant for the
exceptional write case. It serves the Designer (side-effect-free design
sessions) and downstream Coder/Verifier review (no untraced writes).

Scope: In: `.opencode/agents/llmdd/advisor.md` permission frontmatter
(read-only bash allowlist following the `coder.md` pattern, default
deny/ask), the approval-grant mechanism (explicit + specific Designer
sign-off, machine-checkable), and the matching `RULE.ADV.*` / system-tensor
wording in `.LLMDD/prompts/llm-driven-design-sys-prompt.itr.md` where the
enforcement semantics change. In: static audit + negative e2e proving the
gate. Out: `coder.md` / `verifier.md` grants, `dtr-builder`,
`itr-compiler`, `run-waves`, skill-doc rewrites except where the approval
mechanism itself is documented. Fixes `bug-5-advisor-writes-without-approval`.

## Implementation status

Proposed. No CUs assigned, no fix merged. Intended to close
`bug-5-advisor-writes-without-approval` once waves are done and E2E verify
is green. ITR directory
`.LLMDD/ITRS/bugfix-feature-5-enforce-advisor-read-only-approval/`.

## Acceptance criteria

1. Default Advisor session has no write-capable grant: `edit: deny` holds
   and blanket `bash: allow` is gone — bash is a read-only allowlist
   (inspection commands only) with a default deny/ask wildcard for
   everything else.
2. Any Advisor write path requires an explicit, specific Designer approval
   grant that is machine-checkable (approval scope recorded, not a bare
   prompt instruction); without it the write is denied deterministically.
3. The enforcement is capability-level, not prompt-only: removing or
   ignoring the advisory prose still leaves writes blocked without
   approval.
4. Read-only Advisor workflow is preserved: DTR analysis, repo inspection,
   and CU content drafting work with no approval grant.

## Tests

- `T1` (criterion 1, bug-5 repro): parse
  `.opencode/agents/llmdd/advisor.md` frontmatter; assert `edit: deny`,
  assert no blanket `bash: allow`, assert bash allowlist contains only
  read-only commands (e.g. `ls`, `cat`, `rg`) and a trailing `"*": ask` or
  `"*": deny` default. Fails on the current file (blanket allow present).
- `T2` (criterion 2): `grep -rn -i "approval" .opencode/agents/llmdd/advisor.md
  .LLMDD/prompts/llm-driven-design-sys-prompt.itr.md` shows a grant mechanism
  with machine-checkable semantics (what counts as explicit + specific
  approval, what scope it covers, how the harness verifies it); assert a
  write without that grant is denied (negative e2e or permission-hook
  unit check, exit non-zero / permission error).
- `T3` (criterion 3): with advisory prose stripped/ignored (prompt-injection
  style probe asserting "you may write"), a file-write attempt from the
  Advisor grant set is still denied without approval — proving the gate is
  not prompt-dependent.
- `T4` (criterion 4): run the documented read-only Advisor flow (inspect DTR
  fixture, list repo, draft CU content to stdout) with no approval grant;
  assert exit 0 and `git status --porcelain` shows no modifications.
