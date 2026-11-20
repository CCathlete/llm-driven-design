# bugfix-feature-17: Fix final-wave detection in run-waves.py

- Status: Proposed
- Created: 2026-11-20
- Feature ID: bugfix-feature-17-fix-final-wave-commit

## Synopsis

Motivation: bug-11 (`bug-11-final-wave-never-commits-off-by-one-wave-number`).
`run-waves.py` decides whether a wave is the last of a run by comparing the
wave's *label* against `len(waves)`. Those integers coincide only when labels
run `1..N` consecutively. When a run uses a corrective wave numbered above the
count (the common repair case — a single wave numbered 8, or a pair 7,8), no
wave is told to commit. The run still reports `CONVERGED` with every CU green
and zero escalations, yet the whole implementation is left dirty in the working
tree. This was observed live in `newspipe` feat-8 waves 7 and 8, where wave 8
finished with "Not committed - Per instructions, final wave lead will commit"
and left six files uncommitted while convergence read `CONVERGED`. The fix is to
compare against the last wave's *label*, which preserves the intended
one-consolidated-commit-per-run design and leaves intermediate-wave deferral
untouched.

Scope: In — the `is_final` computation in `.LLMDD/tools/run-waves.py`
(the `wave == num_waves` test at ~line 1216 and the `num_waves = len(waves)`
binding at ~line 1628) and regression tests for arbitrary wave labellings.
Out — the one-commit-per-run model itself, per-wave committing, the lead-wave
prompt text beyond the final/non-final decision, the boundary-enforcement code
in `cu_scope.py` / `validate-steps.py`, every other tool, and all compiled
frames. The separate durability gap (an interrupted multi-wave run leaves work
uncommitted by design) is explicitly not this feature.

## Implementation status

Proposed. No CUs assigned, no fix merged. Intended to close
`bug-11-final-wave-never-commits-off-by-one-wave-number` once waves are done and
E2E verify is green. ITR directory
`.LLMDD/ITRS/bugfix-feature-17-fix-final-wave-commit/`.

## Acceptance criteria

1. `is_final` is true for exactly the last wave of a run, identified by the
   last wave's label, for labellings `[1,2,3]`, `[8]`, `[7,8]` and `[1,2,8]`.
2. An end-to-end `--dry-run` over a single corrective wave numbered above
   `len(waves)` emits the commit instruction in the lead prompt and not the
   "Do NOT commit - the final wave lead will commit" deferral.
3. A non-final wave still defers — the fix cannot be implemented by always
   committing or by dropping the final/non-final distinction.
4. `uvx mypy --strict .LLMDD/tools/run-waves.py` reports zero errors on the
   touched file (annotation presence does not imply type agreement).
5. The CU boundary regression suite stays green (24 passed), so the fix does
   not disturb the enforcement code in the same tool.

## Tests

- `T1` (criterion 1, table-driven): for each labelling `[1,2,3]`, `[8]`,
  `[7,8]`, `[1,2,8]`, assert the computed `is_final` is true for exactly the
  last label and false for every earlier wave. Prove by a unit test added to
  `.LLMDD/tests/` that imports or reproduces the runner's final-wave decision
  via the same expression, run with `uvx pytest -q .LLMDD/tests/<file>.py`.
- `T2` (criterion 2, e2e dry-run): build a waves file with a single wave
  numbered 8, run
  `python3 .LLMDD/tools/run-waves.py --itr <tmp> --app repo --waves <waves.json> --dry-run`,
  and assert the emitted lead prompt contains the commit instruction and does
  not contain the deferral string. Exit 0.
- `T3` (criterion 3, negative guard): in a multi-wave labelling, assert every
  wave except the last still receives the "Do NOT commit" deferral — proving
  the fix is not an unconditional-commit shortcut.
- `T4` (criterion 4, strict typing): `uvx mypy --strict .LLMDD/tools/run-waves.py`
  → zero errors. (Note: the currently observed `no-redef` of `run_written`
  must be resolved as part of this feature so the probe is genuinely clean.)
- `T5` (criterion 5, no regression): `uvx pytest -q .LLMDD/tests/test_cu_boundary.py`
  → 24 passed.
