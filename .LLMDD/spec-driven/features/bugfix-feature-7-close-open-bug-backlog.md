# bugfix-feature-7: Close the open bug backlog

- Status: Proposed
- Created: 2026-10-18
- Feature ID: bugfix-feature-7-close-open-bug-backlog

## Synopsis

Motivation: four bugs are Open after the 2026-10-18 audit, and they have
accumulated because each was parked rather than scheduled. They share no
common cause and span three unrelated domains, so this feature is an
administrative container for tracking them, not a coherent unit of work.
That is stated plainly here because it shapes how it should be split.

Open after the audit (`2e5672e`):

| Bug | Domain | Size | Blocker |
| --- | --- | --- | --- |
| `bug-4-layer-layout-demands-violated` | Scala layout in `itr-compiler` + `dtr-builder` | large | relocation refactor across 26 files |
| `bug-7-session-permissions-autoaccept-nullifies-ask` | permission policy | small | target is a user-global file outside the repo |
| `bug-8-approval-gate-advisory-not-enforced` | git enforcement | medium | needs a new tool |
| `bug-10-cu-scope-boundaries-not-enforced` | CU validation + runner | medium-large | format change invalidates existing frames |

Scope: In — the acceptance criteria and Tests below for each of the four
bugs; the CU decomposition proposed at the end of this spec. Out — the
CUs themselves (drafted as a batch only after this spec is approved), and
any relayout of the four into separate features, which is a Designer
decision recorded below.

### The four bugs in detail

**bug-4 — layer layout.** `itr-compiler/src/main/scala/itrcompiler` violates
the demands in `.LLMDD/instructions/architecture.md` in five ways, all
re-verified present: `application/use_cases` (snake_case, demands
`useCases`); `control/{cli,dependency_injection,entry_point}` (demands
`{dependencyInjection,entryPoint}`, no `cli`); `infrastructure` holding
four folders (`database`, `environment`, `filesystem`, `parsers`) for three
ports; `domain` with `models` only, no `valueObjects`; and
`infrastructure/environment/Environment.scala` reading `sys.env` plus an
in-memory map while loading no `.env` or config file, despite its
docstring claiming it does. Layer roots are already clean, so
`NO_LAYER_ROOT_FILES` holds and is not part of the work. Same shape in
`dtr-builder`.

**bug-7 — autoaccept nullifies the ask tier.** `~/.config/opencode/cli.json`
sets `session.permissions: "autoaccept"`. The CLI's own flag documents the
semantics: `--auto  Auto-approve permissions that are not explicitly
denied`. Two consequences, the second the more important:

- Anything not *explicitly denied* auto-approves, so the Advisor
  frontmatter's `"*": ask` default can never surface a prompt.
- **Therefore `deny` is the only action that survives autoaccept.** This
  reframes the fix: the repo must express its write boundary as explicit
  `deny` rules, not rely on `ask`.

Verified constraint: `session` is **not** a project-config key — the
installed SDK types give project `Config` the keys `agent`, `permission`,
`tools`, `instructions`, `skills` and so on, with no `session`. So the
`autoaccept` value cannot be overridden from the repo; it lives only in
the user-global file. This splits the fix in two, and only one half ships
in this repo:

- *Designer action, out of repo:* set `session.permissions` to a prompting
  value (or remove the key) in `~/.config/opencode/cli.json`.
- *Repo action, in scope here:* restate the Advisor boundary as explicit
  `deny` rules so it holds **regardless** of that setting.

The second half is the one that matters, because it is the only one that
travels with the repository and survives a fresh machine.

**bug-8 — the gate is advisory.** `check-advisor-approval.py` is never
invoked by anything. `run-waves.py` is irrelevant here — this is the
Advisor path, not the Coder path. The fix is a real `pre-commit` hook that
refuses any staged change to git-tracked files unless a matching,
unexpired, in-scope token covers those paths, with CI re-validation of
token coverage against the merged diff as backstop. Same shape as the
pre-push hook that already blocks agent pushes.

Note the residual is narrower than bug-8 originally claimed, per its dated
correction: the Advisor has **no edit, write or patch tool at all**, so the
edit channel is already closed by capability removal. What the hook would
actually defend is the `bash` write channel — `cat > file`, `python3`,
`git add` — which is exactly the path bug-7 governs. bug-7 and bug-8 are
consequently two halves of one exposure and should be scheduled together.

**bug-10 — CU scope unenforced.** Three gaps, all verified: F4 in
`validate-steps.py` only substring-tests for `Do NOT touch:` and never
parses the list or intersects it against declared write paths, with no
allowlist concept at all; the shipped frames' most important exclusions are
prose (`any Scala source`, `any Python tool`, `any ITR frame or feedback
file`) that no matcher can test; and `run-waves.py` populates
`files_changed` from the diff and forwards it to reporting without ever
comparing it to anything. Drift has already been waved through — `cu-001`
found a file from its own no-touch list dirty mid-run, `cu-002` found its
edits pre-existing from an aborted attempt, both `STATUS=COMPLETED`.

### Cost warning on bug-10

Making scope a declared structure is a **breaking format change**: every
existing batch and compiled frame lacks an explicit allowlist, so either
all frames must be recompiled or the validator must infer an allowlist for
legacy content and reject it only when inference fails. That trade should
be decided before the CU batch is written, not during implementation. The
cheapest high-leverage slice is independent of the format change and
should be cut first: compare the `files_changed` the runner already
computes against the paths parsed from the existing `Do NOT touch:` list,
with prose clauses converted to globs. That closes the runtime gap without
touching any frame.

## Implementation status

Proposed. No CUs drafted; the decomposition below is a proposal for the
Designer to accept, reshape, or reject.

### Proposed decomposition

Four independent groups, recommended as four waves of one batch rather
than four features — chosen because the Designer asked for one feature,
but see the splitting note.

- `cu-001` (bug-7, smallest, unblocks framing) — restate the Advisor
  permission boundary as explicit `deny` rules; remove reliance on
  `"*": ask`; document that `session` is user-global only. Verify with a
  probe that `deny` is honoured under `autoaccept` — see Tests.
- `cu-002` (bug-10 runtime slice) — parse `Do NOT touch:` into paths,
  convert prose clauses to globs, assert at run time that `files_changed`
  ∩ no-touch is empty, fail the CU non-zero naming the offending path.
  No format change, no recompile.
- `cu-003` (bug-8) — `pre-commit` hook plus CI re-validation, reusing
  `check-advisor-approval.py` as the validator rather than duplicating it.
  Depends on `cu-001`: the deny rules are what the hook defends.
- `cu-004` (bug-4, largest, last) — relocate the Scala tree to the
  demanded layout and implement real `.env` loading in the Env adapter,
  in `itr-compiler` and `dtr-builder`. Independent of the other three;
  placed last because it is the only group with a real regression surface.

### Splitting note for the Designer

`cu-001`–`cu-003` are a coherent unit: they are all enforcement of the
same triad guarantee, and `cu-003` depends on `cu-001`. `cu-004` shares
nothing with them but a bug number. If independent scheduling matters more
than a single tracking document, splitting bug-4 into its own feature is the
change to make — the other three keep their coupling. This feature keeps
all four because the Designer asked for one, and records the disagreement
rather than burying it.

## Acceptance criteria

1. **bug-4** — `itr-compiler/src/main/scala/itrcompiler` matches the
   demanded layout exactly: `application/{ports,services,useCases}`,
   `control/{dependencyInjection,entryPoint}` with no `cli/`,
   `infrastructure` with one folder per port (DTRLoad, CUWrite,
   ContentRead) plus an Env adapter, `domain/{models,valueObjects}`, all
   camelCase, all four layer roots empty. The Env adapter loads `.env`
   and environment variables rather than only `sys.env` plus an in-memory
   map, and its docstring matches what it does. `dtr-builder` reaches the
   same shape. `sbt test` green in both.
2. **bug-7** — the Advisor write boundary is expressed as explicit `deny`
   rules that hold with `session.permissions: "autoaccept"` set, verified
   by probe rather than assumed. No repo rule depends on `ask` to be
   enforced. The user-global setting is documented as a Designer action
   outside the repository, including that `session` is not a project-config
   key and so cannot be overridden from here.
3. **bug-8** — a `pre-commit` hook refuses a staged change to any
   git-tracked file unless a matching unexpired token covers it, exiting
   non-zero with a message naming the offending path. The hook is a real
   git hook, not an agent-invoked step, and functions with the agent
   disabled. CI re-validates token coverage against the merged diff.
4. **bug-10** — `validate-steps.py` parses the `Do NOT touch:` list into a
   path set and fails a CU whose no-touch list is empty, unparseable, or
   intersects its own declared write paths. `run-waves.py` fails a CU
   non-zero when `files_changed` contains a path in that set, naming both.
   Prose clauses are converted to globs so they are matchable.

## Tests proving resolution

- `T1` (criterion 1) — layout audit: assert the exact directory set per
  layer, assert zero files in all four layer roots, assert the Env adapter
  references `.env` loading and not only `sys.env`. Plus `sbt test` green
  in `itr-compiler` and `dtr-builder`.
- `T2` (criterion 2) — **probe, not assumption.** With
  `session.permissions: "autoaccept"` in effect, assert an explicit
  `permission.edit: deny` is honoured and an `ask` rule does not prompt.
  This probe is the load-bearing proof for bug-7 and its result must be
  recorded either way; if `deny` turns out to be auto-approved too, the
  criterion fails and the fix has to move entirely to bug-8's hook.
  (This is stated as a probe because bug-6 was withdrawn for asserting
  unverified runtime behaviour — the discipline applies here.)
- `T3` (criterion 3) — negative: a write outside token scope is refused by
  the hook with non-zero exit and a clear reason, with no agent
  cooperation; positive: an in-scope unexpired token commits cleanly;
  expiry: a past `expires` denies; bypass: the hook still fires with the
  agent disabled.
- `T4` (criterion 4) — negative: a CU whose no-touch list names its own
  write target fails validation naming both sides; negative: an empty or
  prose-only no-touch list fails rather than passing substring presence;
  negative: a run whose diff touches a no-touch path fails the CU non-zero
  naming the path; positive: a conforming run still passes with
  `files_changed` reported as before.
- `T5` (all four) — after the batch, `python3 .LLMDD/tools/next-spec-id.py
  --check` exits 0, no existing frame regresses, and each bug file records
  its proving evidence and moves Open → Resolved.
