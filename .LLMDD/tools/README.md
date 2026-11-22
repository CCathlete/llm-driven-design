# .LLMDD/tools

Repo-agnostic tooling carried by the .LLMDD standard. Every tool derives
the repository root from its own location, so the folder works unchanged
in any repository that carries a .LLMDD folder.

## next-spec-id.py

Allocates the next bug or feature serial for this repository. It reads
the plain-text counter at `.LLMDD/spec-driven/ids.txt` plus every git ref
under `refs/heads` and `refs/remotes`, so two branches cut from the same
state of dev never receive the same number.

```sh
python3 .LLMDD/tools/next-spec-id.py --kind bug
python3 .LLMDD/tools/next-spec-id.py --kind feature --prefix bugfix-feature-
python3 .LLMDD/tools/next-spec-id.py --kind feature --dry-run
python3 .LLMDD/tools/next-spec-id.py --check
```

`--kind` prints the id and bumps the counter. `--prefix` chooses the id
lead, so a bug or bugs fix prints `bugfix-feature-<n>-` from the feature
sequence. `--dry-run` prints without writing. `--check` never writes and
fails on a duplicate serial, on an artifact directory with no matching
spec file, or on a counter behind a serial present in the tree.

Exit codes follow the convention shared with `check-advisor-approval.py`:
0 clean or id printed, 1 check violation, 2 usage error.

## check-advisor-approval.py

Validates a Designer-issued approval token against a proposed change.
Exit 0 allows, 1 denies, 2 is a usage error.

## hypothesis-engine.jar

Fat jar for the Hypothesis Engine — the ledger-backed stage evaluator used by
the `@hypothesize` skill and by `prepare-implementation` step 3. Built from the
Scala project in `hypothesis-engine/`; it embeds the sqlite JDBC driver and the
ledger schema, so no classpath or driver setup is needed. The jar is
gitignored (built artifact), so rebuild it locally after changing engine
source:

```sh
cd hypothesis-engine && sbt -batch assembly
# writes ../.LLMDD/tools/hypothesis-engine.jar
```

Verify the shipped CLI:

```sh
javap -p -cp .LLMDD/tools/hypothesis-engine.jar 'hypothesisengine.control.Main$' \
  | grep -E 'runInit|runCleanup'
```

### Commands

```sh
# Examine: evaluate hypotheses against experiments (the normal gate run)
java -jar .LLMDD/tools/hypothesis-engine.jar \
  <hypotheses.json> <experiments.jsonl>

# Initialise an empty ledger at a path
java -jar .LLMDD/tools/hypothesis-engine.jar init [path]
# -> INIT_OK path=<absolute path>

# Clean ledger data for one hypothesis, or all of it
java -jar .LLMDD/tools/hypothesis-engine.jar cleanup <hypothesis-id>
java -jar .LLMDD/tools/hypothesis-engine.jar cleanup --all
# -> CLEANUP_OK hypothesis=<id> / CLEANUP_ALL_OK
```

With no subcommand the jar runs the examine path. The ledger location defaults
to `ledger.db` and can be overridden with `-Dhypothesis-engine.ledger.path=...`.
`sbt -batch cleanLedger` is a separate build task that resets the source
project's `ledger.db` for a fresh start; it is not a jar subcommand.

The verdict report is printed as one JSON block and appended byte-identically
to `<feature>.ledger-report.jsonl`. `check-hypothesis-report.py` (same folder)
enforces the verbatim-echo hard gate; see `.LLMDD/skills/hypothesize/SKILL.md`
for the full procedure, artifact paths and stop boundary.
