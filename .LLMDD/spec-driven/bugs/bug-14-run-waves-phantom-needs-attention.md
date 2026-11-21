# bug-14: run-waves reports NEEDS_ATTENTION when the code lead repairs a cross-CU defect

- Status: Open
- Created: 2026-11-21
- Resolved: -
- Bug ID: bug-14-run-waves-phantom-needs-attention

## Bug detail

**Symptom.** `run-waves.py` writes `status: NEEDS_ATTENTION` with
`coder_status: 1` for a wave in which every coder succeeded, no escalation was
open, and the code lead itself recorded convergence and a fully green test
suite. The flag is produced by a lead boundary repair being folded into the
coder-status boolean, so it is deterministic and will not clear on re-run. It
has now produced two false alarms on `feat-19-weight-evidence-by-coverage`
(the second after a dedicated fix cycle), each forcing a design-level
`waves-done-broken` re-entry before live evidence showed the feature was done.

**Reproduction (commands + observed output, all run 2026-11-21 from repo root).**

1. Read the terminal state of the fix cycle:

   ```sh
   python3 -c "import json;d=json.load(open('.LLMDD/ITRS/feat-19-weight-evidence-by-coverage-fix/repo.convergence.json'));print(d['status'],d['waves'][0]['coder_status'],d['waves'][0]['lead_status'],d['waves'][0]['escalations'])"
   ```
   Actual: `NEEDS_ATTENTION 1 0 0`.
   Expected: `CONVERGED 0 0 0` — no coder failed, no escalation was open.

2. Read the per-CU feedback (the substance the flag should reflect):

   ```sh
   for f in .LLMDD/ITRS/feat-19-weight-evidence-by-coverage-fix/repo.feedback/cu-*.feedback.txt; do grep -E '^STATUS=|^VERIFICATION_RESULT=' "$f"; done
   ```
   Actual: `STATUS=COMPLETED / VERIFICATION_RESULT=PASSED` for cu-001 and
   `STATUS=VERIFIED / VERIFICATION_RESULT=PASSED` for cu-002.
   Expected: consistent with `CONVERGED`.

3. Re-run the acceptance suite independently:

   ```sh
   (cd hypothesis-engine && sbt -batch test)
   ```
   Actual: `Tests: succeeded 35, failed 0, canceled 0, ignored 0, pending 0`,
   exit 0. The lead's own `repo.feedback/wave-1-verification.txt` ends with
   `Convergence: ACHIEVED (max iterations: 3, actual iterations: 1)`.

4. Read the recorded trigger:

   ```sh
   cat .LLMDD/ITRS/feat-19-weight-evidence-by-coverage-fix/repo.feedback/boundary-breaches.txt
   ```
   Actual: `SCOPE=lead-wave-1 RULE=lead:cu-001,cu-002
   PATH=hypothesis-engine/src/main/scala/hypothesisengine/infrastructure/SqliteLedger.scala`.
   The lead repaired `resetStageData` from a no-op to clear evidence between
   stages — a genuine cross-CU defect fix inside the Code Lead's stated
   mandate ("repairs cross-CU issues"), affecting a path the CUs listed in
   `Do NOT touch:`.

**Suspected cause (observed in the installed source, `.LLMDD/tools/run-waves.py`).**

- `update_convergence` (line ~1626) writes
  `coder_status = 0 if coder_ok else 1` and
  `lead_status = 0 if lead_ok else 1` from separate arguments.
- In the wave loop, the `coder_ok` argument is actually `all_passed`, a single
  boolean that starts `True`. The code-lead boundary check (line ~2434) does:

  ```python
  if lead_breaches:
      report_breaches(lead_breaches, feedback_dir)
      all_passed = False
  ```

  so a **lead** boundary repair flips the boolean later used as **coder_status**.
- Final status (line ~2492) is `CONVERGED` only when
  `total_escalations == 0 and failed_waves == 0`, where `failed_waves` counts
  `coder_status != 0 or lead_status != 0`. The conflated `coder_status=1`
  therefore forces `NEEDS_ATTENTION`.
- `lead_breaches` is computed by applying each non-escalated CU's `Do NOT
  touch:` guard to the lead's own edits. A lead repair of a cross-CU defect is
  indistinguishable here from a violation, even though the lead is explicitly
  supposed to make such repairs.

**Environment.** `llm-driven-design` at HEAD `76de45c` (bug opened right after
the feat-19 review closed the spec as Implemented). Python tool at
`.LLMDD/tools/run-waves.py`; Scala harness under `hypothesis-engine/`
(Scala 3.3.3, sbt). Runner state lives in the gitignored
`.LLMDD/ITRS/<feature>/repo.{convergence.json,feedback/,logs/}`.

**Impact.** Phantom NEEDS_ATTENTION misdirects the workflow into repeated
`waves-done-broken` redesign cycles on code that is correct and green, wastes
designer attention, and erodes trust in the convergence signal. Because the
flag is derived deterministically from runner logic, waiting or re-running
never clears it — only a runner fix does.

## Resolution status

Open. Not resolved. No fixing commit yet; the feature is still reproducible as
described above. Move to Resolved only when the fix is merged *and* the
proving tests below pass.

## Fix summary (Resolved only)

Pending.

## Tests proving resolution (Resolved only)

Pending.
