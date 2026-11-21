# bug-10: CU scope boundaries are declared but never enforced

- Status: Resolved
- Created: 2026-10-18
- Resolved: 2026-11-20
- Bug ID: bug-10-cu-scope-boundaries-not-enforced

## Bug detail

Symptoms: a CU declares its blast radius twice — the paths it writes
(F1, "exact file path") and the `Do NOT touch:` list — but neither
declaration is ever checked against what the Coder actually changes. The
boundary is prose that no gate parses and no runner compares. A Coder
that drifts outside its scope produces a green wave.

Three independent gaps, each individually sufficient to let drift through.

**Gap 1 — the validator checks that a no-touch list exists, never what is
in it.** `.LLMDD/tools/validate-steps.py` F4 is two substring tests:

```python
if "Do NOT touch:" not in body:
    fails.append("F4: missing Do NOT touch list")
```

The list is never parsed, never normalised, and never intersected against
the paths the steps declare. An empty list, a list naming the very file
the CU writes, or a list of pure prose all pass F4 identically. The
validator also has no allowlist concept: scope is inferred from prose
paths inside step bodies rather than from an explicit declaration.

**Gap 2 — the no-touch lists in practice are unmatchable prose.** The
shipped frames for bugfix-feature-5 lean on exactly the exclusions a
simple Coder is most likely to violate:

```
cu-001  Do NOT touch: ...coder.md, ...verifier.md, opencode.json,
        the sys-prompt tensor, any Scala source, any Python tool.
cu-003  Do NOT touch: ...advisor.md (both twins), coder.md, verifier.md,
        opencode.json, the sys-prompt tensor, any Scala source,
        any ITR frame or feedback file.
```

"any Scala source", "any Python tool" and "any ITR frame or feedback
file" are not paths. No mechanical matcher can test them, so the clauses
that carry the most weight are the ones enforcement cannot reach.

**Gap 3 — the runner measures drift and discards the measurement.**
`.LLMDD/tools/run-waves.py` already collects the changed-file list per CU
(`files_changed`, populated from the diff at line 1127) and threads it
through to reporting (lines 1186, 1202, 1428, 1458, 1501). It is never
compared to anything — there is no intersection, no subset assertion, no
deny path. Grepping the runner for `no_touch`, `scope_files`,
`allowed_paths` or any declared-scope reference returns nothing. The one
place CU scope is stated to a Coder is a prompt string at line 1018,
`f"Your scope is EXACTLY one CU: {cu_id}. Nothing else."` — advisory text,
the same class of control as bug-8.

Reproduction (from repo root, read-only):

````sh

# Gap 1 - a CU whose no-touch list names its own write target passes F4.
cat > /tmp/opencode/cu-bad-steps.txt <<'CU'
1. Edit .LLMDD/tools/x.py

```python
x = 1
```

Prove: `python3 -m py_compile .LLMDD/tools/x.py`

Do NOT touch: .LLMDD/tools/x.py, any Scala source
Stop when green.
CU
python3 -c "import json,pathlib; s=pathlib.Path('/tmp/opencode/cu-bad-steps.txt').read_text(); print(json.dumps({'cus':[{'cu-id':'cu-bad','content':'# IMPLEMENTATION_STEPS:\n'+s+'# ACCEPTANCE:\n1. x is 1\n'}]}))" > /tmp/opencode/batch.json
python3 .LLMDD/tools/validate-steps.py /tmp/opencode/batch.json
# EXPECTED: FAIL - the CU lists its own write target as untouchable
# ACTUAL:   PASS cu-bad, exit 0 - F4 tests substring presence, never parses

# Gap 2 - the real frames' most important exclusions are not paths
grep -h "Do NOT touch:" .LLMDD/ITRS/bugfix-feature-5-*/cu-*/IMPLEMENTATION_STEPS.itr \
  | grep -oE "any [A-Za-z ]+"
# ACTUAL: "any Scala source", "any Python tool",
#   "any ITR frame or feedback file" - unmatchable

# Gap 3 - the runner records changed files and never checks them
grep -n "files_changed" .LLMDD/tools/run-waves.py | tail -3
# ACTUAL: populated at 1127, forwarded to reporting; no comparison anywhere
grep -cE "no_touch|scope_files|allowed_paths" .LLMDD/tools/run-waves.py
# ACTUAL: 0
````

Evidence that drift has already occurred under this gate:

- `cu-001` feedback: the sys-prompt tensor "carries a pre-existing
  modification not made or touched by this session (cu-002 scope)". A
  file explicitly on cu-001's no-touch list was dirty in the working tree
  while cu-001 ran. The Coder noticed and narrated it; nothing failed.
- `cu-002` feedback: "both cu-002 edits were already present in the
  working tree from a prior interrupted attempt (attempt-0/attempt-1
  logs)". State from an aborted attempt leaked into a later run with no
  gate noticing.

Both waves reported STATUS=COMPLETED and verification green.

All three reproductions above were executed against this checkout on
2026-10-18; Gap 1 prints `PASS cu-bad` and exits 0, confirming a
self-contradictory no-touch list is accepted. (First draft of the Gap 1
probe omitted the `IMPLEMENTATION_STEPS:` / `ACCEPTANCE:` markers the
batch path requires, so the validator read an empty body and failed for
the wrong reason - a reminder that a reproduction must be run before it is
recorded, not merely written.)

Environment: repo checkout at 397334b on `llmdd-v9`; identical content on
`origin/main`. Python 3.13, Linux.

Suspected cause: scope was treated as a documentation requirement rather
than a constraint to be computed. The validator was designed to police
step *shape* (F1 paths, F2 fences, F3 proofs, F5 vague verbs, F6
placeholders) and scope slipped through as one more prose requirement. The
no-touch list is free-text because nothing has ever needed to match it.
Meanwhile the runner collected `files_changed` for reporting before
anything needed it for enforcement, and the reporting use case locked in
the shape.

Impact: the CU is the unit of coordination in the whole design — it
carries DTR coordinates, severity, acceptance tests and the no-touch
boundary that keeps parallel waves from colliding. A boundary that is not
checked is not a boundary. With N CUs in a wave running in parallel
against one working tree, an undetected out-of-scope edit is attributed to
the wrong CU, can silently satisfy another CU's acceptance test, or
corrupt a sibling's diff before either is committed. This is the drift
risk that grows as waves get wider and CUs get smaller — exactly the
regime the global spec-id work (feat-6) pushed toward.

Note: `bug-9` is a burned serial. The allocator is a high-water mark, so a
verification call issued it and no file was created. `--check` exits 0
(ahead-of-tree is safe), but the sequence skips 9.

Remediation direction (not a design decision — for a future
bugfix-feature): make scope a declared, parsed structure rather than
prose. (1) Give each CU an explicit machine-readable allowlist — the set
of paths it may write — replacing path inference from step prose.
(2) Parse the `Do NOT touch:` list into the same path vocabulary and
assert at design time that allowlist and no-touch are disjoint, which
catches self-contradictory CUs before compilation. (3) Convert the
prose clauses ("any Scala source") into glob patterns so they become
matchable. (4) At run time, compare the `files_changed` the runner
already computes against the declared allowlist and fail the CU on any
file outside it — this is the smallest change with the largest effect,
because the measurement already exists. (5) Reject when the working tree
is dirty in another CU's declared paths at CU start, which is the
cu-001/cu-002 contamination above.

## Resolution status

Resolved 2026-11-20. All three gaps are closed in the shipped tree, and the Phase 2 stale-baseline variant of Gap 3 (phantom `NEEDS_ATTENTION` from earlier-wave writes) is closed as well — see Phase 2 below and the linked feature's Phase 2 section.

## Fix summary (Resolved only)

Delivered by `bugfix-feature-7-enforce-cu-boundaries` (Implemented,
`CONVERGED`): commits `fa50684` (initial boundary enforcement), `bc0573d`
(cu-001..cu-008: boundary vocabulary, no-touch enforcement, dirty-tree
refusal, scope vocabulary hardening, per-CU change attribution, per-wave
boundary enforcement, lead-gate rule, escalation cleanup), `4be0d3a` /
`dcb364d` (attribution of correction logs), `15dced6` (correction pass and
unattributed lead context) and `95b2fa8` (exclude runner/lead bookkeeping
paths from no-touch checks).

- Gap 1: `validate-steps.py` now parses `Do NOT touch:` via the shared
  `.LLMDD/tools/cu_scope.py` vocabulary and fails a CU whose no-touch set
  intersects its own declared write paths (F7), naming both sides.
- Gap 2: prose clauses resolve to matchable globs; `cu_scope.resolve_patterns`
  expands directories and symlinks, and unenforceable lists fail closed.
- Gap 3: `run-waves.py` attributes changes per CU (own tool_use writes,
  wave diff and untracked files minus the per-wave baseline) and enforces
  three rules — own no-touch, cross-CU shared-write, and unattributed
  changes against every no-touch in the wave — plus a pre-flight dirty-path
  refusal and a gate on leads.

## Tests proving resolution (Resolved only)

Live re-run on 2026-11-20 from the repo root:

- Original bug-10 Gap 1 reproduction (CU listing its own write target as
  untouchable) now prints `FAIL cu-bad / F7: no-touch list contains this CU
  own write target: .LLMDD/tools/x.py`, exit 1. Previously `PASS cu-bad`,
  exit 0.
- `uvx pytest -q .LLMDD/tests/test_cu_boundary.py` — 24 passed
  (`.LLMDD/tests/test_cu_boundary.py`, regression suite for measurement and
  enforcement: parse, glob resolution, attribution, all three breach rules,
  fail-closed on unenforceable lists).
- `bugfix-feature-7` convergence: `CONVERGED`, all waves `coder_status=0`,
  zero escalations.

Designer-approved exception for test T5: the original suggested `mypy --strict`
probe was skipped at the first close, at which point it reported one `no-redef`
(`run_written` inside the wave loop). Phase 2 criterion 9 removed that redundant
re-annotation, and a live re-run on 2026-11-21 reports `Success: no issues found`
on `run-waves.py`. The exception is now moot; the strict probe is clean.


## Phase 2 resolution — phantom NEEDS_ATTENTION from earlier-wave writes (2026-11-21)

The Gap 3 family had one surviving variant: a clean wave run (all CUs
`COMPLETED`/`PASSED`, zero escalations) still ended `NEEDS_ATTENTION` because
`wave_writes` measured earlier-wave writes against a stale `stash_ref` and
attributed them as `unattributed:<cu>` breaches in later waves. Delivered by
`bugfix-feature-7` Phase 2 (cu-009), commit `a366731`.

Fix: thread the run-level accumulated written-paths set (`run_written`) into
`wave_writes`, `enforce_boundary` and `run_wave_parallel`, subtract it from
the measured wave change set, pass `frozenset(run_written)` at the four
enforcement call sites in `main_async`, and remove the redundant
`run_written` re-annotation that had been the outstanding `mypy` no-redef.

Tests proving Phase 2 resolution (live re-run 2026-11-21):

- `TestPriorWrittenExclusion.test_wave_writes_drops_prior_written` and
  `.test_enforce_boundary_ignores_prior_written_guard` — both pass.
- `python3 -m unittest discover -s .LLMDD/tests -p test_cu_boundary.py` —
  `Ran 26 tests ... OK`.
- `python3 .LLMDD/tools/validate-steps.py .LLMDD/ITRS/bugfix-feature-7-enforce-cu-boundaries.phase2.batch.json` — `PASS cu-009`.
- `uvx mypy --strict .LLMDD/tools/run-waves.py` — `Success: no issues found in 1 source file`.
- Source audit — `prior_written` in all four enforcement call sites; single
  `run_written: set[str]` binding (no no-redef); `ast.parse` clean.

Artifact caveat: `repo.convergence.json` for the phase-2 wave still reads
`NEEDS_ATTENTION` with `coder_status: 1`. This was emitted by the pre-fix
in-memory runner and is itself the phantom this phase fixes — a single-wave
fix run cannot self-correct its own status artifact. Designer decided on
2026-11-21 to close on the live green evidence (26/26 tests, all CU and lead
feedback PASS, zero escalations) and record this discrepancy rather than
re-dispatch a coder. A fresh run of the now-fixed runner would be expected
to write `CONVERGED`.
