# feat-19: Replace probe-count probability with coverage-and-falsification scoring

- Status: Proposed
- Created: 2026-11-21
- Feature ID: feat-19-weight-evidence-by-coverage

## Synopsis

Motivation: the hypothesis engine's `BayesianEvaluator.update` computes
`posterior = (prior*2 + passes) / (2 + total)` — a Beta-Binomial smoothing whose
score rises with the *number* of probes, not with how well the probes test the
claim. At `prior=0.5`, gate `0.85`: 5 passing probes reach 0.857 (GREEN), 15 reach
0.941, and even 19 passes plus 1 failure reach 0.909 (GREEN). Adding redundant or
trivial probes buys a green light; a single counter-example is diluted away. The
number is therefore a poor indicator of the probability that the hypothesis is
correct — the exact quantity `probability_for_correctness` claims to report.

This feature replaces that calculation with a **coverage-and-falsification
score** that rewards breadth of evidence over quantity and treats a failure as
dominant. It serves every consumer of the hypothesis gate (notably
`prepare-implementation`, which hard-gates on GREEN_LIGHT).

Scope: In — the evaluator in
`hypothesis-engine/src/main/scala/hypothesisengine/application/Examination.scala`,
the call site in `control/Main.scala`, the experiment-definition decoder
(`control/Main.scala` / experiment JSONL codec), domain model in
`domain/Model.scala`, and ScalaTest coverage. Out — the report/ledger JSON shape
and field names (`probability_for_correctness`, `posterior`, `gate`, `gap`,
`decision`, `history` all remain; only the numeric value changes), the
`check-hypothesis-report.py` contract, the gate floor (`0.85` unchanged), the
remediation/breakpoint logic, and any Python implementation work. Also in scope: the two ledger-lifecycle
subcommands (`init`, `ledgerCleanup`) on the CLI and the port operations they need.
This is a Scala feature, so the Python `mypy --strict` probe does not apply;
`sbt test` is the language proof.

### The model

For a hypothesis declaring coordinates `C = [c1..cn]` and results grouped by the
coordinate each experiment verifies:

1. **Per-coordinate evidence** — `e(c) = 1 − 0.5^(passes(c))` when `passes(c) > 0`
   and no failure on `c` (0.5 after one pass, 0.75, 0.875, …); `e(c) = 0` when a
   coordinate has no passing probe or any failing probe.
2. **Coverage** — `coverage = (Σ e(c)) / |C|`. Unverified coordinates contribute 0
   and pull the mean down; repeating probes on one coordinate is bounded by
   `e(c) < 1`.
3. **Score** — `score = prior + (0.95 − prior) * coverage`. The ceiling `0.95 < 1`
   encodes that finite evidence never proves certainty.
4. **Falsification dominance** — if any probe failed, `score = min(score, prior)`.
   With the default `prior=0.5` a single failure can never clear the `0.85` gate,
   regardless of how many probes passed.
5. **No evidence** — with zero probes, `score = prior` (red at the default gate).

Worked checks at `prior=0.5, ceiling=0.95` (gate `0.85`):
- 4 coordinates each with 3 passes → coverage `0.875`, score `0.894` GREEN.
- 4 coordinates, only 3 covered (3 passes each) → coverage `0.75`, score `0.795` RED.
- 4 coordinates, one falsified → score capped to `0.5` RED.
- 1 coordinate, 1 pass → `0.725`; 10 passes on that one coordinate → `0.9496`
  (bounded by ceiling; the per-probe gain shrinks geometrically).

### Coordinate mapping (backward-compatible)

Each `ExperimentDefinition` gains an optional `dtr_coordinates` field (vector of
DTR coordinate strings, default empty). When present, a result is credited to
those coordinates; when absent, the effective coordinate is `hypothesis.coordinates(i)`
for experiment index `i` (the same positional convention `RemediationReporter`
already uses for breakpoints). Existing hypotheses without the new field keep
working and are scored by position, so no prep artifact needs rewriting.

### Ledger lifecycle commands (jar-only operation)

The engine is consumed as `hypothesis-engine.jar` by other repos that do not
carry the Scala source or `ledger-schema.sql`, so ledger setup and cleanup must
be reachable from the binary itself. `Main` gains a small subcommand dispatch:

- `init [path]` — create the SQLite DB and all seven tables at `path`, or at
  the configured ledger path (`-Dhypothesis-engine.ledger.path`, default
  `ledger.db`) when omitted. Idempotent: repeated calls succeed and never drop
  data. Prints `INIT_OK path=<resolved path>`.
- `cleanup <hypothesis-id>` — delete that hypothesis's rows from all seven
  tables (`hypotheses`, `coordinates`, `experiments`, `executions`, `evidence`,
  `calculations`, `decisions`); other hypotheses and the schema survive.
  Prints `CLEANUP_OK hypothesis=<id>`.
- `cleanup --all` — delete all rows in the same seven tables, keep the schema.
  Prints `CLEANUP_ALL_OK`.
- Anything else keeps the existing positional form
  `Main <hypothesis.json> <experiments.jsonl>` (examine), byte-for-byte.
  A subcommand token with missing/invalid arguments prints usage and exits 2.

For testability, `Main` exposes `def run(args: Array[String]): Int` returning
the exit code; `main` delegates to it and calls `System.exit` only for a
non-zero result. Tests call `run` directly so the test JVM is never killed.

The command token is matched exactly, so a real hypothesis JSON file named
`init` or `cleanup` would need `./init` to reach examine.

To respect DIP, `control.Main` dispatches through the `Ledger` port: the port
gains `initialize()`, `deleteHypothesisData(id)` and `deleteAllData()`;
`SqliteLedger` implements them (its constructor already calls the schema
bootstrap, so `initialize()` is idempotent by construction). `Main` must not
contain JDBC or SQL. `Ledger.resetStageData` stays as-is — the new cleanup
operations are explicit and call-driven, not an automatic side effect of
`execute`.

## Implementation status

Proposed. No CUs drafted; this spec is open for Designer review of the criteria
and tests below.

## Acceptance criteria

1. A new evaluator (replacing `BayesianEvaluator.update`) computes the score by
   the five-step model in "The model" above; the Beta-Binomial pseudo-count
   formula is gone and no `concentration` constant remains.
2. `ExperimentDefinition` carries `dtr_coordinates: Vector[DtrCoordinate]` with an
   empty default; the decoder accepts the field, and `HypothesisEngine.execute`
   passes experiment-to-coordinate mapping into the evaluator. Experiments
   without the field map positionally to `hypothesis.coordinates(i)`.
3. Per-coordinate evidence is bounded and diminishing: for one coordinate,
   `passes = 1, 2, 3` yield `e = 0.5, 0.75, 0.875`; `e < 1` for any finite `passes`;
   the marginal gain from an extra probe strictly decreases.
4. Coverage is the mean over **all declared coordinates**, so adding a passing
   probe on a new coordinate raises the score more than adding one to an
   already-covered coordinate, and unverified coordinates lower the score.
5. A single failing probe caps the score at `prior`, so a `prior=0.5` hypothesis
   cannot pass the `0.85` gate while any probe failed (19 passes + 1 failure is
   RED).
6. The score is strictly below `1` for any finite evidence (ceiling `0.95`), and
   an experiment set with zero probes yields exactly `prior`.
7. `ExaminationGate.decide` still yields GREEN only at or above the configured
   gate; the decision/report/ledger JSON field names and the `StageReporter`
   history shape are unchanged — only the numeric `probability` value differs.
8. The evaluator is deterministic and pure: identical inputs give identical
   scores across runs and across instances; no `Any`, no mutable shared state.
9. `Main` dispatches `init [path]`, `cleanup <hypothesis-id>` and
   `cleanup --all`; any other first argument preserves the legacy 2-argument
   examine call with unchanged output. Missing/invalid subcommand arguments
   print usage and exit 2. Exit codes come from a testable `Main.run(args): Int`
   seam; `main` only forwards non-zero results to `System.exit`.
10. `init` creates the DB and all seven schema tables at the requested path (or
    the `-Dhypothesis-engine.ledger.path` value, default `ledger.db`), is
    idempotent across repeated calls with no data loss, exits 0, and prints
    `INIT_OK path=<resolved>`.
11. The `Ledger` port declares `initialize()`, `deleteHypothesisData(id)` and
    `deleteAllData()`; `SqliteLedger` implements them; `control.Main` performs
    dispatch solely through the port type with no JDBC/SQL/DDL in the control
    layer. Port flow stays outbound → inbound.
12. `cleanup <id>` removes only that hypothesis's rows from all seven tables;
    every other hypothesis and the schema remain intact.
13. `cleanup --all` empties all seven tables while leaving the schema usable —
    a subsequent examine on the same DB succeeds.
14. The legacy `Main <hypothesis.json> <experiments.jsonl>` path is unchanged:
    same report block, same appended `.ledger-report.jsonl`, same
    `GREEN_LIGHT` / `RED_LIGHT` / `ADVISOR_ACTION_REQUIRED` terminal line.

## Tests

ScalaTest, from `hypothesis-engine/`. Each test maps to a criterion above.

- `T1` (criterion 1) — `NewEvaluatorSpec` source-level probe: `BayesianEvaluator`
  and `concentration` no longer appear in `Examination.scala`; the new evaluator
  object does. Command: `grep -c concentration hypothesis-engine/src/main/scala/hypothesisengine/application/Examination.scala` → `0`.
- `T2` (criterion 2) — decoder round-trip: an experiment JSONL line with
  `dtr_coordinates: ["TYPE.a.Domain"]` decodes to a definition carrying that
  coordinate; a line without it decodes to an empty vector and maps to
  `hypothesis.coordinates(i)` by position. `sbt -batch "testOnly hypothesisengine.ExperimentMappingSpec"`.
- `T3` (criterion 3) — `NewEvaluatorSpec`: per-coordinate evidence for
  `passes = 1,2,3` is `0.5,0.75,0.875` (±1e-9); `e < 1` for `passes = 100`; gains
  strictly decrease. `sbt -batch "testOnly hypothesisengine.NewEvaluatorSpec"`.
- `T4` (criterion 4) — with two coordinates, three passes on A + zero on B scores
  lower than three passes on A + one on B; four coordinates with only three
  covered score below the `0.85` gate. Same spec.
- `T5` (criterion 5) — 19 passing probes and one failing probe (any coordinate)
  score `<= prior` and `ExaminationGate.decide` returns `Decision.Red`. Same spec.
- `T6` (criterion 6) — score with 100 passes on a single coordinate `< 1` and
  `<= ceiling`; zero probes yields exactly `prior`. Same spec.
- `T7` (criterion 7) — `GateSpec` and `LedgerStageReportSpec` still pass; the
  report block still contains `probability_for_correctness`, `posterior`,
  `gate`, `gap`, `decision`, `history`. Commands: `sbt -batch "testOnly hypothesisengine.GateSpec hypothesisengine.LedgerStageReportSpec"`.
- `T8` (criterion 8) — evaluator is called twice with identical inputs and the
  scores are equal; `sbt -batch test` is green overall (language proof for Scala).

- `T9` (criteria 9, 10) — `CliSpec`: `Main.run(Array("init", tmpPath))` creates
  the file and seven tables (query `sqlite_master`), prints `INIT_OK`, exits 0;
  a second call is also exit 0 and leaves the tables intact. Omitted path uses
  the sysprop. `Main.run(Array("cleanup"))` and an unknown subcommand print
  usage and exit 2. Command: `sbt -batch "testOnly hypothesisengine.CliSpec"`.
- `T10` (criterion 11) — source-level probe: `control/Main.scala` contains no
  `java.sql`, `jdbc:sqlite` or `CREATE TABLE`; the `Ledger` trait declares all
  three new methods and `SqliteLedger` overrides them. Same spec or a dedicated
  `LedgerPortSpec`.
- `T11` (criterion 12) — seed two hypotheses into a temp ledger, invoke
  `cleanup <id-A>`, assert zero rows for A in all seven tables and unchanged
  row counts for B; schema tables still present. `CliSpec`.
- `T12` (criterion 13) — seed two hypotheses, invoke `cleanup --all`, assert
  all seven tables empty and `sqlite_master` still lists them; then run a real
  examine on the same DB and assert a GREEN or RED decision (not an error).
  `CliSpec`.
- `T13` (criterion 14) — regression: run the existing two-argument examine
  fixture and assert the emitted JSON block, appended report line and terminal
  status line are unchanged from the pre-change fixture. Covered by the full
  `sbt -batch test` run plus `GateSpec` / `LedgerStageReportSpec`.
