# Hypothesis conversation — feat-18-raise-hypothesis-gate-and-print-ledger Phase 2 (fat jar)

Hypothesis H-feat-18-raise-hypothesis-gate-and-print-ledger-DESIGN-FATJAR, gate 0.85, six prep probes (all true in baseline).

Invocation note: `sbt -batch runMain` failed with `No suitable driver found for
jdbc:sqlite:ledger.db` — an sbt classloader quirk (the JDBC driver registered by the
`ensureLedger` meta-build task is not visible to sbt's `runMain` app classloader). The
same engine entrypoint was run with `java -cp target/scala-3.3.3/classes:<deps>` so
ServiceLoader registers the driver in one app classloader. Engine logic, hypotheses and
evidence are unchanged; the engine alone made the decision.

## Stage 1 report block (verbatim)

{"stage":1,"hypothesis_id":"H-feat-18-raise-hypothesis-gate-and-print-ledger-DESIGN-FATJAR","statement":"Phase 2 baseline gap is real at every declared coordinate: no sbt-assembly plugin, no mainClass or assemblyJarName, no ledger-path override in Main, no jar ignore rule, and no assembly/release workflow — so the engine is source-distributed and CWD-fragile, not a downloadable self-contained jar.","dtr_coordinates":["FILE.hypothesis-engine/project/plugins.sbt","FILE.hypothesis-engine/build.sbt","FILE.hypothesis-engine/src/main/scala/hypothesisengine/control/Main.scala","FILE..gitignore"],"justification":"Advisor-authored: confirms each fat-jar acceptance criterion (13-16) is net-new work in the current baseline before any packaging CU is drafted.","probability_for_correctness":0.875,"gate":0.85,"gap":-0.025000000000000022,"decision":"GREEN","breakpoints":[],"fix":null,"history":[{"stage":1,"posterior":0.875,"gate":0.85,"decision":"GREEN"}]}

## Verdict

GREEN_LIGHT posterior=0.875 gate=0.85
