# Project Memory

## Repository purpose

Data structures, code, prompts, and apps for LLM-driven design (LLMDD)
development. Uses system tensors (`.itr` files) to encode design workflows.

## Key files

| File | Purpose |
|------|---------|
| `system_tensors/llm-driven-design-sys-prompt.itr` | Master system prompt defining the LLMDDv3 pipeline |
| `AGENTS.md` | Project instructions loaded by opencode |
| `.opencode/opencode.json` | Project-level opencode config |
| `.opencode/instructions/` | Knowledge base (architecture, memory, etc.) |
| `.opencode/skills/` | Project-specific skills |

## OpenCode setup

- **Global pre-push hook**: blocks AI assistants from pushing (see
  `~/.config/opencode/skills/pre-push-hook/SKILL.md`)
- **Project agent instructions**: loaded from `AGENTS.md`
- **Project skills**: scanned from `.opencode/skills/`

## Session notes

<!-- Use this section to record cross-session context, decisions, and TODO items -->
