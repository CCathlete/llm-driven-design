# feat-18: Raise the hypothesis gate to 85% and surface ledger data each stage

- Status: Proposed
- Created: 2026-11-20
- Feature ID: feat-18-raise-hypothesis-gate-and-print-ledger

## Synopsis

Motivation: feat-15 made the Hypothesis Engine the sole owner of experiment
execution, evidence and the examination decision, and the `prepare-implementation`
skill made a green light a hard gate before any CU is drafted. Two weaknesses
remain. First, the green-light threshold is a per-hypothesis JSON value that
defaults to `0.6` and has no engine-side minimum, so a run can be approved at a
confidence the Designer no longer accepts. Second, the engine emits only a
one-line verdict; it never surfaces what the ledger actually holds — the
hypothesis, its DTR coordinates, its justification and the current probability
of correctness — and nothing forces that evidence into the design conversation.
The Advisor therefore revises and re-runs hypotheses without a visible,
cumulative record of *why* a stage was red or how the posterior moved toward a
green light.

This feature (a) raises the effective approval bar to `0.85` in both places —
the authoring default and an engine-enforced floor — and (b) makes the engine
emit a structured, cumulative per-stage ledger report and makes the hypothesis
stage fail closed until the Advisor has pasted that report verbatim into the
conversation. The engine still owns execution, evidence and the decision; the
new report is a read-only projection of the ledger, never a second decision
path.

Scope (in):
- Engine floor: a hypothesis whose `gate_probability_for_correctness` is below
  `0.85` fails closed with `ADVISOR_ACTION_REQUIRED`; no calculation, no green
  light. `0.85` itself is allowed (inclusive).
- Authoring default: the `prepare-implementation` hypotheses template and every
  in-repo example use `0.85`.
- A required, Advisor-authored `justification` on the hypothesis, carried
  through the JSON contract, the domain model, the ledger schema and the report;
  blank or missing justification fails closed.
- A structured `LedgerStageReport` emitted by the engine on every invocation
  (RED, GREEN or BLOCKED), containing the hypothesis, its DTR coordinates, its
  justification, the current probability for correctness (posterior), the gate,
  the gap, the decision label, RED breakpoints/fix, and the cumulative decision
  history from the ledger. It is printed to stdout and persisted to a
  deterministic report file, one block per stage.
- An amendment to `prepare-implementation` step 3: after every engine run the
  Advisor must paste the report block verbatim into the conversation and record
  it in the feature's conversation-record artifact before revising hypotheses or
  advancing to step 4.
- A deterministic checker tool that treats a missing or non-verbatim report echo
  as a red gate, plus engine specs, tool tests and a skill-contract check.

Scope (out):
- Migrating existing hypotheses files (e.g. bugfix-feature-17) — this is a
  going-forward default plus floor; old runs are not re-gated.
- Changing Bayesian math, the concentration constant, the prior, or the
  GREEN/RED/BLOCKED decision semantics beyond the floor and the report.
- Letting the engine observe the chat directly; enforcement is artifact-backed
  (stdout block + report file + conversation record + checker), because the
  engine is a CLI with no access to the conversation.
- Applying fixes, drafting CUs, compiling ITR, running waves, or changing
  Coder/Verifier behaviour.
- Adding a ledger table; justification is a column on the existing hypotheses
  record.

## Implementation status

Proposed. No CUs assigned, no implementation merged. Intended ITR directory
`.LLMDD/ITRS/feat-18-raise-hypothesis-gate-and-print-ledger/`. The work spans the
Scala Hypothesis Engine (`hypothesis-engine/`), the `prepare-implementation`
skill, a new checker under `.LLMDD/tools/`, and tests under `.LLMDD/tests/` and
the engine's `src/test/scala/`. Move Proposed → In Progress when CUs are
assigned; move to Implemented when all waves are done and E2E verify is green,
recording the implementing commit(s) and ITR directory here.

## Acceptance criteria

1. The `prepare-implementation` step 3 hypotheses template uses
   `gate_probability_for_correctness: 0.85`, and no in-repo reference default or
   example for the hypothesis gate remains at `0.6`.
2. The engine enforces an inclusive floor of `0.85`: a hypothesis with a gate of
   `0.85` proceeds; a gate of `0.849` (or lower) produces
   `ADVISOR_ACTION_REQUIRED` naming the floor violation, with no posterior
   calculation and no GREEN_LIGHT persisted.
3. The hypotheses JSON contract requires a non-empty `justification`; absent,
   empty or whitespace-only justification fails closed with
   `ADVISOR_ACTION_REQUIRED` naming the missing datum before any evaluation.
4. `justification` is a first-class `Hypothesis` field and a column on the
   existing `hypotheses` ledger table; it round-trips through persistence and
   appears in the report. No new ledger table is introduced (the schema keeps
   seven tables).
5. On every engine invocation — RED, GREEN or BLOCKED — the engine emits a
   `LedgerStageReport` containing: stage number (1-based count of decision
   records for that hypothesis), hypothesis id, statement, DTR coordinates,
   justification, `probability_for_correctness` (posterior), gate, gap
   (gate − posterior), decision label, and on RED the breakpoints with execution
   IDs and the suggested fix.
6. Each report is cumulative: it carries the prior decision history for that
   hypothesis from the ledger (stage, posterior, gate, decision), so the
   progression from the first RED through each re-run to the GREEN_LIGHT stage
   is visible in one block.
7. The engine writes the report to a deterministic file
   `.LLMDD/ITRS/<feature>/<feature>.ledger-report.jsonl`, one JSON block per
   stage, and prints a verbatim-identical block to stdout.
8. `prepare-implementation` step 3 is amended so that after every run the Advisor
   pastes the report block verbatim into the conversation and appends it to
   `.LLMDD/ITRS/<feature>/<feature>.hypothesis-conversation.md`; no hypothesis
   revision and no advance to step 4 occurs without that paste for the current
   stage. A GREEN_LIGHT is likewise reported before step 4.
9. A deterministic checker `.LLMDD/tools/check-hypothesis-report.py` verifies,
   for the current stage: (a) the report file exists and holds one block for
   that stage, (b) that block appears verbatim in the conversation-record
   artifact, and (c) the block's decision label matches the verdict line; it
   exits non-zero on any failure. `prepare-implementation` step 3 runs it as a
   hard gate after each stage, before the existing verdict interpretation.
10. Decision semantics are otherwise unchanged: GREEN iff the ledger is
    complete, all evidence valid, the gate meets the floor, and the posterior is
    at or above the gate; breakpoints and suggested fix on RED are unchanged;
    the report is derived from ledger state and never decides the outcome.
11. Verification is green: `cd hypothesis-engine && sbt -batch test` passes
    including new specs for criteria 2–7; `uvx pytest -q` passes the checker
    tests for criterion 9; `python3 .LLMDD/tools/skill-check.py
    prepare-implementation --print-prompt` passes for the amended skill.
12. `uvx mypy --strict .LLMDD/tools/check-hypothesis-report.py` reports zero
    errors (annotation presence alone does not imply type agreement).

## Tests

Terms: a *stage* is one engine invocation for a hypothesis; the report file is
`<feature>.ledger-report.jsonl` and the conversation record is
`<feature>.hypothesis-conversation.md` under `.LLMDD/ITRS/<feature>/`.

- T1 (criterion 1, static): grep the `prepare-implementation` skill and in-repo
  hypothesis examples for `0.85` present and no stale `0.6` gate default. Prove:
  `grep -c 'gate_probability_for_correctness.*0.85' .LLMDD/skills/prepare-implementation/SKILL.md`
  prints ≥1 and no `0.6` default remains.
- T2 (criterion 2, engine): `GateSpec` — assert gate `0.85` with a complete
  ledger yields GREEN at posterior ≥ gate, and gate `0.849` yields
  `ADVISOR_ACTION_REQUIRED` with zero new `calculations`/`decisions` rows.
- T3 (criteria 3, 4, engine): `LedgerValidatorSpec` / `LedgerSpec` — missing,
  empty and whitespace justification each fail closed; a non-empty
  justification round-trips through `saveHypothesis` and the `hypotheses`
  table; the schema still has exactly seven `CREATE TABLE` statements.
- T4 (criteria 5, 6, 7, engine): `LedgerStageReportSpec` — run two RED stages
  then a GREEN stage; assert three report blocks with correct fields (including
  coordinates, justification, posterior/gate/gap), a cumulative `history` of
  three decisions in the final block, and byte-identical stdout block and file
  block per stage.
- T5 (criterion 8, skill contract): `skill-check.py prepare-implementation
  --print-prompt` lists the mandatory paste into the conversation and the
  conversation record; a static check that step 3 forbids advancing before the
  paste.
- T6 (criterion 9, tool): `uvx pytest -q .LLMDD/tests/test_hypothesis_report.py`
  — table-driven: report file + verbatim echo + matching verdict passes; missing
  file, non-verbatim echo and mismatched verdict each exit non-zero with a named
  reason.
- T7 (criterion 10, regression): existing `GateSpec`, `BayesianEvaluatorSpec`
  and `LedgerCompletenessSpec` assertions still pass unchanged.
- T8 (criterion 11, suites): `cd hypothesis-engine && sbt -batch test` all green;
  T6 green; T5 green.
- T9 (criterion 12, strict typing):
  `uvx mypy --strict .LLMDD/tools/check-hypothesis-report.py` → zero errors.

Language note: engine work is Scala, so `ARCH.PYTHON.SCOPE` and its mypy probe do
not apply to engine code; the new checker is Python and is covered by T9.

## Phase 2 — self-contained fat jar for the hypothesis engine

Motivation: the engine is currently source-distributed and run via
`sbt -batch runMain`, so it cannot be handed to a machine without an sbt/JDK
toolchain or downloaded as a release artifact. This phase makes it a single
self-contained executable jar, matching the distribution model already used for
`dtr-builder`, `commit-tool` and `itr-compiler` (fat JARs published as GitHub
Releases, never tracked in git). This work is LLMDD distribution consistency,
not new engine behaviour.

Acceptance criteria (continuing from phase 1):

13. `hypothesis-engine/project/plugins.sbt` declares the `sbt-assembly` plugin;
    `hypothesis-engine/build.sbt` sets `Compile / mainClass` to
    `hypothesisengine.control.Main` and `assembly / assemblyJarName` to a
    deterministic name (e.g. `hypothesis-engine.jar`).
14. `cd hypothesis-engine && sbt -batch assembly` exits 0 and produces a
    self-contained jar containing all runtime dependencies (sqlite-jdbc, circe)
    and the `ledger-schema.sql` resource. `java -jar <jar>` on a hypotheses +
    experiments pair runs the engine without sbt on PATH.
15. The engine accepts an explicit ledger path via JVM property
    `-Dhypothesis-engine.ledger.path=<path>`, read in `Main.scala` and passed to
    `new SqliteLedger(path)`; the default remains `ledger.db` for source runs.
    Running the jar from any cwd with the property set writes the ledger to
    exactly that path, creating it when absent — no CWD-fragile split.
16. `.gitignore` ignores the assembled jar at the module root (anchored), so the
    binary is a release artifact and never tracked.
17. Verification stays green: `cd hypothesis-engine && sbt -batch assembly` and
    `sbt -batch test` pass; a jar run with the ledger-path property emits a
    verdict line and creates the ledger at the given path.

Tests (phase 2):

- TJ1 (criterion 13, static + build): grep `plugins.sbt` for `sbt-assembly`,
  `build.sbt` for `mainClass` and `assemblyJarName`;
  `cd hypothesis-engine && sbt -batch assembly` exits 0.
- TJ2 (criterion 14, runtime): `java -jar hypothesis-engine.jar <hypotheses>
  <experiments>` from a temp cwd runs without sbt and prints a verdict line.
- TJ3 (criterion 15, runtime): run the jar with
  `-Dhypothesis-engine.ledger.path=<tmp>` from a different cwd; the ledger
  appears at exactly that path (created when absent) and not at the cwd.
- TJ4 (criterion 16, static): `git check-ignore hypothesis-engine/<jar>` prints
  the path; the jar is never staged.
- TJ5 (criterion 17, suites): `sbt -batch assembly` and `sbt -batch test` green.

