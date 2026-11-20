# feat-16: waves-done-broken Advisor handoff skill

- Status: Implemented
- Created: 2026-11-20
- Implemented: 2026-11-20
- Feature ID: feat-16-waves-done-broken

## Synopsis

Motivation: the pipeline had a defined path for a converged, green wave run
(`review-implementation`, which closes specs) but none for the opposite terminal
state — waves finished, artifacts were rebuilt, and the feature is still broken.
Without a named procedure that state was handled ad hoc: another review pass on
a known-bad build, or an immediate jump back to coders with no revisit of the
CU decomposition. This feature adds a `waves-done-broken` skill: a single-feature
Advisor handoff that confirms the failure live, localizes it to a CU, decides
design defect versus implementation defect, and drafts corrected CU content for
Designer approval.

The skill is documented after the fact: it was authored directly as a design
artifact (a `SKILL.md`), not via CUs and waves, because a skill is a Markdown
procedure rather than compiled application code. This spec records what was
delivered and how to prove it, consistent with the 9.41 lesson that a spec must
not lag a finished implementation.

Scope (in):
- A skill at `.LLMDD/skills/waves-done-broken/SKILL.md`, reachable via the
  `.opencode/skills` symlink, with a frontmatter `name` and `description` that
  route the Designer's "still broken after rebuild" report to it.
- Live confirmation of the reported state (convergence file, rebuild commit in
  `git log`, failing acceptance probes re-run) with a red gate on mismatch.
- Per-break localization (failing `T_i` → CU → DTR coordinates → file/line),
  classification as design vs implementation defect, and hand-back of
  implementation-only breaks to the Coder.
- For design defects: a corrected `.fix-N.batch.json` (new variant each cycle,
  never overwriting a prior one) plus a `broken-wave-diagnosis.txt`, gated by
  `validate-steps.py`, committed as design output only.
- A hard stop at the Designer approval boundary: no application code, no frame
  edits, no wave re-run, no spec closure, no push.
- A fresh `dtr-builder` scan treated as optional (STEP8 iteration, not STEP1
  restart), requested only when evidence shows coordinates no longer match.

Scope (out):
- Implementing the fix, editing compiled frames, running `run-waves.py`,
  closing bugs or marking a feature Implemented, and pushing.
- Bundling two features in one invocation (one `<feature-name>` per run).
- Adding application source (the feature is a Markdown skill only).

## Implementation status

Implemented in commit `ef08f03`, recorded in the book as section 9.40
(`d60c710`). The delivered artifact is
`.LLMDD/skills/waves-done-broken/SKILL.md` (168 lines); `.opencode/skills` is a
symlink to `.LLMDD/skills`, so one authored file made the skill immediately
available to the harness (confirmed by skill registration) with no registry edit.

No ITR directory or wave run applies: this is a design-artifact feature, not
compiled application code. Two follow-on touches are part of the same delivery:
the `prepare-implementation` hard-gate step it complements (`7811007`, book
9.39) and the feat-15 close-out that names it (`06c9c3f`, book 9.41).

## Acceptance criteria

1. A skill file exists at `.LLMDD/skills/waves-done-broken/SKILL.md` with
   frontmatter declaring `name: waves-done-broken` and a `description` that
   says to use it when waves ran, artifacts were rebuilt, and the feature is
   still broken.
2. The skill is reachable at `.opencode/skills/waves-done-broken/SKILL.md` as the
   same inode (symlink), and is registered as an available skill.
3. The skill scopes one `<feature-name>` per invocation and never batches two
   features.
4. The skill requires live confirmation — convergence file, rebuild commit in
   `git log`, failing probes re-run, never quoted from a transcript — and treats
   a mismatch as a red gate (no phantom-failure diagnosis).
5. The skill maps each failing `T_i` to a CU and classifies breaks as design vs
   implementation, handing implementation-only breaks back to the Coder.
6. For design defects the skill directs drafting a new `.fix-N.batch.json`
   (preserving prior cycles) plus a `broken-wave-diagnosis.txt`, with full CU
   content, dependency order, and a `validate-steps.py` gate.
7. The skill halts at the Designer approval boundary: no application code, no
   frame edits, no `run-waves.py`, no spec closure, specs stay open until a
   green `review-implementation`.
8. The skill does not mandate a fresh `dtr-builder` scan; it requests one only
   if evidence shows DTR coordinates no longer match the working tree.
9. The skill respects the Advisor approval gate (standing grant for
   `.LLMDD/ITRS/*`, scoped token for any application-code write) and forbids
   `git add -A` and push.
10. A book subsection under the current-version section of
    `docs/building-llm-driven-design.md` records the skill, committed
    separately with a `docs:` message.

## Tests

This feature adds only Markdown (a `SKILL.md`), so there is no Python source to
type-check and the `mypy --strict` probe does not apply. Each criterion is proved
by a static probe run from the repo root; expected result shown inline.

1. `test -f .LLMDD/skills/waves-done-broken/SKILL.md && head -4 <file>` → frontmatter
   has `name: waves-done-broken` and a description mentioning "rebuilt" and
   "still broken" (crit 1).
2. `readlink -f .opencode/skills` → `/home/kcat/Repos/llm-driven-design/.LLMDD/skills`;
   `cmp -s .LLMDD/skills/waves-done-broken/SKILL.md .opencode/skills/waves-done-broken/SKILL.md`
   → exit 0; skill appears in the available-skills list (crit 2).
3. `grep -c 'One invocation = one feature' <file>` → 1 (crit 3).
4. `grep -F -c 'Re-run the failing acceptance probes live' <file>` → 1 and
   `grep -c 'phantom failure' <file>` → 1 (crit 4).
5. `grep -c 'Decide design defect vs implementation defect' <file>` → 1 and
   `grep -F -c 'hand the evidence back to the' <file>` → ≥1 (crit 5).
6. `grep -F -c '.fix-N.batch.json' <file>` → ≥1,
   `grep -F -c 'broken-wave-diagnosis.txt' <file>` → ≥1,
   `grep -c 'validate-steps.py' <file>` → ≥1 (crit 6).
7. `grep -c 'never implements' <file>` → ≥1; `git show --stat ef08f03` → only
   `.LLMDD/skills/waves-done-broken/SKILL.md` changed (crit 7).
8. `grep -F -c 'A fresh scan is **not**' <file>` → 1 and
   `grep -F -c 'before re-running' <file>` → 1 (crit 8).
9. `grep -c 'scoped approval token' <file>` → 1 and
   `grep -F -c 'Never `git add -A`' <file>` → ≥1 (crit 9).
10. `git -C ~/Repos/llmdd-book log --oneline --grep='9.40' -1` → `d60c710 ...`;
    `git show --stat d60c710` → doc file only (crit 10).

Commands (expected: all pass, zero application source added):
- `python3 .LLMDD/tools/skill-check.py feature --print-prompt`
- The numbered static probes above.
