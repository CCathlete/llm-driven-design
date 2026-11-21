# bugfix-feature-20: Fix run-waves phantom NEEDS_ATTENTION

- Status: Implemented
- Created: 2026-11-21
- Feature ID: bugfix-feature-20-fix-run-waves-phantom-detection

## Synopsis

Motivation: `run-waves.py` reports `status: NEEDS_ATTENTION` with
`coder_status: 1` for waves in which every coder passed, no escalation was
opened, the lead itself recorded convergence, and the acceptance suite is
fully green. The flag is produced by folding a *lead* boundary repair into the
`all_passed` boolean that is later written as *coder_status*. Because the lead
is explicitly mandated to repair cross-CU defects — including writing paths in
a CU's `Do NOT touch:` list — this is a legitimate repair being mislabeled as a
coder failure. It is deterministic (will not clear on re-run) and has already
caused two false `waves-done-broken` re-entries on
`feat-19-weight-evidence-by-coverage`. `bug-14` is Open against exactly this
and is the only bug this feature covers.

Scope: In — `.LLMDD/tools/run-waves.py` convergence bookkeeping, specifically
(a) stop deriving `coder_status`/`lead_status` from the lead boundary check,
(b) add an explicit, machine-visible `lead_repairs` field to each wave record
so real lead repairs are still *detected and recorded* without failing the
wave, and (c) extract the final-status decision into a pure, testable function.
Out — redefining what counts as a coder failure or an escalation, changing CU
no-touch semantics, the `report_breaches` feedback file format, any compiled
ITR frame or runner runtime state, and any Scala source.

### Intended end state

1. The lead boundary branch records the repaired paths (via the existing
   `report_breaches` call, plus a `lead_repairs` list carried into the wave
   record) but does **not** assign to `all_passed`, `coder_ok`, or `lead_ok`.
2. `coder_status` reflects only coder outcomes; `lead_status` reflects only
   the lead process exit code.
3. Final status is `CONVERGED` when there are no escalations and no wave has
   `coder_status != 0` or `lead_status != 0`, regardless of `lead_repairs`.
4. A genuinely failing coder (expected-failure path) still yields
   `coder_status: 1` and `NEEDS_ATTENTION` — detection of real failures is
   preserved and strengthened, not removed.

## Acceptance criteria

- **T1** — lead boundary repair no longer flips the coder-status boolean. Prove:
  `python3 .LLMDD/tests/bugfix-feature-20/probe.py t1`, expecting exit 0 and
  `passed=true` (the lead-breaches branch contains no `all_passed = False`
  assignment).
- **T2** — lead repairs are recorded, not discarded. Prove:
  `python3 .LLMDD/tests/bugfix-feature-20/probe.py t2`, expecting exit 0 and
  `passed=true` (a `lead_repairs` field is written into the wave record by
  `update_convergence`, and `boundary-breaches.txt` is still written).
- **T3** — final status is a pure, testable function. Prove:
  `python3 .LLMDD/tests/bugfix-feature-20/probe.py t3`, expecting exit 0 and
  `passed=true`.
- **T4** — the bug-14 scenario converges. Prove:
  `python3 .LLMDD/tests/bugfix-feature-20/probe.py t4`, expecting exit 0 and
  `passed=true` (a wave with escalations=0, coder_status=0, lead_status=0 and
  a non-empty `lead_repairs` list yields `CONVERGED`).
- **T5** — real failures are still detected. Prove:
  `python3 .LLMDD/tests/bugfix-feature-20/probe.py t5`, expecting exit 0 and
  `passed=true` (a wave with coder_status=1 still yields `NEEDS_ATTENTION`).
- **T6** — regression suite green and no collateral damage. Prove:
  `python3 .LLMDD/tests/bugfix-feature-20/test_convergence_status.py` exits 0
  with all tests passing; `python3 .LLMDD/tools/run-waves.py --help` exits 0;
  `git diff --stat` shows only `.LLMDD/tools/run-waves.py` and the new
  `.LLMDD/tests/bugfix-feature-20/` files against the pre-wave ref.

## Tests

T1–T5 are the standalone probe script; T6 is the focused regression suite plus
the boundary/help smoke checks. All are re-run live by `review-implementation`.


## Implementation status

Implemented (2026-11-21). Two waves converged at the CU level (both
`STATUS=COMPLETED`, `VERIFICATION_RESULT=PASSED`, zero escalations) and the
implementation landed in wave commit `cb65ca2`: cu-001 made the production fix
in `.LLMDD/tools/run-waves.py` (record lead repairs in `lead_repairs`, stop
folding them into `all_passed`, pure `final_status` for terminal status);
cu-002 added the six-test regression suite. Verified live by
`review-implementation`: T1–T5 probes passed, T6 suite 6/6, `validate-steps.py`
PASS for both CUs, AST parse and `--help` exit 0, tree clean after read-only
probes. bug-14 is Resolved.

Caveat — the run's own `repo.convergence.json` reads NEEDS_ATTENTION
(wave 1 `coder_status=1`, no `lead_repairs` field). This is expected and not a
failure of this feature: the runner process was loaded from disk before cu-001
edited it, so both wave records were written by the OLD code — the absence of
the `lead_repairs` field is proof. The specific flag came from cu-001's six
coder model-fallback attempts: an early non-zero rc set the old runner's sticky
`all_passed=False`, which no later successful attempt cleared. That is a
separate out-of-scope runner defect (sticky coder status across fallback
rotations), not the lead-repair defect this feature fixed. Live proof the fix
works: applying the new `final_status` to a wave with coder=0/lead=0/esc=0 plus
a recorded lead repair returns CONVERGED (T4), and the six regression tests
confirm real failures still yield NEEDS_ATTENTION (T5). Follow-up: open a
separate bug for the sticky-coder-status path if real detection is to be
complete.
