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
    "any itr frame file": ".LLMDD/ITRS/**",
    "any dtr file": ".LLMDD/DTRS/**",
}

_PATHISH: Final[re.Pattern[str]] = re.compile(r"^[\w./*-]+$")
_EXT: Final[re.Pattern[str]] = re.compile(r"[\w./-]+\.(?:py|scala|md|json|itr|txt|sbt|yaml|yml)")
_FENCE: Final[re.Pattern[str]] = re.compile(r"```.*?```", re.S)


@dataclass(frozen=True, slots=True)
class NoTouch:
    entries: Sequence[str]
    patterns: Sequence[Pattern]
    unparsed: Sequence[str]


EMPTY: Final[NoTouch] = NoTouch(entries=(), patterns=(), unparsed=())


def prose_only(body: str) -> str:
    return _FENCE.sub("", body)


def _split_entries(body: str) -> Sequence[str]:
    raw_line: str
    for raw_line in body.splitlines():
        stripped: str = raw_line.strip()
        if stripped.startswith(NO_TOUCH_LABEL):
            tail: str = stripped[len(NO_TOUCH_LABEL):]
            return [part.strip() for part in tail.split(",") if part.strip()]
    return ()


def _trim(entry: str) -> str:
    return entry.rstrip(".;:")


def parse_no_touch(body: str) -> NoTouch:
    prose: str = prose_only(body)
    entries: Sequence[str] = _split_entries(prose)
    patterns: list[Pattern] = []
    unparsed: list[str] = []
    entry: str
    for entry in entries:
        clean: str = _trim(entry)
        mapped: str | None = PROSE_TO_GLOB.get(clean.lower())
        if mapped is not None:
            patterns.append(mapped)
        elif _PATHISH.match(clean):
            patterns.append(clean)
        else:
            unparsed.append(entry)
    return NoTouch(entries=entries, patterns=tuple(patterns), unparsed=tuple(unparsed))


def is_touched(patterns: Sequence[Pattern], path: str) -> bool:
    normalised: str = path[2:] if path.startswith("./") else path
    return any(fnmatch(normalised, pattern) for pattern in patterns)


def write_paths(body: str) -> Sequence[str]:
    prose: str = prose_only(body)
    write_lines: list[str] = []
    line: str
    for line in prose.splitlines():
        stripped: str = line.strip()
        if not stripped.startswith(NO_TOUCH_LABEL):
            write_lines.append(line)
    return tuple(dict.fromkeys(_EXT.findall("\n".join(write_lines))))
