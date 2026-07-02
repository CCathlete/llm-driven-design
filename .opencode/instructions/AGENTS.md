# LLM-Driven Design (LLMDD)

This project explores AI-assisted software design through structured system
tensors. Every design decision, architecture constraint, and implementation
step is encoded in `.itr` (implementation tensor) files under `system_tensors/`.

## The Triad

Every development task is a team of three:

| Role     | Who             | Responsibility |
|----------|-----------------|----------------|
| Designer | Human           | Extracts DTR, brainstorms, gives green light |
| Advisor  | AI agent        | Analyzes DTR, discusses design, emits ITR |
| Coder    | AI agent        | Receives ITR, implements in strict order |

The Designer opens an **Advisor session** for design work and a **Coder
session** for implementation. See `.opencode/agents/advisor.md` and
`.opencode/agents/coder.md`.

## Workflow

The LLMDD pipeline is defined in `system_tensors/llm-driven-design-sys-prompt.itr`:

1. **Extract** — Designer produces a DTR from the codebase (AST, files, types, relations)
2. **Advise** — Advisor analyzes the DTR and brainstorms with Designer
3. **Design** — On green light, Advisor emits an ITR
4. **Code** — Coder executes the ITR (strict order, no skipping)
5. **Output** — Produced code, feeds back into the next DTR

## Architecture rules

- Hexagonal architecture, dependency injection, dependency inversion
- No cross-layer dependencies
- Port flow: outbound → inbound
- Layer order: domain > application > infrastructure > control

## Knowledge base

Project-specific knowledge is stored in `.opencode/instructions/`:
- `memory.md` — persistent session context
- `architecture.md` — triad, pipeline, constraints, tensor format

## OpenCode agents

Two primary agents are defined in `.opencode/agents/`:

- **advisor** — design partner; reads only, never edits
- **coder** — implementation engine; reads and writes, never designs

Use `opencode --agent advisor` or `opencode --agent coder` to start a
session in the respective role.

## Pre-push hook

A global pre-push hook blocks AI assistants from pushing to remote (installed
at `~/.config/git/hooks/pre-push`). Only interactive terminal users may push.
