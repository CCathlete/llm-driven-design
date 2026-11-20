"""Execution-target fixture for the StateConsistency hypothesis."""
import json


def main() -> int:
    observed: list[int] = [1, 2, 3]
    expected: list[int] = [1, 2, 3]
    passed: bool = observed == expected
    print(json.dumps({"passed": passed, "detail": "state consistency probe"}))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())