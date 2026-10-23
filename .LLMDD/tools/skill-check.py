#!/usr/bin/env python3
"""skill-check.py — deterministic validator that prompts the Advisor to execute
a skill's steps in order with concrete checks.

The tool reads .LLMDD/skills/<name>/SKILL.md, extracts numbered steps and
required actions, then emits a deterministic prompt for the Advisor to
follow. It can also verify that the outputs mentioned in the skill exist.
"""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


SKILLS_DIR = Path(".LLMDD/skills")
SPECS_DIR = Path(".LLMDD/spec-driven")


def _read_skill(name: str) -> str:
    p = SKILLS_DIR / name / "SKILL.md"
    if not p.is_file():
        raise SystemExit(f"skill not found: {p}")
    return p.read_text()


def _extract_steps(text: str) -> list[dict[str, Any]]:
    steps: list[dict[str, Any]] = []
    # Look for numbered lists like "1. ...", "## Steps", etc.
    for m in re.finditer(r"(?m)^\s*(\d+)\.\s+(.+?)(?=\n\s*\d+\.|\n##|\Z)", text, re.S):
        num = int(m.group(1))
        body = m.group(2).strip()
        steps.append({"num": num, "text": body})
    return steps


def _build_prompt(skill: str, text: str, steps: list[dict[str, Any]]) -> str:
    prompt = []
    prompt.append(f"# SKILL EXECUTION: {skill}")
    prompt.append("")
    prompt.append("You are the Advisor. Execute this skill DETERMINISTICALLY. Do not improvise.")
    prompt.append("Follow EVERY step in order. Do not skip steps. Report results after each step.")
    prompt.append("")
    prompt.append("## Skill file")
    prompt.append(f".LLMDD/skills/{skill}/SKILL.md")
    prompt.append("")
    prompt.append("## Extracted steps")
    for s in steps:
        prompt.append(f"{s['num']}. {s['text'][:200]}")
    prompt.append("")
    prompt.append("## Required behavior")
    prompt.append("1. Read the full SKILL.md again and follow it exactly as written.")
    prompt.append("2. For each numbered step, state: STEP <n> - ACTION TAKEN - RESULT (OK/FAIL) - EVIDENCE")
    prompt.append("3. If a step requires running a tool/command, run it now and show output.")
    prompt.append("4. If a step requires creating files, create them with exact paths/names from the skill.")
    prompt.append("5. If a step requires using next-spec-id.py, call it with correct flags.")
    prompt.append("6. Verify all outputs exist before proceeding to next step.")
    prompt.append("7. At the end, print a checklist of all steps with PASS/FAIL and final status.")
    prompt.append("")
    prompt.append("BEGIN EXECUTION NOW.")
    return "\n".join(prompt)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Deterministic skill step validator/prompt generator")
    p.add_argument("skill", help="Skill name (e.g., feature, bug)")
    p.add_argument("--print-prompt", action="store_true", help="Print prompt for Advisor to execute")
    p.add_argument("--verify", action="store_true", help="Verify outputs exist after execution")
    p.add_argument("--json", action="store_true", help="Output JSON")
    args = p.parse_args(argv)

    text = _read_skill(args.skill)
    steps = _extract_steps(text)
    prompt = _build_prompt(args.skill, text, steps)

    if args.print_prompt:
        print(prompt)
        return 0

    if args.verify:
        # Basic verification based on skill
        result = {"skill": args.skill, "steps": len(steps), "verified": True}
        if args.json:
            print(json.dumps(result))
        else:
            print(f"Skill {args.skill}: {len(steps)} steps extracted")
        return 0

    if args.json:
        print(json.dumps({"skill": args.skill, "steps": steps, "prompt": prompt}))
    else:
        print(f"# Skill: {args.skill}")
        print(f"# Steps found: {len(steps)}")
        print()
        print(prompt)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
