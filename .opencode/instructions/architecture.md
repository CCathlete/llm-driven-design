# Architecture

## The Triad: Designer — Advisor — Coder

LLMDD models every development task as a team of three roles, not one:

| Role     | Node | Who      | Description |
|----------|------|----------|-------------|
| Designer | DSG  | Human    | Obtains baseline DTR (via dtr-builder), brainstorms with Advisor, approves CU content, runs itr-compiler, assigns CUs to Coders. |
| Advisor  | ADV  | AI agent | Receives baseline DTR, analyzes codebase structure, brainstorms with Designer. Never writes code — only drafts CU content for Designer approval. |
| Coder    | COD  | AI agent | Receives compiled CU frames, implements them in dependency order, writes per-CU feedback. Never changes design. |

The Designer works in the role of a tech lead: they obtain a baseline DTR
(either by extracting from existing code or creating a greenfield skeleton),
brief the Advisor, and together they decompose work into Computational Units
(CUs). Once approved, the Designer compiles CU content via `itr-compiler` and
assigns the resulting CU frames to Coders.

```
┌──────────┐   DTR    ┌──────────┐  CU content  ┌──────────────┐   frames   ┌──────────┐
│ Designer │ ───────→ │ Advisor  │ ────────────→ │ ITR Compiler │ ─────────→ │  Coder   │
│  (human) │ ←─────── │ (AI)     │ brainstorm    │   (tool)     │            │  (AI)    │
└──────────┘ discuss  └──────────┘               └──────────────┘            └──────────┘
    │                                                                             │
    │ runs dtr-builder                                                            │ implements +
    ▼                                                                             ▼ per-CU feedback
  Codebase                                                                     Codebase
```

## LLMDD Tensor Pipeline

```
X (dtr-builder) → DTR (baseline) → ADV (advisor) ↔ DSG (designer) → CU content → ITR COMPILER → ITR (frames) → COD (coder) → OUT (code + feedback) → DTR (next iteration)
```

The pipeline loops: output feeds back into the design tensor for iterative
refinement.

## Node roles

Each node in the pipeline has strict rules:

| Node | Rules |
|------|-------|
| **X (dtr-builder)** | Walks source tree, extracts files/types/relations, produces flat DTR. Two modes: --root (extract) and --create-baseline (greenfield). |
| **DTR (Baseline Design Tensor)** | Flat KEY=VALUE file with sections: ARCH, META, FILE, CODEX, TYPE, REL. Serves as coordinate system for CUs. |
| **ADV (Advisor)** | Analyze only, no implementation. Brainstorms with Designer, drafts CU content. |
| **DSG (Designer)** | Decides on CU decomposition, approves CU content, runs itr-compiler, assigns CUs to Coders. No implementation. |
| **ITR COMPILER** | Tool that takes baseline DTR + CU content (JSON/YAML/JSONL/raw) and produces per-CU .itr frame files in `<app>.itr/` directory. |
| **ITR (Implementation Tensor)** | Directory of compiled CU frame files (cu-001.itr, cu-002.itr, ...) plus LEGEND.itr and ARCH.itr. |
| **COD (Coder)** | Implements CUs in dependency order. Writes per-CU feedback. Commits once per task. No design changes. |
| **OUT (Output)** | Produced code + per-CU FEEDBACK files. Feeds back into the next DTR cycle. |

## Tensor format

Every line is a complete semantic unit: `NAMESPACE.KEY=VALUE` or `KEY=VALUE`.
No brackets, no nesting. A misgenerated line kills only itself — no cascade.

```
SYS=LLMDDv7.0
MODE=DTR_BASELINE_X_CU_COMPILE
ARCH=HEX,DI,DIP,NO_CROSS_LAYER,PORT_FLOW_OUT_IN,HARD_FAIL
WORKFLOW.STEP1=OBTAIN_BASELINE_DTR
```

## Implementation Tensor (ITR) structure

An ITR is a **directory** at `itr-buffer/<app>.itr/` containing compiled
CU frame files:

```
<app>.itr/
├── LEGEND.itr          # Symbol definitions
├── ARCH.itr            # App-specific architecture config
├── cu-001.itr          # Compiled frame for CU 001
├── cu-001.feedback     # Coder feedback for CU 001
├── cu-002.itr          # Compiled frame for CU 002
└── cu-002.feedback     # Coder feedback for CU 002
```

Each CU frame is compiled by `itr-compiler` from CU content + baseline DTR.

## Workflow

1. **OBTAIN_BASELINE_DTR** — Designer runs `dtr-builder` (extract or create)
2. **ANALYZE_AND_DECOMPOSE** — Advisor analyzes DTR, brainstorms with Designer
3. **DRAFT_CU_CONTENT** — Advisor drafts CU content, Designer approves
4. **COMPILE_ITR** — Designer runs `itr-compiler` to produce CU frame files
5. **IMPLEMENT_CUS** — Coder implements CUs in order, writes per-CU feedback, commits
6. **ITERATE** — Re-scan with dtr-builder, repeat

## Constraints

- Hexagonal architecture (`HEX`)
- Dependency injection (`DI`)
- Dependency inversion (`DIP`)
- No cross-layer dependencies (`NO_CROSS_LAYER`)
- Port flow: outbound → inbound (`PORT_FLOW_OUT_IN`)
- DotEnv walk-up discovery (`DOTENV_WALKUP`)
- Per-CU feedback (`CODER_FEEDBACK`)
- Severity taxonomy: CRITICAL / MAJOR / MINOR / TRIVIAL (`SEVERITY`)
- One ITR directory per app, tracked in git (`ITR_LIFECYCLE`)
- Hard fail on constraint violation (`HARD_FAIL`)

See the system tensor at `system_tensors/llm-driven-design-sys-prompt.itr` for
the complete specification.
