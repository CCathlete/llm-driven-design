# bug-5: Advisor writes without approval

- Status: Open
- Created: 2026-10-16
- Resolved: -
- Bug ID: bug-5-advisor-writes-without-approval

## Bug detail

Symptoms: the Advisor role is specified as analyze-only / never writes
application code (`.opencode/agents/llmdd/advisor.md`: `edit: deny`;
`RULE.ADV=ANALYZE_ONLY,NO_IMPLEMENTATION` in
`.LLMDD/prompts/llm-driven-design-sys-prompt.itr.md`), yet nothing
deterministically enforces the "ask Designer for explicit, specific approval
before obtaining code access" requirement. An Advisor session can perform
code changes without that approval, silently violating the triad
separation (Designer approves CU content, Coder implements).

Reproduction (from repo root, read-only):

```sh
sed -n '1,10p' .opencode/agents/llmdd/advisor.md
# EXPECTED: a deterministic gate — e.g. Advisor has no write-capable tools
#   at all (no edit, no bash/file-writes), or a permission-escalation hook
#   that blocks on missing Designer approval token.
# ACTUAL: frontmatter grants `bash: allow` alongside `edit: deny`, and the
#   no-implementation rule lives only in prompt text (agent md + system
#   tensor). Bash is a write-capable channel (`echo x > file`, `apply_patch`,
#   `python3 -c "open(...,'w')..."`), so the constraint is advisory, not
#   enforced. No approval-token check exists anywhere in the repo.
grep -rn "approval" .opencode/agents/llmdd/advisor.md .LLMDD/prompts/llm-driven-design-sys-prompt.itr.md | head
# EXPECTED: a machine-checkable approval requirement.
# ACTUAL: no hits — approval is an unwritten convention, not a gate.
```

Environment: repo checkout at `.opencode/agents/llmdd/advisor.md`,
`.LLMDD/prompts/llm-driven-design-sys-prompt.itr.md` lines ~193-195.

Suspected cause: the read-only posture was implemented as prompt
instruction (`ANALYZE_ONLY`, `NO_IMPLEMENTATION`, `edit: deny`) rather
than as capability removal + deterministic enforcement. `bash: allow`
(needed for read-only inspection) re-opens the write path, and there is
no approval-grant mechanism (explicit/specific Designer sign-off) for an
Advisor session to request or the harness to verify.

Impact: breaks the core LLMDD guarantee that design sessions are
side-effect-free until the Designer approves CU content and hands
compiled frames to a Coder. Any write performed by an Advisor bypasses
DTR-traceability, severity baselining, and Coder/Verifier review.

## Resolution status

Open. No fix merged.

## Fix summary (Resolved only)

Pending.

## Tests proving resolution (Resolved only)

Pending. Suggested: (1) static assertion that the Advisor agent grants no
write-capable tools, or that any write-capable tool is wrapped by an
approval-token check; (2) negative e2e test — Advisor session attempting a
file write without a Designer approval token is denied deterministically
(exit non-zero / permission error), and succeeds only with an explicit,
specific approval grant; (3) `grep` audit showing an approval requirement
with machine-checkable semantics in the agent frontmatter and/or system
tensor.
