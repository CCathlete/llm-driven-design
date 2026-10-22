# feat-9: Enforce CU boundary redirect on violation

- Status: Proposed
- Created: 2026-10-22
- Feature ID: feat-9-enforce-cu-boundary-redirect

## Synopsis

Motivation: the post-hoc enforcement in `run-waves.py` now detects boundary violations (own no-touch, cross-CU collision, unattributed) and writes `boundary-breaches.txt`, but it does not redirect out-of-scope coders or leads back to scope to continue their work. A breached CU is not automatically escalated, breach contents are not injected into lead prompts, and unattributed writes have no owning agent. Waves can complete with `NEEDS_ATTENTION` without ever asking the agents to fix the breach.

Scope: In — auto-escalate breached CUs, include breached CUs in the per-CU lead set, inject `boundary-breaches.txt` into lead prompts, handle unattributed violations explicitly (force wave lead to address or fail before lead with clear guidance), and add a retry loop so the wave can self-correct. Out — changing attribution model, changing enforcement rules.

## Acceptance Criteria

- [ ] If a CU has a boundary breach (owned/shared-write) but feedback shows no ESCALATION, the runner creates/updates `<cu>.feedback.txt` with STATUS=ESCALATION, ESCALATION_REASON="CU boundary violation", and ESCALATION_DETAIL containing the relevant breach lines before the per-CU lead phase.
- [ ] The per-CU lead loop runs for all breached CUs regardless of initial escalation status.
- [ ] `boundary-breaches.txt` contents are injected into the per-CU lead prompt and the wave lead prompt when breaches exist.
- [ ] Unattributed breaches are handled: the wave lead is tasked to address them (guarded against non-escalated CUs) and/or the wave fails fast with actionable instructions if they cannot be assigned.
- [ ] After leads run, the runner re-measures boundaries once; if breaches remain, the wave is marked failed and convergence remains NEEDS_ATTENTION (or retries are exhausted).
- [ ] The run does not break — existing flows continue to work, and feedback files are merged without clobbering critical coder evidence.
- [ ] Regression tests cover auto-escalation and redirect behavior.

## Tests

- `test_cu_boundary.py` (extend) — auto-escalate a breached CU, inject breaches into prompts, unattributed forces wave lead action, retry behavior.
