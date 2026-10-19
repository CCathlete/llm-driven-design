"""Shared CU scope vocabulary: parse a Do NOT touch list into matchable patterns."""
from __future__ import annotations

import re
from dataclasses import dataclass
from fnmatch import fnmatch
from typing import Final, Sequence, TypeAlias

Pattern: TypeAlias = str

NO_TOUCH_LABEL: Final[str] = "Do NOT touch:"

PROSE_TO_GLOB: Final[dict[str, str]] = {
    "any scala source": "**/*.scala",
    "any python tool": ".LLMDD/tools/*.py",
    "any itr frame or feedback file": ".LLMDD/ITRS/**",
}

_PATHISH: Final[re.Pattern[str]] = re.compile(r"^[\w./*-]+$")

_EXT: Final[re.Pattern[str]] = re.compile(
    r"[\w./-]+\.(?:py|scala|md|json|itr|txt|sbt|yaml|yml)")


@dataclass(frozen=True, slots=True)
class NoTouch:
    """The normalised no-touch set declared by one CU."""

    entries: Sequence[str]
    patterns: Sequence[Pattern]
    unparsed: Sequence[str]


EMPTY: Final[NoTouch] = NoTouch(entries=(), patterns=(), unparsed=())


def _split_entries(body: str) -> Sequence[str]:
    """Return the comma-separated entries of the Do NOT touch line."""
    for raw_line in body.splitlines():
        stripped: str = raw_line.strip()
        if stripped.startswith(NO_TOUCH_LABEL):
            tail: str = stripped[len(NO_TOUCH_LABEL):]
            return [part.strip() for part in tail.split(",") if part.strip()]
    return ()


def _trim(entry: str) -> str:
    """Strip trailing sentence punctuation the shipped frames use."""
    return entry.rstrip(".;:")


def parse_no_touch(body: str) -> NoTouch:
    """Normalise a CU body Do NOT touch line into matchable patterns."""
    patterns: list[Pattern] = []
    unparsed: list[str] = []
    for entry in _split_entries(body):
        clean: str = _trim(entry)
        mapped: str | None = PROSE_TO_GLOB.get(clean.lower())
        if mapped is not None:
            patterns.append(mapped)
        elif _PATHISH.match(clean):
            patterns.append(clean)
        else:
            unparsed.append(entry)
    return NoTouch(entries=_split_entries(body), patterns=tuple(patterns),
                   unparsed=tuple(unparsed))


def is_touched(patterns: Sequence[Pattern], path: str) -> bool:
    """True when path falls under any no-touch pattern."""
    normalised: str = path[2:] if path.startswith("./") else path
    return any(fnmatch(normalised, pattern) for pattern in patterns)


def write_paths(body: str) -> Sequence[str]:
    """Return every path-like token appearing in a CU body, excluding the Do NOT touch line."""
    filtered_lines: list[str] = []
    for raw_line in body.splitlines():
        stripped: str = raw_line.strip()
        if stripped.startswith(NO_TOUCH_LABEL):
            continue
        filtered_lines.append(raw_line)
    filtered_body: str = "\n".join(filtered_lines)
    return tuple(dict.fromkeys(_EXT.findall(filtered_body)))
