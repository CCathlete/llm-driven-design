"""Prep-runnable probe for bugfix-feature-17: confirm the final-wave defect
exists in the baseline at the exact DTR coordinate, and that the label-based
decision fixes all four labellings while preserving non-final deferral."""
import json
from pathlib import Path

root: Path = Path(__file__).resolve().parents[3]
src: str = (root / ".LLMDD" / "tools" / "run-waves.py").read_text()

# The defect: label compared to count.
has_bug: bool = "is_final: bool = (wave == num_waves)" in src
has_count: bool = "num_waves: int = len(waves)" in src

# Simulate both decisions over the four bug-11 labellings.
cases: list[list[int]] = [[1, 2, 3], [8], [7, 8], [1, 2, 8]]
count_commits: list[list[int]] = []
label_commits: list[list[int]] = []
labels: list[int]
for labels in cases:
    n: int = len(labels)
    last: int = labels[-1]
    count_commits.append([w for w in labels if w == n])
    label_commits.append([w for w in labels if w == last])

# Expectation: count-based loses the commit for non-1..N; label-based never does.
count_correct: bool = all(c for c in count_commits)
label_correct: bool = all(len(c) == 1 and c[0] == labs[-1]
                          for c, labs in zip(label_commits, cases))
# Non-final deferral preserved: for a 3-wave run, exactly one commits.
deferral_preserved: bool = label_commits[0] == [3]

passed: bool = has_bug and has_count and (not count_correct) and label_correct and deferral_preserved
detail: str = (f"bug_present={has_bug} count_binding={has_count} "
               f"count_commits={count_commits} label_commits={label_commits} "
               f"deferral_preserved={deferral_preserved}")
print(json.dumps({"passed": passed, "detail": detail}))
