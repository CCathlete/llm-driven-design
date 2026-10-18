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
feature adds deterministic enforcement, not prose: least-privilege Advisor
capabilities by default (read-only inspection plus scratch creation —
directories and text files outside tracked code), plus a scoped approval
token for the exceptional code-change case. Every code change (any
modification to a git-tracked file) requires a token file
`.LLMDD/approvals/<id>.json` naming files plus commands plus reason plus
expiry, and a gate script `.LLMDD/tools/check-advisor-approval.py` that
exits non-zero without a valid, unexpired, in-scope token. It serves the
Designer (side-effect-free design sessions, scoped auditable grants) and
downstream Coder/Verifier review (no untraced writes).

Scope: In: `.opencode/agents/llmdd/advisor.md` permission frontmatter
(read-only bash allowlist — `ls`, `cat`, `rg`, `grep`, `head`, `tail`,
`find`, `wc`, `file`, `stat`, full `git` — plus `mkdir`/`touch` for
scratch creation, default deny/ask), the approval-token schema
(`.LLMDD/approvals/<id>.json`: files, commands, reason, granted-by,
expiry), the gate script (`.LLMDD/tools/check-advisor-approval.py`,
exit non-zero without a valid in-scope token), and the matching
`RULE.ADV.*` / system-tensor wording in
`.LLMDD/prompts/llm-driven-design-sys-prompt.itr.md` where the
enforcement semantics change. In: static audit + gate-script unit checks
+ negative e2e proving the gate. Out: `coder.md` / `verifier.md` grants,
`dtr-builder`, `itr-compiler`, `run-waves`, skill-doc rewrites except
where the approval mechanism itself is documented. Fixes
`bug-5-advisor-writes-without-approval`. Code means git-tracked files;
scratch means untracked paths (`itr-buffer/`, `/tmp/opencode/`).

## Implementation status

In progress. `cu-001` (read-only frontmatter) and `cu-002`
(`RULE.ADV.APPROVAL_REQUIRED` + Approval gate prose) are merged. Remaining
to close `bug-5-advisor-writes-without-approval`: `cu-003` (approval-token
schema + gate script with unit checks) and `cu-004` (widened frontmatter:
read-only set + `git` + `mkdir`/`touch`, exact scoping of code vs
scratch). ITR directory
`.LLMDD/ITRS/bugfix-feature-5-enforce-advisor-read-only-approval/`.

## Acceptance criteria

1. Default Advisor session has no write-capable grant on code: `edit: deny`
   holds and blanket `bash: allow` is gone — bash is a read-only allowlist
   (`ls`, `cat`, `rg`, `grep`, `head`, `tail`, `find`, `wc`, `file`,
   `stat`, `git`) plus `mkdir`/`touch` for scratch creation only, with a
   trailing `"*": ask` or `"*": deny` default for everything else.
2. Every code change requires a scoped approval token that is
   deterministically enforced: `.LLMDD/approvals/<id>.json` names files
   plus commands plus reason plus granted-by plus expiry; the gate script
   `.LLMDD/tools/check-advisor-approval.py` validates schema, expiry, and
   that each changed tracked file and invoked command is within token
   scope, exiting non-zero (write denied) otherwise. No token, expired
   token, or out-of-scope path/command is denied deterministically — never
   by prose alone.
3. The enforcement is capability-level, not prompt-only: removing or
   ignoring the advisory prose still leaves code writes blocked without a
   valid in-scope token (harness `edit: deny` + gate-script denial).
4. Read-only Advisor workflow is preserved, including scratch writes: DTR
   analysis, repo inspection, directory creation, and draft-text/CU-content
   writes to untracked scratch paths work with no approval token, and
   `git status` shows no tracked-file modifications from that path.

## Tests

- `T1` (criterion 1, bug-5 repro): parse
  `.opencode/agents/llmdd/advisor.md` frontmatter; assert `edit: deny`,
  assert no blanket `bash: allow`, assert bash allowlist contains the
  read-only set (`ls`, `cat`, `rg`, `grep`, `head`, `tail`, `find`, `wc`,
  `file`, `stat`, `git`) plus `mkdir`/`touch` and a trailing `"*": ask` or
  `"*": deny` default. Fails on the pre-fix file (blanket allow present).
- `T2` (criterion 2): gate-script unit checks — no token file exits
  non-zero; expired token exits non-zero; token whose `files` omit a
  changed tracked file exits non-zero; token whose `commands` omit the
  invoked write command exits non-zero; valid unexpired in-scope token
  exits zero. Plus negative e2e: an Advisor-attributed code modification
  with no token is denied (gate script exit non-zero on the resulting
  diff), and succeeds only with an in-scope token.
- `T3` (criterion 3): with advisory prose stripped/ignored (prompt-injection
  style probe asserting "you may write"), a code-write attempt is still
  denied without a valid in-scope token — harness `edit: deny` plus gate
  script both refuse, proving the gate is not prompt-dependent.
- `T4` (criterion 4): run the documented read-only Advisor flow (inspect DTR
  fixture, list repo, `mkdir` scratch dir, write draft text to untracked
  scratch, draft CU content to stdout) with no token; assert exit 0 and
  `git status --porcelain` shows no tracked-file modifications.
