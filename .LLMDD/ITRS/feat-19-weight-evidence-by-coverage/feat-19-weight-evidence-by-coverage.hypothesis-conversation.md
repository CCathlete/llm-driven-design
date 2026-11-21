# Hypothesis conversation — feat-19-weight-evidence-by-coverage

Hypothesis H-feat-19-weight-evidence-by-coverage-DESIGN, gate 0.85, nine prep probes (all true in baseline).

Invocation note: `sbt -batch runMain` failed with `No suitable driver found for
jdbc:sqlite:ledger.db` — the known sbt classloader quirk recorded in the feat-18
conversation record. The same engine entrypoint was run via
`java -cp "target/scala-3.3.3/classes:<deps>" hypothesisengine.control.Main ...`
from `hypothesis-engine/` so ServiceLoader registers the JDBC driver in one app
classloader. Engine logic, hypotheses and evidence are unchanged; the engine alone
made the decision.

## Stage 1 report block (verbatim)

{"stage":1,"hypothesis_id":"H-feat-19-weight-evidence-by-coverage-DESIGN","statement":"The baseline probability score is a probe-count Beta-Binomial (not evidence coverage), experiment definitions carry no coordinate mapping, the Ledger port exposes no lifecycle operations, and Main has no init/cleanup subcommand; each gap is localized to the declared coordinates and the report/gate contract is stable, so the feature is net-new work at these coordinates.","dtr_coordinates":["FILE.hypothesis-engine/src/main/scala/hypothesisengine/application/Examination.scala","FILE.hypothesis-engine/src/main/scala/hypothesisengine/control/Main.scala","FILE.hypothesis-engine/src/main/scala/hypothesisengine/domain/Model.scala","FILE.hypothesis-engine/src/main/scala/hypothesisengine/application/Ports.scala","FILE.hypothesis-engine/src/main/scala/hypothesisengine/infrastructure/SqliteLedger.scala","FILE.hypothesis-engine/src/main/resources/ledger-schema.sql"],"justification":"Advisor-authored: each probe confirms one acceptance criterion (1-14) is unmet in the current baseline before any CU is drafted, at the exact files the DTR extracted.","probability_for_correctness":0.9090909090909091,"gate":0.85,"gap":-0.05909090909090908,"decision":"GREEN","breakpoints":[],"fix":null,"history":[{"stage":1,"posterior":0.9090909090909091,"gate":0.85,"decision":"GREEN"}]}

## Verdict

GREEN_LIGHT posterior=0.9090909090909091 gate=0.85
