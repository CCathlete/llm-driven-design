# Architecture

## The Triad: Designer — Advisor — Coder

LLMDD models every development task as a team of three roles, not one:

| Role     | Node | Description |
|----------|------|-------------|
| Designer | DSG  | Human. Extracts the Design Tensor (DTR) from the codebase, brainstorms with the Advisor, and gives the green light to emit an ITR. |
| Advisor  | ADV  | AI agent. Receives the DTR, analyzes the codebase structure, brainstorms with the Designer. Never writes code — only emits ITRs when the Designer approves. |
| Coder    | COD  | AI agent. Receives a finished ITR and implements it in strict order. Never changes the design — executes exactly what the ITR prescribes. |

The Designer works in the role of a tech lead: they understand the full
context, extract the DTR (a structured snapshot of the codebase — AST,
file topology, type map, relations, constraints), and use it to brief the
Advisor. Once the Advisor and Designer agree on a plan, the Designer signs
off and the Advisor emits an ITR. The Coder then picks up the ITR and
works through it deterministically.

```
┌──────────┐   DTR    ┌──────────┐   ITR    ┌──────────┐
│ Designer │ ───────→ │ Advisor  │ ───────→ │  Coder   │
│  (human) │ ←─────── │ (AI)     │          │  (AI)    │
└──────────┘ discuss  └──────────┘          └──────────┘
    │                                            │
    │ extract                                    │ execute
    ▼                                            ▼
  Codebase                                    Codebase
```

## LLMDD Tensor Pipeline

```
X (Extractor) → DTR (Design Tensor) → ADV (Advisor) ↔ DSG (Designer) → ITR (Implementation Tensor) → COD (Coder) → OUT (Output) → DTR
```

The pipeline loops: output feeds back into the design tensor for iterative
refinement.

## Node roles

Each node in the pipeline has strict rules:

| Node | Rules |
|------|-------|
| **X (Extractor)** | Builds the DTR from the working tree. Positional, flat, no tree — explicit relations only. |
| **DTR (Design Tensor)** | Holds: MODE, STATE, FILES, TYPE_MAP, RELATIONS, CONSTRAINTS, META. |
| **ADV (Advisor)** | Analyze only, no implementation. Brainstorms with the Designer, emits ITR on approval. |
| **DSG (Designer)** | Emit ITR only, no code. The human decision-maker. |
| **ITR (Implementation Tensor)** | Holds: ARCH, LAYERS, PORTS, DOMAIN, APPLICATION, INFRASTRUCTURE, CONTROL, TESTS, COMMITS. |
| **COD (Coder)** | Execute ITR only, no design changes. Strict order, no skipping, deterministic. |
| **OUT (Output)** | Produced code. Feeds back into the next DTR cycle. |

## Implementation Tensor (ITR) structure

An ITR is generated in strict order:

1. **LOCK_ARCH** — freeze architecture from constraints
2. **MAP_LAYERS** — map to domain, application, infrastructure, control
3. **BIND_PORTS** — bind ports to application edges
4. **DECOMPOSE_DOMAIN** — decompose domain models
5. **DERIVE_APPLICATION** — derive application services and use cases
6. **BUILD_INFRASTRUCTURE** — build infrastructure adapters and Environment singleton
7. **WIRE_CONTROL** — wire dependency container, controllers, CLI, entry point
8. **GENERATE_TESTS** — generate tests from ports
9. **GENERATE_COMMITS** — generate commits as final step

## Constraints

- Hexagonal architecture (`HEX`)
- Dependency injection (`DI`)
- Dependency inversion (`DIP`)
- No cross-layer dependencies (`NO_CROSS_LAYER`)
- Port flow: outbound → inbound (`PORT_FLOW_OUT_IN`)
- Hard fail on constraint violation (`HARD_FAIL`)
