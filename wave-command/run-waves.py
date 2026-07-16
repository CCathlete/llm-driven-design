#!/usr/bin/env python3
"""run-waves.py - Execute CUs in waves with live coloured output and credit tracking.

Platform and shell agnostic replacement for run-waves.fish.
Requires: Python 3.8+, opencode CLI on PATH, git repo.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import threading
from datetime import datetime
from pathlib import Path


# ── Defaults ───────────────────────────────────────────────────────

DEFAULT_CODER_MODEL = "mistral/codestral-latest"
DEFAULT_LEAD_MODEL = "opencode/big-pickle"
DEFAULT_CU_TIMEOUT = 180
DEFAULT_LEAD_TIMEOUT = 300
DEFAULT_MAX_FIX_ITERATIONS = 3


# ── Token / Cost tracking ─────────────────────────────────────────

def _extract_step_tokens(ev: dict) -> tuple[dict, float, str]:
    """Extract (tokens_dict, cost, reason) from a step_finish event."""
    part = ev.get("part", {})
    tok = part.get("tokens", ev.get("tokens", {}))
    cost = part.get("cost", ev.get("cost", 0))
    reason = part.get("reason", ev.get("reason", ""))
    return tok, cost, reason


class TokenUsage:
    """Accumulated token and cost data from opencode step_finish events."""
    __slots__ = ("input", "output", "reasoning", "cache_read", "cache_write",
                 "total", "cost", "session_id", "model")

    def __init__(self):
        self.input = 0
        self.output = 0
        self.reasoning = 0
        self.cache_read = 0
        self.cache_write = 0
        self.total = 0
        self.cost = 0.0
        self.session_id = ""
        self.model = ""

    def add_from_step_finish(self, ev: dict):
        tok, cost, _reason = _extract_step_tokens(ev)
        self.input += tok.get("input", 0)
        self.output += tok.get("output", 0)
        self.reasoning += tok.get("reasoning", 0)
        cache = tok.get("cache", {})
        self.cache_read += cache.get("read", 0)
        self.cache_write += cache.get("write", 0)
        self.total += tok.get("total", 0)
        self.cost += cost

    def is_empty(self):
        return self.total == 0 and self.cost == 0.0

    def dict(self):
        return {
            "input": self.input, "output": self.output,
            "reasoning": self.reasoning,
            "cache_read": self.cache_read, "cache_write": self.cache_write,
            "total": self.total, "cost": self.cost,
            "session_id": self.session_id, "model": self.model,
        }


class WaveCredits:
    def __init__(self, wave_num: int):
        self.wave_num = wave_num
        self.coders: dict[str, TokenUsage] = {}

    def wave_totals(self) -> TokenUsage:
        totals = TokenUsage()
        for u in self.coders.values():
            totals.input += u.input
            totals.output += u.output
            totals.reasoning += u.reasoning
            totals.cache_read += u.cache_read
            totals.cache_write += u.cache_write
            totals.total += u.total
            totals.cost += u.cost
        return totals


class CreditTracker:
    def __init__(self):
        self.waves: list[WaveCredits] = []
        self._accumulated = TokenUsage()

    def start_wave(self, wave_num: int) -> WaveCredits:
        wc = WaveCredits(wave_num)
        self.waves.append(wc)
        return wc

    def accumulate(self, usage: TokenUsage):
        self._accumulated.input += usage.input
        self._accumulated.output += usage.output
        self._accumulated.reasoning += usage.reasoning
        self._accumulated.cache_read += usage.cache_read
        self._accumulated.cache_write += usage.cache_write
        self._accumulated.total += usage.total
        self._accumulated.cost += usage.cost

    @property
    def accumulated(self) -> TokenUsage:
        return self._accumulated


# ── ANSI Colours ───────────────────────────────────────────────────

class C:
    RESET   = "\033[0m"
    BOLD    = "\033[1m"
    DIM     = "\033[2m"
    ULINE   = "\033[4m"
    RED     = "\033[31m"
    GREEN   = "\033[32m"
    YELLOW  = "\033[33m"
    BLUE    = "\033[34m"
    MAGENTA = "\033[35m"
    CYAN    = "\033[36m"
    WHITE   = "\033[37m"
    BR_RED    = "\033[91m"
    BR_GREEN  = "\033[92m"
    BR_YELLOW = "\033[93m"
    BR_BLUE   = "\033[94m"
    BR_MAG    = "\033[95m"
    BR_CYAN   = "\033[96m"
    BR_WHITE  = "\033[97m"
    BG_RED   = "\033[41m"
    BG_GREEN = "\033[42m"
    BG_BLUE  = "\033[44m"

    @classmethod
    def disable(cls):
        for attr in dir(cls):
            if attr.isupper() and attr != "RESET":
                setattr(cls, attr, "")
        cls.RESET = ""


CODER_COLOURS = [
    (C.BR_CYAN,   "CYN"),
    (C.BR_GREEN,  "GRN"),
    (C.BR_YELLOW, "YLW"),
    (C.BR_MAG,    "MAG"),
    (C.BR_BLUE,   "BLU"),
    (C.BR_RED,    "RED"),
    (C.YELLOW,     "yel"),
    (C.CYAN,       "cyn"),
]


def _no_colour_support():
    if os.environ.get("NO_COLOR"):
        return True
    if not sys.stdout.isatty():
        return True
    return False

if _no_colour_support():
    C.disable()


# ── Formatting helpers ─────────────────────────────────────────────

def _fmt_tokens(n: int) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M"
    if n >= 1_000:
        return f"{n / 1_000:.1f}K"
    return str(n)

def _fmt_cost(c: float) -> str:
    if c == 0.0:
        return "$0.00"
    if c < 0.01:
        return f"${c:.4f}"
    return f"${c:.2f}"

def _banner_line(width=63):
    return f"{C.BR_CYAN}{'═' * width}{C.RESET}"

def _info(msg):
    print(f"  {C.DIM}{msg}{C.RESET}")

def _success(msg):
    print(f"  {C.BR_GREEN}✓{C.RESET} {msg}")

def _fail(msg):
    print(f"  {C.BR_RED}✗{C.RESET} {msg}")

def _warn(msg):
    print(f"  {C.BR_YELLOW}⚠{C.RESET} {msg}")

def _header(msg):
    print(f"  {C.BOLD}{C.BR_WHITE}{msg}{C.RESET}")


def _print_credit_table(cu_usages: dict[str, TokenUsage], label: str,
                         colour: str = ""):
    """Print credit usage table — cost is the primary column."""
    if not cu_usages:
        return

    print(f"    {colour}{C.BOLD}{label}{C.RESET}")
    hdr = (f"    {C.DIM}{'CU':<22} {'Cost':>8}  "
           f"{'In':>8} {'Out':>8} {'Reason':>8} {'CacheR':>8}{C.RESET}")
    print(hdr)
    print(f"    {C.DIM}{'─' * 72}{C.RESET}")

    wt = TokenUsage()
    for cu_id, u in cu_usages.items():
        if u.is_empty():
            continue
        cost_str = _fmt_cost(u.cost)
        cost_col = C.BR_YELLOW if u.cost > 0 else C.DIM
        print(f"    {colour}{cu_id:<22}{C.RESET}"
              f" {cost_col}{cost_str:>8}{C.RESET}  "
              f"{_fmt_tokens(u.input):>8}"
              f" {_fmt_tokens(u.output):>8}"
              f" {_fmt_tokens(u.reasoning):>8}"
              f" {_fmt_tokens(u.cache_read):>8}")
        wt.input += u.input
        wt.output += u.output
        wt.reasoning += u.reasoning
        wt.cache_read += u.cache_read
        wt.cache_write += u.cache_write
        wt.total += u.total
        wt.cost += u.cost

    total_cost_col = C.BR_YELLOW if wt.cost > 0 else C.DIM
    print(f"    {C.DIM}{'─' * 72}{C.RESET}")
    print(f"    {C.BOLD}{'TOTAL':<22}{C.RESET}"
          f" {total_cost_col}{_fmt_cost(wt.cost):>8}{C.RESET}  "
          f"{_fmt_tokens(wt.input):>8}"
          f" {_fmt_tokens(wt.output):>8}"
          f" {_fmt_tokens(wt.reasoning):>8}"
          f" {_fmt_tokens(wt.cache_read):>8}")
    print()


def _print_accumulated(tracker: CreditTracker):
    """Print accumulated totals — cost is the hero number."""
    a = tracker.accumulated
    if a.is_empty():
        return
    print(f"    {C.BOLD}{C.BR_WHITE}ACCUMULATED TOTALS{C.RESET}")
    print(f"    {C.DIM}{'─' * 50}{C.RESET}")
    cost_col = C.BR_YELLOW if a.cost > 0 else C.DIM
    print(f"    {C.BOLD}{'Total cost:':<25}{C.RESET} "
          f"{cost_col}{C.BOLD}{_fmt_cost(a.cost)}{C.RESET}")
    print(f"    {'Input tokens:':<25} {C.BOLD}{_fmt_tokens(a.input)}{C.RESET}")
    print(f"    {'Output tokens:':<25} {C.BOLD}{_fmt_tokens(a.output)}{C.RESET}")
    print(f"    {'Reasoning tokens:':<25} {C.BOLD}{_fmt_tokens(a.reasoning)}{C.RESET}")
    print(f"    {'Cache read:':<25} {C.BOLD}{_fmt_tokens(a.cache_read)}{C.RESET}")
    print()


# ── CLI ────────────────────────────────────────────────────────────

def parse_args(argv=None):
    p = argparse.ArgumentParser(
        description="Execute CUs in waves with live coloured output and credit tracking.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("--itr", required=True, help="Path to compiled ITR directory")
    p.add_argument("--app", required=True, help="Application name")
    p.add_argument("--waves", default=None, help="Wave definition JSON file")
    p.add_argument("--coder-model", default=DEFAULT_CODER_MODEL)
    p.add_argument("--lead-model", default=DEFAULT_LEAD_MODEL)
    p.add_argument("--max-fix-iterations", type=int, default=DEFAULT_MAX_FIX_ITERATIONS)
    p.add_argument("--cu-timeout", type=int, default=DEFAULT_CU_TIMEOUT)
    p.add_argument("--lead-timeout", type=int, default=DEFAULT_LEAD_TIMEOUT)
    p.add_argument("--project", default=None,
                   help="Project root directory (default: auto-detect from .opencode/agents/)")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--no-color", action="store_true")
    return p.parse_args(argv)


# ── CU ID → file resolution ───────────────────────────────────────

_CUID_RE = re.compile(r"^#\s*CU-ID:\s*(.+)$")

def build_cu_map(itr_path: Path) -> dict[str, Path]:
    cu_map = {}
    for f in sorted(itr_path.iterdir()):
        if f.suffix == ".itr" and f.is_file():
            try:
                with open(f) as fh:
                    for line in fh:
                        m = _CUID_RE.match(line)
                        if m:
                            cu_map[m.group(1).strip()] = f
                            break
            except Exception:
                pass
    return cu_map


def resolve_cu_file(cu_id: str, cu_map: dict[str, Path], itr_path: Path) -> Path | None:
    if cu_id in cu_map:
        return cu_map[cu_id]
    direct = itr_path / f"{cu_id}.itr"
    return direct if direct.is_file() else None


# ── Wave detection ─────────────────────────────────────────────────

def detect_waves(itr_path: Path, waves_file: str | None) -> list[dict]:
    if waves_file:
        p = Path(waves_file)
        if p.is_file():
            return json.loads(p.read_text())
        _warn(f"Waves file not found: {waves_file}")
    cu_map = build_cu_map(itr_path)
    cu_ids = sorted(cu_map.keys())
    return [{"wave": 1, "cus": cu_ids}] if cu_ids else []


# ── Git helpers ────────────────────────────────────────────────────

def _git(*args, cwd: Path | None = None) -> str:
    try:
        r = subprocess.run(
            ["git"] + list(args),
            capture_output=True, text=True, cwd=cwd or Path.cwd(), timeout=30,
        )
        return r.stdout.strip()
    except Exception:
        return ""

def git_diff_names(work_dir: Path, ref: str) -> list[str]:
    names = _git("diff", "--name-only", ref, cwd=work_dir)
    return names.splitlines() if names else []

def git_stash_ref(work_dir: Path) -> str:
    return _git("rev-parse", "HEAD", cwd=work_dir)


# ── Live-streaming with JSON parsing ──────────────────────────────

def _stream_process_json(cmd: list[str], log_fh, colour: str, prefix: str,
                         timeout: int, work_dir: Path,
                         token_usage: TokenUsage) -> int:
    start_ts = datetime.now()
    try:
        proc = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            cwd=work_dir, bufsize=1,
        )
    except FileNotFoundError:
        _fail(f"Command not found: {cmd[0]}")
        return 1

    _done = threading.Event()

    def _reader():
        assert proc.stdout is not None
        for raw in proc.stdout:
            line = raw.decode("utf-8", errors="replace").rstrip("\n")
            log_fh.write(line + "\n")
            log_fh.flush()

            try:
                ev = json.loads(line)
                ev_type = ev.get("type", "")

                sid = ev.get("sessionID", "")
                if sid and not token_usage.session_id:
                    token_usage.session_id = sid

                if ev_type == "model_start":
                    model_info = ev.get("model", {})
                    if isinstance(model_info, dict):
                        token_usage.model = model_info.get("id", "")
                    elif isinstance(model_info, str):
                        token_usage.model = model_info

                if ev_type == "step_finish":
                    token_usage.add_from_step_finish(ev)

                display = _format_event(ev, colour)
                if display:
                    print(f"  {colour}{prefix}{C.RESET} {C.DIM}│{C.RESET} {display}",
                          flush=True)

            except (json.JSONDecodeError, ValueError):
                print(f"  {colour}{prefix}{C.RESET} {C.DIM}│{C.RESET} {line}",
                      flush=True)

        _done.set()

    t = threading.Thread(target=_reader, daemon=True)
    t.start()

    while not _done.is_set():
        elapsed = (datetime.now() - start_ts).total_seconds()
        if elapsed > timeout:
            proc.kill()
            t.join(timeout=5)
            _fail(f"Timed out after {timeout}s")
            log_fh.write(f"\n[TIMED OUT after {timeout}s]\n")
            return 124
        _done.wait(timeout=0.5)

    proc.wait()
    return proc.returncode


def _format_event(ev: dict, colour: str) -> str | None:
    t = ev.get("type", "")

    if t == "step_start":
        return f"{C.DIM}▸ step start{C.RESET}"

    if t == "step_finish":
        tok, cost, reason = _extract_step_tokens(ev)
        parts = []
        if cost > 0:
            parts.append(f"cost:{_fmt_cost(cost)}")
        if tok.get("input"):
            parts.append(f"in:{_fmt_tokens(tok['input'])}")
        if tok.get("output"):
            parts.append(f"out:{_fmt_tokens(tok['output'])}")
        if tok.get("reasoning"):
            parts.append(f"reason:{_fmt_tokens(tok['reasoning'])}")
        cache = tok.get("cache", {})
        if cache.get("read"):
            parts.append(f"cR:{_fmt_tokens(cache['read'])}")
        if reason:
            parts.append(f"({reason})")
        token_str = " ".join(parts) if parts else "no data"
        return f"{C.BR_GREEN}▸ done{C.RESET} {C.DIM}{token_str}{C.RESET}"

    if t == "model_start":
        model = ev.get("model", {})
        if isinstance(model, dict):
            name = model.get("id", "unknown")
            provider = model.get("providerID", "")
            return f"{C.DIM}▸ model: {provider}/{name}{C.RESET}"
        return f"{C.DIM}▸ model start{C.RESET}"

    if t == "model_finish":
        return None

    if t == "text":
        part = ev.get("part", {})
        text = part.get("text", "")
        if not text:
            return None
        if len(text) > 120:
            text = text[:117] + "..."
        text = text.replace("\n", "↵")
        return f"{C.DIM}📝 {text}{C.RESET}"

    if t == "tool_invocation_start":
        part = ev.get("part", {})
        tool = part.get("toolName", "unknown")
        return f"{C.BR_YELLOW}🔧 {tool}{C.RESET}"

    if t == "tool_result":
        part = ev.get("part", {})
        tool = part.get("toolName", "unknown")
        result = part.get("result", "")
        if isinstance(result, str) and len(result) > 80:
            result = result[:77] + "..."
        elif isinstance(result, dict):
            result = json.dumps(result, ensure_ascii=False)
            if len(result) > 80:
                result = result[:77] + "..."
        return f"{C.DIM}  ↳ {tool}: {result}{C.RESET}"

    if t == "permission_request":
        part = ev.get("part", {})
        action = part.get("action", "")
        resource = part.get("resource", "")
        return f"{C.BR_MAG}🔐 permission: {action} {resource}{C.RESET}"

    return None


# ── Coder / Lead execution ─────────────────────────────────────────

def _usage_line(usage: TokenUsage, colour: str = "") -> str:
    """Format a single-line usage summary: cost is prominent."""
    if usage.is_empty():
        return ""
    cost_str = _fmt_cost(usage.cost)
    cost_col = C.BR_YELLOW if usage.cost > 0 else C.DIM
    return (f"    {cost_col}{C.BOLD}cost: {cost_str}{C.RESET}"
            f" {C.DIM}| in={_fmt_tokens(usage.input)}"
            f" out={_fmt_tokens(usage.output)}"
            f" reason={_fmt_tokens(usage.reasoning)}"
            f" cacheR={_fmt_tokens(usage.cache_read)}{C.RESET}")


def run_coder(cu_file: Path, cu_id: str, model: str,
              feedback_dir: Path, log_dir: Path, timeout: int,
              project_dir: Path, coder_colour: tuple[str, str]) -> tuple[int, list[str], TokenUsage]:
    log_file = log_dir / f"{cu_id}.log"
    colour_code, colour_label = coder_colour
    prefix = f"[{colour_label}:{cu_id}]"
    usage = TokenUsage()

    prompt = (
        f"Implement CU {cu_id} from ITR. "
        f"Read the CU frame file at {cu_file}, "
        f"implement the changes described, "
        f"write feedback to {feedback_dir / f'{cu_id}.feedback.txt'}, "
        f"then commit."
    )

    cmd = [
        "opencode", "run",
        "--model", model,
        "--agent", "coder",
        "--format", "json",
        "--auto",
        prompt,
    ]

    print(f"  {colour_code}{C.BOLD}▸{C.RESET} {colour_code}{C.BOLD}CU {cu_id}{C.RESET}"
          f" {C.DIM}→ {cu_file.name}{C.RESET}  {C.DIM}({colour_label}){C.RESET}")
    print(f"    {C.DIM}{'─' * 55}{C.RESET}")

    with open(log_file, "w") as log_fh:
        rc = _stream_process_json(cmd, log_fh, colour_code, prefix,
                                  timeout, project_dir, usage)

    files_changed = git_diff_names(project_dir, git_stash_ref(project_dir))

    if rc == 0:
        _success(f"CU {cu_id} completed {C.DIM}({len(files_changed)} files){C.RESET}")
    else:
        _fail(f"CU {cu_id} failed (exit {rc})")

    line = _usage_line(usage)
    if line:
        print(line)

    if files_changed:
        for fc in files_changed:
            print(f"    {colour_code}├{C.RESET} {fc}")

    print()
    return rc, files_changed, usage


def run_code_lead(wave: int, model: str, max_iterations: int,
                  feedback_dir: Path, log_dir: Path, timeout: int,
                  project_dir: Path) -> tuple[int, TokenUsage]:
    log_file = log_dir / f"lead-wave-{wave}.log"
    colour = C.BR_MAG
    prefix = "[LEAD]"
    usage = TokenUsage()

    prompt = (
        f"You are the code lead. Review wave {wave} implementation.\n\n"
        f"1. Check feedback files in {feedback_dir} for ESCALATED status\n"
        f"2. Fix any escalations\n"
        f"3. Run verification tests for implemented CUs\n"
        f"4. Write verification report to {feedback_dir}/wave-{wave}-verification.txt\n\n"
        f"Max fix iterations: {max_iterations}"
    )

    cmd = [
        "opencode", "run",
        "--model", model,
        "--agent", "verifier",
        "--format", "json",
        "--auto",
        prompt,
    ]

    print(f"  {colour}{C.BOLD}▸{C.RESET} {colour}{C.BOLD}Code Lead — Wave {wave} review{C.RESET}")
    print(f"    {C.DIM}{'─' * 55}{C.RESET}")

    with open(log_file, "w") as log_fh:
        rc = _stream_process_json(cmd, log_fh, colour, prefix,
                                  timeout, project_dir, usage)

    if rc == 0:
        _success(f"Lead review completed")
    else:
        _fail(f"Lead review failed (exit {rc})")

    line = _usage_line(usage)
    if line:
        print(line)
    print()
    return rc, usage


# ── Escalation check ───────────────────────────────────────────────

def check_escalations(wave_cus: list[str], feedback_dir: Path) -> int:
    count = 0
    for cu_id in wave_cus:
        fb = feedback_dir / f"{cu_id}.feedback.txt"
        if fb.is_file():
            text = fb.read_text(errors="replace")
            if "STATUS=ESCALATION" in text:
                count += 1
                _warn(f"Escalation: {cu_id}")
    return count


# ── Convergence tracking ───────────────────────────────────────────

def load_convergence(path: Path) -> dict:
    if path.is_file():
        return json.loads(path.read_text())
    return {"waves": [], "status": "RUNNING"}

def save_convergence(path: Path, data: dict):
    path.write_text(json.dumps(data, indent=2) + "\n")

def update_convergence(path: Path, wave_num: int, wave_cus: list[str],
                       escalations: int, coder_ok: bool, lead_ok: bool,
                       files_changed: dict[str, list[str]],
                       credits: dict[str, dict]):
    data = load_convergence(path)
    data["waves"].append({
        "wave": wave_num, "cus": wave_cus,
        "escalations": escalations,
        "coder_status": 0 if coder_ok else 1,
        "lead_status": 0 if lead_ok else 1,
        "files_changed": files_changed,
        "credits": credits,
    })
    data["status"] = "RUNNING"
    save_convergence(path, data)


# ── Main ───────────────────────────────────────────────────────────

def main(argv=None):
    args = parse_args(argv)
    if args.no_color:
        C.disable()

    itr_path = Path(args.itr).resolve()
    if not itr_path.is_dir():
        _fail(f"ITR path does not exist: {itr_path}")
        sys.exit(1)

    app_name = args.app
    work_dir = itr_path.parent

    # Resolve project root: --project flag, or auto-detect from .opencode/agents/
    if args.project:
        project_dir = Path(args.project).resolve()
    else:
        # Walk up from cwd, then ITR parent, looking for .opencode/agents/
        project_dir = None
        for start in [Path.cwd(), work_dir.resolve()]:
            search = start
            for _ in range(10):
                if (search / ".opencode" / "agents").is_dir():
                    project_dir = search
                    break
                parent = search.parent
                if parent == search:
                    break
                search = parent
            if project_dir:
                break
        if project_dir is None:
            _fail("Cannot find project root (.opencode/agents/ not found)")
            _info("Use --project to specify the project root")
            sys.exit(1)
    feedback_dir = work_dir / f"{app_name}.feedback"
    log_dir = work_dir / f"{app_name}.logs"
    convergence_file = work_dir / f"{app_name}.convergence.json"

    feedback_dir.mkdir(parents=True, exist_ok=True)
    log_dir.mkdir(parents=True, exist_ok=True)

    cu_map = build_cu_map(itr_path)

    print()
    print(_banner_line())
    print(f"  {C.BOLD}{C.BR_CYAN}LLMDD Wave Executor{C.RESET}")
    print(_banner_line())
    print()
    _info(f"ITR:           {C.BOLD}{itr_path}{C.RESET}")
    _info(f"Project:       {C.BOLD}{project_dir}{C.RESET}")
    _info(f"App:           {C.BOLD}{app_name}{C.RESET}")
    _info(f"CUs found:     {C.BOLD}{len(cu_map)}{C.RESET}")
    _info(f"Coder model:   {args.coder_model}")
    _info(f"Lead model:    {args.lead_model}")
    _info(f"CU timeout:    {args.cu_timeout}s")
    _info(f"Lead timeout:  {args.lead_timeout}s")
    print()

    _header("Loading waves...")
    waves = detect_waves(itr_path, args.waves)
    log_dir.joinpath("waves.json").write_text(json.dumps(waves, indent=2) + "\n")

    num_waves = len(waves)
    _success(f"Found {num_waves} waves")
    print()

    save_convergence(convergence_file, {"waves": [], "status": "RUNNING"})
    tracker = CreditTracker()

    for wave_def in waves:
        wave_num = wave_def["wave"]
        wave_cus = wave_def["cus"]
        description = wave_def.get("description", "")
        wc = tracker.start_wave(wave_num)

        print(_banner_line())
        print(f"  {C.BOLD}{C.BR_CYAN}Wave {wave_num}/{num_waves}{C.RESET}"
              + (f"  {C.DIM}— {description}{C.RESET}" if description else ""))
        print(_banner_line())
        _info(f"CUs: {', '.join(wave_cus)}")
        print()

        cu_files = []
        for cu_id in wave_cus:
            cu_file = resolve_cu_file(cu_id, cu_map, itr_path)
            if cu_file:
                cu_files.append((cu_id, cu_file))
            else:
                _warn(f"Not found: {cu_id}")

        if not cu_files:
            _warn("No CUs found, skipping")
            print()
            continue

        if args.dry_run:
            print(f"  {C.DIM}[dry-run] Would execute:{C.RESET}")
            for cu_id, f in cu_files:
                print(f"    {C.DIM}·{C.RESET} {C.BR_CYAN}{cu_id}{C.RESET} → {C.DIM}{f.name}{C.RESET}")
            print()
            continue

        _header(f"Running {len(cu_files)} coder(s)...")
        print()
        wave_files_changed: dict[str, list[str]] = {}
        all_passed = True

        for idx, (cu_id, cu_file) in enumerate(cu_files):
            coder_colour = CODER_COLOURS[idx % len(CODER_COLOURS)]
            rc, files, usage = run_coder(
                cu_file, cu_id, args.coder_model,
                feedback_dir, log_dir, args.cu_timeout,
                project_dir, coder_colour,
            )
            wave_files_changed[cu_id] = files
            wc.coders[cu_id] = usage
            tracker.accumulate(usage)
            if rc != 0:
                all_passed = False

        # Wave file summary
        print(f"  {C.BOLD}Wave {wave_num} files changed:{C.RESET}")
        for cu_id, files in wave_files_changed.items():
            if files:
                for f in files:
                    print(f"    {C.DIM}├{C.RESET} {C.BR_CYAN}{cu_id}{C.RESET} {C.DIM}→{C.RESET} {f}")
        total_files = set()
        for files in wave_files_changed.values():
            total_files.update(files)
        if total_files:
            _info(f"{C.BOLD}{len(total_files)}{C.RESET} unique file(s) modified this wave")
        else:
            _info("No files modified")
        print()

        # Wave credit summary
        _print_credit_table(wc.coders, f"Wave {wave_num} Credits", C.BR_CYAN)
        _print_accumulated(tracker)

        # Code lead review
        lead_rc, lead_usage = run_code_lead(
            wave_num, args.lead_model, args.max_fix_iterations,
            feedback_dir, log_dir, args.lead_timeout,
            project_dir,
        )
        tracker.accumulate(lead_usage)
        lead_ok = lead_rc == 0

        escalations = check_escalations(wave_cus, feedback_dir)
        if escalations > 0:
            _warn(f"Wave {wave_num}: {escalations} escalation(s)")
        else:
            _success(f"Wave {wave_num} completed")
        print()

        credits_data = {cu_id: u.dict() for cu_id, u in wc.coders.items()}
        credits_data["lead"] = lead_usage.dict()
        update_convergence(
            convergence_file, wave_num, wave_cus,
            escalations, all_passed, lead_ok,
            wave_files_changed, credits_data,
        )

    # Final summary
    convergence = load_convergence(convergence_file)
    total_escalations = sum(w["escalations"] for w in convergence["waves"])
    failed_waves = sum(
        1 for w in convergence["waves"]
        if w["coder_status"] != 0 or w["lead_status"] != 0
    )
    unique_all = set()
    for w in convergence["waves"]:
        for files in w.get("files_changed", {}).values():
            unique_all.update(files)

    if total_escalations == 0 and failed_waves == 0:
        convergence["status"] = "CONVERGED"
    else:
        convergence["status"] = "NEEDS_ATTENTION"
    save_convergence(convergence_file, convergence)

    status_colour = C.BR_GREEN if convergence["status"] == "CONVERGED" else C.BR_RED

    print(_banner_line())
    print(f"  {C.BOLD}Summary{C.RESET}")
    print(_banner_line())
    _info(f"Waves:       {C.BOLD}{num_waves}{C.RESET}")
    _info(f"Escalations: {total_escalations}")
    _info(f"Files:       {C.BOLD}{len(unique_all)}{C.RESET}")
    _info(f"Status:      {status_colour}{C.BOLD}{convergence['status']}{C.RESET}")
    print()
    _print_accumulated(tracker)
    print(_banner_line())


if __name__ == "__main__":
    main()
