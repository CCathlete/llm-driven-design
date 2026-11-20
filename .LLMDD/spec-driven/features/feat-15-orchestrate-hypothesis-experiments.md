# feat-15: Hypothesis Engine owns the experiment execution lifecycle

- Status: Implemented
- Created: 2026-11-19
- Implemented: 2026-11-20
- Feature ID: feat-15-orchestrate-hypothesis-experiments

## Synopsis

Motivation: experiment execution today is ad hoc. A test may be a Scala spec or
a Python test, results are not captured in a durable, reproducible ledger, and
the LLM agent's role has blurred into running tests and handling evidence. This
feature builds a standalone Scala application with its own runtime: the
Hypothesis Engine is the single owner of the entire experiment lifecycle. The
agent describes *what* to execute (hypotheses, DTR coordinates, predictions,
experiment/test definitions); the engine owns execution, evidence, probability
math and the examination green-light decision. Python and Scala are pluggable
execution targets, not architectural dependencies. SQLite is linked into the
engine binary and the ledger is carried by that artifact, owned exclusively by
the engine, so results are reproducible and the mathematical steps are
deterministic.

The examination path is fail-closed. Before evaluating or passing any
hypothesis, the engine validates that the ledger contains all required data;
if it does not, the engine fails and returns a structured action-required
message telling the Advisor exactly what is missing and what correction is
needed. A green light is granted only when a hypothesis posterior reaches its
configured gate probability for correctness. On a red light the engine reports
the DTR coordinates of the breakpoints together with a suggested fix, so the
Advisor proceeds from precise, evidence-backed coordinates rather than
guesswork.

Scope (in):
- A language-independent, declarative experiment definition (JSON) accepted for
  both `python` and `scala` runtimes.
- Engine components: Experiment Scheduler, Process Adapter, Execution
  Collector, Result Normalizer, Result Validator, SQLite Repository, Ledger
  Validator, Bayesian Evaluator, Examination Gate, Remediation Reporter.
- Persist raw execution results to SQLite *before* Bayesian calculation.
- A structured examination ledger with immutable execution identity and full
  reproducibility metadata.
- A boundary that forbids any test process from writing to the examination DB.
- Fail-closed ledger completeness validation with an Advisor-directed
  remediation prompt (`AdvisorActionRequired`).
- A configured gate probability for correctness that is the sole condition for
  a green light.
- A breakpoint report carrying DTR coordinates and a suggested fix on red
  light.
- A self-contained Scala app with its own runtime: SQLite linked into the
  binary, the ledger held in/by the artifact, and a build-time flag that cleans
  the ledger for a fresh start.

Scope (out):
- The agent executing tests, writing to SQLite, or deciding the green light.
- Prescribing how a Scala or Python test is implemented internally; the engine
  supplies the command and the normalized-result contract only.
- Adding a Scala runtime dependency on Python (or vice versa) in the
  domain/application layers.
- The engine implementing or applying the suggested fix — that is Advisor/Coder
  work informed by the report.
- An external database server, a host application, or a separate DB process:
  the engine is self-hosting and the ledger is local to its artifact.
- Committing or pushing (Designer-owned).

## Implementation status

Implemented. Compiled ITR at
`.LLMDD/ITRS/feat-15-orchestrate-hypothesis-experiments/` (8 CUs, 7 waves).
All 8 CUs were implemented and committed in `6ebb72a`; the wave-runner
lead-bookkeeping fix landed in `95b2fa8`.

Two follow-up corrections were made after the wave run and verified green:
environment capture was removed from `RawResult` and the ledger end to end
(`3fdbf75`, Designer-directed — see book 9.38), and `Main.execute` was fixed to
seed hypothesis/coordinates/experiments so a fresh ledger can actually reach a
verdict (`c9e9351`, book 9.39, new `LedgerSeedingSpec`). The `prepare-implementation`
hypothesis hard gate (`7811007`, book 9.39) and the `waves-done-broken` skill
(`ef08f03`, book 9.40) build on this engine.

Verification at close: `cd hypothesis-engine && sbt -batch test` → 18/18 pass,
compile OK; a fresh-ledger CLI run prints `GREEN_LIGHT posterior=0.666… gate=0.6`
with all seven ledger tables populated.

Acknowledged deviation: criterion 8 and test 8 name `environment` as recorded
reproducibility metadata. The whole-environment map is deliberately *not*
persisted — it leaked every process secret into the ledger and its comma parser
crashed on ordinary values. `sourceRevision` (the single `SOURCE_REVISION`
provenance scalar), command, timeout and exit status are still recorded; the
Designer explicitly authorised dropping the environment blob. The static
assertion in test 8 should be read against that decision.

Known stale state: `repo.convergence.json` reads `NEEDS_ATTENTION` because it
was written by the old `run-waves.py` before the lead-bookkeeping fix; it is a
phantom breach, not unfinished work, and no re-run is required.

## Acceptance criteria

1. A declarative JSON experiment definition with fields `experiment_id`,
   `hypothesis_id`, `runtime`, `command` (argv list), `working_directory` and
   `timeout_seconds` is accepted for both `runtime: "python"` and
   `runtime: "scala"`, and the engine never inspects test internals.
2. The engine is composed of distinct single-responsibility components:
   Experiment Scheduler, Process Adapter, Execution Collector, Result
   Normalizer, Result Validator, SQLite Repository, Ledger Validator, Bayesian
   Evaluator, Examination Gate and Remediation Reporter; test runtimes are
   pluggable execution targets rather than compile-time architectural
   dependencies of the domain/application layers.
3. The Process Adapter launches both Python and Scala test processes from
   `command` + `working_directory`, enforces `timeout_seconds`, and the
   Execution Collector captures exit code, stdout, stderr and duration.
4. The Result Normalizer converts per-runtime outputs (stdout, exit code,
   structured result file) into one normalized execution-result contract; a
   non-conforming result is rejected by the Result Validator and is never
   persisted as evidence.
5. Raw execution results are persisted to SQLite before the Bayesian Evaluator
   runs; an evaluator failure leaves the raw evidence intact and retrievable.
6. The SQLite examination ledger holds distinct record types: hypotheses, DTR
   coordinate references, experiments, experiment executions, evidence,
   probability calculations and examination decisions.
7. Every execution record has an immutable execution ID and references the
   exact hypothesis, experiment definition and test artifact version.
8. Each run records reproducibility metadata: command, environment, source
   revision, timeout and exit status.
9. A test process cannot write to the examination database; the engine is the
   sole owner/writer of SQLite, and results reach it only via stdout, exit code
   or structured result files that it validates.
10. The Bayesian Evaluator updates posterior probabilities deterministically:
    the same evidence and prior always yield the same posterior, and each
    calculation is persisted as its own record.
11. The Examination Gate derives the green-light decision solely from
    engine-owned, persisted probability calculations and the configured gate
    probability for correctness (see 14); the agent supplies hypotheses, DTR
    coordinates, predictions and test definitions but cannot decide the
    outcome.
12. The boundary is structurally enforced (hexagonal/DIP): application-layer
    ports define execution and persistence, infrastructure supplies adapters,
    and no application/domain type imports a concrete test runtime.
13. Before any Bayesian calculation or examination decision, the Ledger
    Validator checks that the ledger holds all required data for the
    hypothesis: the hypothesis record and its gate probability, DTR coordinate
    references, the experiment definition (command, runtime, working directory,
    timeout, test artifact version), at least one execution with an immutable
    ID and reproducibility metadata, raw evidence, and the probability
    calculation inputs. If any datum is missing, the engine fails closed — no
    posterior update, no green light — and returns a structured
    `AdvisorActionRequired` result naming every missing datum and the
    correction needed.
14. Each hypothesis carries a configured `gate_probability_for_correctness`
    threshold. The Examination Gate grants a green light if and only if the
    ledger is complete, all evidence valid, and the persisted posterior
    probability is greater than or equal to that threshold. The threshold and
    the comparison are recorded in the examination decision, and the decision
    is deterministic.
15. On a red light, the Remediation Reporter emits an evidence-backed report
    containing (a) the hypothesis ID, posterior, threshold and gap, (b) the DTR
    coordinates of each breakpoint together with the experiment/execution ID
    and the normalized failing result that implicates it, and (c) a suggested
    fix derived from those breakpoints. The report is structured output to the
    Advisor; the engine never applies the fix itself.
16. The Hypothesis Engine is a standalone Scala application with its own
    runtime, packaged as a self-contained binary artifact (assembled fat JAR or
    native launcher) that starts by launching that binary and opening its own
    ledger. SQLite is linked into the binary — no external database server, DB
    process, host application or DB URL configuration is required. The ledger
    is held in/by the artifact: its initial schema is an embedded resource that
    the engine materializes to a writable local file resolved relative to the
    binary on first launch, and that file is the examination ledger. Python is
    required only as a pluggable execution target, never to start the engine.
17. A compilation flag (`-Dhypothesis-engine.ledger.fresh=true`, exposed as the
    build task `sbt cleanLedger`) resets the ledger for a fresh start. When set,
    the build regenerates the embedded ledger to an empty schema and removes
    any materialized local ledger file together with its `-wal`/`-shm`
    sidecars; when unset, the existing ledger is preserved. The flag is explicit
    opt-in and its use is recorded in the build output. A fresh ledger is empty,
    so criterion 13 applies: the engine fails closed and prompts the Advisor
    until hypotheses and evidence are supplied.

## Tests

Scala engine specs under `src/test/scala/` run with `sbt test` / `sbt
testOnly`; Python execution-target fixtures live under `experiments/`. Each
criterion maps to a named test (one-to-one where practical):

1. `HypothesisEngineContractSpec` — submit a python and a scala experiment
   definition; assert both accepted and no test-internal access.
2. `HypothesisEngineContractSpec` — assert the component inventory exists and
   that an application-layer source set has no import of a concrete runtime
   adapter (compile-time boundary check).
3. `ProcessAdapterSpec` — launch a trivial Python test and a trivial Scala
   test; assert exit code, stdout, stderr, duration captured and that a
   low timeout terminates the process.
4. `ResultNormalizerSpec` — normalize stdout/exit-code/structured-file outputs
   into the contract; feed a malformed result and assert Result Validator
   rejection and no evidence row written.
5. `EvidencePersistenceSpec` — persist a raw result, inject an evaluator
   failure, assert raw evidence still retrievable and that evaluator ran after
   persistence (ordering assertion).
6. `EvidencePersistenceSpec` — assert all seven ledger record types are
   writable/readable.
7. `EvidencePersistenceSpec` — assert immutable execution ID and that
   hypothesis, experiment definition and test artifact version are referenced.
8. `EvidencePersistenceSpec` — assert command, environment, source revision,
   timeout and exit status are recorded on each run.
9. `DatabaseOwnershipSpec` — hand a test process the DB path; it attempts a
   write and is prevented; assert the only accepted channels are stdout, exit
   code and a structured result file, and that the engine performs the write.
10. `BayesianEvaluatorSpec` — run the same evidence/prior twice; assert equal
    posteriors and that a probability-calculation record is persisted.
11. `ExaminationGateSpec` — flip an agent-supplied "decision" input; assert the
    green light is unchanged and is derived from persisted posteriors only.
12. `HypothesisEngineContractSpec` — static check that domain/application
    source files contain no concrete runtime or persistence imports.
13. `LedgerCompletenessSpec` — for each required datum in turn, delete or omit
    it; assert fail-closed (no posterior update, no examination decision, no
    green light) and that `AdvisorActionRequired` names the exact missing datum
    and its remediation. Assert a complete ledger passes validation.
14. `ExaminationGateSpec` — with a complete ledger, assert green light only
    when posterior >= threshold; assert threshold and comparison persisted and
    that repeating the run yields the same decision; assert incomplete ledger
    or invalid evidence never produces a green light even above threshold.
15. `BreakpointReportSpec` — with a failing hypothesis, assert the report lists
    the hypothesis ID, posterior/threshold/gap, the DTR coordinates of each
    breakpoint with its execution ID and normalized failing result, and a
    non-empty suggested fix derived from them; assert the engine made no code
    change.
16. `StandaloneRuntimeSpec` — build the artifact, launch it with no external DB
    service, no host app and no Python on PATH; assert it starts and opens its
    own ledger; assert the embedded ledger resource exists in the artifact and
    is materialized to a writable file next to the binary; assert no DB URL
    configuration is needed.
17. `FreshLedgerFlagSpec` — seed a ledger with a hypothesis and evidence, run
    `sbt cleanLedger` (equivalently with the flag set), assert the embedded
    ledger is regenerated to an empty schema and the local ledger plus sidecars
    are removed; assert the next launch has zero hypotheses and that criterion
    13 produces `AdvisorActionRequired`; assert that omitting the flag preserves
    the ledger.

Commands (expected: all pass, zero test processes write to SQLite):
- `sbt test`
- `sbt testOnly HypothesisEngineContractSpec ProcessAdapterSpec ResultNormalizerSpec`
- `sbt testOnly EvidencePersistenceSpec DatabaseOwnershipSpec BayesianEvaluatorSpec ExaminationGateSpec`
- `sbt testOnly LedgerCompletenessSpec BreakpointReportSpec`
- `sbt testOnly StandaloneRuntimeSpec FreshLedgerFlagSpec`
- `sbt cleanLedger` (fresh-start build task; verify ledger removed)

Language note: implementation language is Scala, so the `ARCH.PYTHON.SCOPE`
discipline and its `mypy --strict` probe do not apply to engine code. Python
files here are disposable execution-target fixtures. If any authored Python
source beyond fixtures is introduced, add `uvx mypy --strict <touched-files>`
and require zero errors.
