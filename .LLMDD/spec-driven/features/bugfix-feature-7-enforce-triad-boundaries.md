# bugfix-feature-7: Enforce the triad boundaries

- Status: Proposed
- Created: 2026-10-18
- Feature ID: bugfix-feature-7-enforce-triad-boundaries

## Synopsis

Motivation: three bugs are Open after the 2026-10-18 audit, and all three
are the same defect wearing different clothes: the triad's separation
between Advisor, Coder and Designer is asserted in prose and enforced
nowhere. They share a cause — every control is advisory — so unlike the
full backlog they form a coherent unit of work. That is stated plainly
here because it is the reason they are bundled.

Scope: In — the acceptance criteria and Tests below for `bug-7`,
`bug-8` and `bug-10`. Out — `bug-4`, excluded by Designer decision on
2026-10-18 (see "Out of scope" below); the CUs themselves, drafted as a
batch only after this spec is approved; and any change to
`itr-compiler` or `dtr-builder`.

### Out of scope: bug-4

`bug-4-layer-layout-demands-violated` (the Scala layer layout in
`itr-compiler` and `dtr-builder`) is **excluded by Designer decision on
2026-10-18** and remains **Open**. It is a five-violation relocation
refactor across ~26 files with a real regression surface, and it shares
nothing with the enforcement work beyond a bug number. It was carried in
an earlier draft of this spec; the feature was renamed from
`bugfix-feature-7-close-open-bug-backlog` at the same time, because that
name promised to close the whole backlog and would no longer be true.

Also unresolved and still open from that draft: whether bug-4's scope
covers `dtr-builder` as well as `itr-compiler`, since its suggested test
demands `sbt test` green in both. That needs settling whenever bug-4 is
picked up, not here.

### The three in-scope bugs in detail

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
### Why these three belong together

They are one defect in three places. `bug-7`, `bug-8` and the bug-10
runtime slice each describe the same failure: the boundary is declared and
nothing checks it. `cu-003` depends on `cu-001`, because the deny rules are
exactly what the hook defends. Three CUs, one guarantee.

## Acceptance criteria

1. **bug-7** — the Advisor write boundary is expressed as explicit `deny`
   rules that hold with `session.permissions: "autoaccept"` set, verified
   by probe rather than assumed. No repo rule depends on `ask` to be
   enforced. The user-global setting is documented as a Designer action
   outside the repository, including that `session` is not a project-config
   key and so cannot be overridden from here.
2. **bug-8** — a `pre-commit` hook refuses a staged change to any
   git-tracked file unless a matching unexpired token covers it, exiting
   non-zero with a message naming the offending path. The hook is a real
   git hook, not an agent-invoked step, and functions with the agent
   disabled. CI re-validates token coverage against the merged diff.
3. **bug-10** — `validate-steps.py` parses the `Do NOT touch:` list into a
   path set and fails a CU whose no-touch list is empty, unparseable, or
   intersects its own declared write paths. `run-waves.py` fails a CU
   non-zero when `files_changed` contains a path in that set, naming both.
   Prose clauses are converted to globs so they are matchable.

## Tests proving resolution

- `T1` (criterion 1, bug-7) — **probe, not assumption.** With
  `session.permissions: "autoaccept"` in effect, assert an explicit
  `permission.edit: deny` is honoured and an `ask` rule does not prompt.
  This probe is the load-bearing proof for bug-7 and its result must be
  recorded either way; if `deny` turns out to be auto-approved too, the
  criterion fails and the fix has to move entirely to bug-8's hook.
  (This is stated as a probe because a bug withdrawn earlier the same day
  asserted unverified runtime behaviour — the discipline applies here.)
- `T2` (criterion 2) — negative: a write outside token scope is refused by
  the hook with non-zero exit and a clear reason, with no agent
  cooperation; positive: an in-scope unexpired token commits cleanly;
  expiry: a past `expires` denies; bypass: the hook still fires with the
  agent disabled.
- `T3` (criterion 3) — negative: a CU whose no-touch list names its own
  write target fails validation naming both sides; negative: an empty or
  prose-only no-touch list fails rather than passing substring presence;
  negative: a run whose diff touches a no-touch path fails the CU non-zero
  naming the path; positive: a conforming run still passes with
  `files_changed` reported as before.
- `T4` (all three) — after the batch, `python3 .LLMDD/tools/next-spec-id.py
  --check` exits 0, no existing frame regresses, and each bug file records
  its proving evidence and moves Open → Resolved.
