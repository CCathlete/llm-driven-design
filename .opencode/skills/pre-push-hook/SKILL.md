---
name: pre-push-hook
description: Use when creating, updating, or debugging the pre-push hook that blocks AI assistants from pushing to remote. Hook lives in .githooks/pre-push.
---

# Pre-push Hook: AI Assistant Push Blocker

This repo uses a custom pre-push hook at `.githooks/pre-push` that prevents AI
coding assistants (opencode, GitHub Copilot, Claude Code, Cursor, Windsurf,
aider, etc.) from pushing commits to the remote repository.

## Detection method

**Process-tree inspection** (the only reliable approach): walks the
parent-process chain looking for known agent tool binaries (`opencode`,
`copilot`, `claude-code`, `cursor`, `windsurf`, `aider`) in the process
command name or arguments. Environment variables are not used — AI
assistants rarely set unique env vars, and those that do may change
 or omit them without notice.

## Setup

The hook is activated by the repo-level git config:

```
git config core.hooksPath .githooks
```

## How it works

If the hook detects an agent tool, it prints an error and exits non-zero,
which aborts the push. Only interactive terminal sessions from the repository
owner are allowed to push.

## Adding a new agent tool to detect

Edit `.githooks/pre-push` and add the tool name to:

- The `AGENT_TOOLS` list (process-tree check)
- An environment-variable check block (if the tool sets a known env var)
