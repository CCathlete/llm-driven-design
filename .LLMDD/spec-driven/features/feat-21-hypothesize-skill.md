# feat-21: Standalone hypothesize skill (hypothesis reasoning + validation)

- Status: Proposed
- Created: 2026-11-21
- Feature ID: feat-21-hypothesize-skill

## Synopsis

Motivation: the only place the LLMDD pipeline reasons about "why does this not
work" or "is this proposed fix actually correct" is step 3 of
`@prepare-implementation`. That step is embedded in a chain that also commits
specs, builds a DTR, drafts CUs, compiles ITR and plans waves. A Designer who
just wants a hypothesis investigated has to either run the whole chain or
improvise without the engine's hard gate. The experiment ledger, the verbatim
stage echo and `check-hypothesis-report.py` were all built to make that
reasoning rigorous; they should be usable on their own.

Scope (in): create `.LLMDD/skills/hypothesize/SKILL.md` (reachable as
`@hypothesize` through the existing `.opencode/skills -> .LLMDD/skills`
symlink) owning the hypothesis reasoning + validation segment; support two
declared modes over one shared loop; define the baseline-DTR input policy;
reduce `@prepare-implementation` step 3 to a delegation so there is one source
of truth; keep every artifact path and engine command unchanged.

Scope (out): changing the Hypothesis Engine itself, changing the 0.85 minimum
gate rule, CU drafting, batch creation, ITR compilation, waves, dry-run,
`run-waves.txt`, executing `run-waves.py`, and the rest of the prep chain.

Two modes, one loop:
- **diagnosis** — "why is this not working?" The hypothesis states a candidate
  cause at DTR coordinates; `GREEN_LIGHT` means that cause is supported, a
  `RED_LIGHT` names the breakpoints to revise the hypothesis against.
- **validation** — "is this suggested solution correct?" The hypothesis states
  the solution claim; `GREEN_LIGHT` means the claim is confirmed, a
  `RED_LIGHT` names the coordinates where the claim fails.
Both modes use identical experiment authoring, engine invocation, verbatim
echo, hard gate and verdict handling; only the hypothesis statement template
and the interpretation of a green result differ.

## Implementation status

Proposed. Not implemented. No CUs assigned, no waves, no implementation
commit. Move to In Progress when CUs are assigned, to Implemented when both
wave(s) converge, the contract tests below pass and E2E verify is green.
Target ITR directory: `.LLMDD/ITRS/feat-21-hypothesize-skill/`.

## Acceptance criteria

1. `.LLMDD/skills/hypothesize/SKILL.md` exists with valid YAML frontmatter
   whose `name` is `hypothesize` and whose `description` names both question
   types ("why is this not working" and "is this suggested solution correct"),
   so `@hypothesize` resolves through the existing skills symlink.
2. The skill declares exactly two modes — `diagnosis` and `validation` — each
   with a hypothesis-statement template and an explicit statement of what
   `GREEN_LIGHT` means for that mode; both modes share one reasoning/validation
   loop and one hard gate.
3. The skill states the DTR input policy: reuse an existing baseline DTR at
   `.LLMDD/DTRS/<subject>/<app>.dtr`; build it with `dtr-builder` when it is
   absent or stale; never hand-edit a DTR. Stale means any of: file missing,
   no `META.GENERATOR=dtr-builder`, missing `FILE.`/`CODEX.`/`TYPE.` entries,
   or `META.TIMESTAMP` older than the newest source file under the scanned
   root.
4. The skill is the canonical statement of the engine procedure: write
   `<subject>.hypotheses.json` (`id`, `statement`, `dtr_coordinates`,
   `gate_probability_for_correctness` >= 0.85, non-empty `justification`);
   write `<subject>.experiments.jsonl`, one JSON object per line with
   `experiment_id`, `hypothesis_id`, `runtime`, `command` (argv list),
   `working_directory`, `timeout_seconds`, `test_artifact_version`; run
   `cleanLedger` only before the first stage; run
   `runMain hypothesisengine.control.Main`; echo the ledger block verbatim
   into the conversation and `hypothesis-conversation.md`; run
   `check-hypothesis-report.py` as a hard gate; interpret `GREEN_LIGHT`,
   `RED_LIGHT` and `ADVISOR_ACTION_REQUIRED` with their fix loops; record the
   verdict line in `hypothesis-verification.txt`.
5. The skill states an explicit stop boundary: it ends at the recorded verdict
   and does not draft a batch CU, compile ITR, plan or dry-run waves, write
   `run-waves.txt`, or execute the operational wave command.
6. `@prepare-implementation` step 3 is replaced by a delegation to
   `@hypothesize`; the full engine-procedure body (hypotheses JSON fields,
   experiments JSONL fields, `cleanLedger`, `runMain`, verbatim echo, checker
   gate, verdict list) appears in exactly one skill file. All other
   prepare-implementation steps keep their strict order, and the hard gate
   (no batch CU before `GREEN_LIGHT`) remains stated.
7. Artifact paths stay `.LLMDD/ITRS/<subject>/<subject>.hypotheses.json`,
   `.experiments.jsonl`, `.ledger-report.jsonl`, `.hypothesis-conversation.md`
   and `hypothesis-verification.txt`, so the engine and
   `check-hypothesis-report.py` require no changes.
8. Both `python3 .LLMDD/tools/skill-check.py hypothesize --print-prompt` and
   the same command for `prepare-implementation` exit 0, and each prompt's
   "Skill File" resolves to the corresponding SKILL.md.

## Tests

- `.LLMDD/tests/feat-21-hypothesize-skill/test_skill_contract.py` (unittest):
  load both SKILL.md files and assert criteria 1–8 statically — frontmatter
  `name`/`description`, both mode names and their GREEN_LIGHT meanings, the
  DTR policy strings including the four staleness conditions, all canonical
  engine markers present in `hypothesize/SKILL.md`, the same markers absent
  from `prepare-implementation/SKILL.md`, the delegation present there, the
  stop-boundary exclusions present, and the unchanged artifact paths. Every
  assertion reports the missing marker by name.
- `python3 .LLMDD/tools/skill-check.py hypothesize --print-prompt` -> exit 0,
  output contains `Skill File: .LLMDD/skills/hypothesize/SKILL.md`.
- `python3 .LLMDD/tools/skill-check.py prepare-implementation --print-prompt`
  -> exit 0, output still lists the mandatory requirements (commit specs,
  build DTR, `validate-steps.py`, compile) and no longer embeds the engine
  command block.
- Checker smoke against recorded artifacts (proves criterion 7 needs no engine
  change): `python3 .LLMDD/tools/check-hypothesis-report.py --report
  .LLMDD/ITRS/bugfix-feature-20-fix-run-waves-phantom-detection/bugfix-feature-20-fix-run-waves-phantom-detection.ledger-report.jsonl
  --conversation
  .LLMDD/ITRS/bugfix-feature-20-fix-run-waves-phantom-detection/bugfix-feature-20-fix-run-waves-phantom-detection.hypothesis-conversation.md
  --stage 2 --verdict GREEN_LIGHT` -> `CHECK_OK`, exit 0.
- Python discipline probe: `uvx mypy --strict
  .LLMDD/tests/feat-21-hypothesize-skill/test_skill_contract.py` -> 0 errors.
- No Scala source changes are in scope, so `sbt test` is not an acceptance
  gate for this feature; the checker smoke above is the live engine-side proof.
