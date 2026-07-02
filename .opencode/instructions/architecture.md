# Architecture

## LLMDD Tensor Pipeline

```
X (Extractor) → DTR (Design Tensor) → ADV (Advisor) ↔ DSG (Designer) → ITR (Implementation Tensor) → COD (Coder) → OUT (Output) → DTR
```

The pipeline loops: output feeds back into the design tensor for iterative
refinement.

## Implementation Tensor (ITR) structure

An ITR is generated in strict order:

1. **LOCK_ARCH** — freeze architecture from constraints
2. **MAP_LAYERS** — map to L0–L3 layers
3. **BIND_PORTS** — bind ports to domain edges
4. **DECOMPOSE_DOMAIN** — decompose domain logic
5. **DERIVE_INFRA** — derive infrastructure adapters
6. **GENERATE_TESTS** — generate tests from ports
7. **GENERATE_COMMITS** — generate commits as final step

## Constraints

- Hexagonal architecture (`HEX`)
- Dependency injection (`DI`)
- Dependency inversion (`DIP`)
- No cross-layer dependencies (`NO_CROSS_LAYER`)
- Port flow: outbound → inbound (`PORT_FLOW_OUT_IN`)
- Hard fail on constraint violation (`HARD_FAIL`)
