# Wave Command

Execute CUs in parallel waves with code lead review.

## Usage

```fish
./run-waves.fish --itr <itr-path> --app <app-name> [--waves <waves-json>]
```

## Options

| Option | Description | Default |
|--------|-------------|---------|
| `--itr <path>` | Path to compiled ITR directory | Required |
| `--app <name>` | Application name | Required |
| `--waves <json>` | Wave definition file | Auto-detect |
| `--coder-model <m>` | Model for coders | gemini-2.5-flash |
| `--lead-model <m>` | Model for code lead | opencode-zen-big-pickle |
| `--max-fix-iterations <n>` | Max fix iterations per wave | 3 |
| `--dry-run` | Show what would be executed | false |

## Wave Structure

Each wave:
1. Launches parallel coders (one per CU)
2. Waits for all coders to finish
3. Launches code lead to review and fix escalations
4. Moves to next wave

Final wave: code lead runs E2E tests.

## Wave Definition Files

- `itr-compiler-waves.json` - itr-compiler refactor waves
- `llmdd-v8-waves.json` - LLMDD v8 implementation waves

## Examples

```fish
# Run itr-compiler refactor
./run-waves.fish --itr itr-buffer/itr-compiler.itr --app itr-compiler --waves itr-compiler-waves.json

# Run LLMDD v8 implementation
./run-waves.fish --itr itr-buffer/llm-driven-design.itr --app llm-driven-design --waves llmdd-v8-waves.json

# Dry run
./run-waves.fish --itr itr-buffer/itr-compiler.itr --app itr-compiler --dry-run
```

## Output

- `<app>.feedback/` - Per-CU feedback files
- `<app>.logs/` - Execution logs
- `<app>.convergence.json` - Convergence state

## Models

- **Coders**: gemini-2.5-flash (fast, parallel execution)
- **Code Lead**: opencode-zen-big-pickle (smarter, reviews and fixes)
