# LLM-Driven Design (LLMDD)

This project explores AI-assisted software design through structured system
tensors. Every design decision, architecture constraint, and implementation
step is encoded in `.itr` (implementation tensor) files under `system_tensors/`.

## Workflow

The LLMDD pipeline is defined in `system_tensors/llm-driven-design-sys-prompt.itr`:

1. **Extract** requirements from input → Design Tensor (DTR)
2. **Advise** on design (analysis only, no implementation)
3. **Design** by emitting an Implementation Tensor (ITR)
4. **Code** by executing the ITR (strict order, no skipping)
5. **Output** feeds back into the Design Tensor

## Architecture rules

- Hexagonal architecture, dependency injection, dependency inversion
- No cross-layer dependencies
- Port flow: outbound → inbound
- Layer order: infra > domain > application > runtime

## Knowledge base

Project-specific knowledge is stored in `.opencode/instructions/`:
- `memory.md` — persistent session context
- `architecture.md` — architecture decisions and conventions

## Pre-push hook

A global pre-push hook blocks AI assistants from pushing to remote (installed
at `~/.config/git/hooks/pre-push`). Only interactive terminal users may push.
