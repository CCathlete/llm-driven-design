# feat-9: Enforce CU boundary correction pass

- Status: Proposed
- Created: 2026-10-22
- Feature ID: feat-9-enforce-cu-boundary-redirect

## Synopsis

Motivation: the post-hoc enforcement now detects boundary violations, but when a coder goes out of scope it currently has no path back to alignment. We do not want to terminate the coder session or route to the code lead by default. We want to prompt the original coder to correct only the out-of-scope changes while keeping its in-scope work intact. 

Scope: In — correction pass for the original coder (same model, no fallback rotation), up to 3 retries per breached CU, re-measure after each attempt, inject only the relevant breach details into the correction prompt, escalate to the lead only if it fails to comply after retries or for unattributed writes. Out — changing attribution, changing the three enforcement rules, using fallback models for scope corrections.

## Acceptance Criteria

- [ ] When a CU has owned or cross-CU shared-write breaches (and no unrecoverable failure), the runner does NOT write `STATUS=ESCALATION`. It runs a correction pass against the original coder.
- [ ] Correction passes use the same model as the coder that produced the breach. They do not consume or increment the coder fallback chain. Fallbacks remain only for "coder failed to respond/start" cases.
- [ ] Up to 3 correction attempts per breached CU. Each attempt produces new logs (e.g. `cu-XXX.correction-N.log`) which are included by `coder_writes()`.
- [ ] After each correction attempt, boundaries are re-measured from logs and the working tree diff. If breaches are resolved, stop correcting that CU.
- [ ] If breaches persist after 3 correction attempts, escalate that CU to the per-CU lead with the full breach history injected into the lead prompt.
- [ ] Unattributed breaches (Rule 3) have no owner. They are not sent to any coder for correction; they are routed to the wave lead with explicit instructions and relevant paths, or the wave fails fast with actionable guidance.
- [ ] Correction prompts are minimal and targeted: list only the violating paths, the rule, and instruct "fix ONLY these violations, do not change in-scope files unnecessarily, revert or move out-of-scope changes to stay within your CU".
- [ ] `boundary-breaches.txt` is written and injected into correction/lead prompts as appropriate. Existing coder feedback is not clobbered.
- [ ] The run does not break. If all breaches are corrected, the wave can proceed normally.

## Tests

- `test_cu_boundary.py` (extend) — same-model correction pass, 3 retry cap, no fallback usage, re-measure after correction, unattributed routes to wave lead, escalation only after cap.
