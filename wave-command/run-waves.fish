#!/usr/bin/env fish

# run-waves.fish - Execute CUs in waves using simple models
# Purpose: save credits by using cheap models for implementation

function usage
    echo "Usage: ./run-waves.fish --itr <itr-path> --app <app-name> [--waves <waves-json>]"
    echo ""
    echo "Options:"
    echo "  --coder-model <model>   Model for coders (default: mistral/codestral-latest)"
    echo "  --lead-model <model>    Model for code lead (default: opencode/big-pickle)"
    echo "  --waves <file.json>     Custom wave definitions"
    echo "  --dry-run               Show what would be executed"
    echo "  --cu-timeout <seconds>  Timeout per CU (default: 180)"
    echo "  --lead-timeout <seconds> Timeout for lead review (default: 300)"
    exit 1
end

# Defaults
set -g ITR_PATH ""
set -g APP_NAME ""
set -g WAVES_FILE ""
set -g CODER_MODEL "mistral/codestral-latest"
set -g LEAD_MODEL "opencode/big-pickle"
set -g MAX_FIX_ITERATIONS 3
set -g DRY_RUN false
set -g CU_TIMEOUT 180
set -g LEAD_TIMEOUT 300

# Parse arguments
set -l i 1
while test $i -le (count $argv)
    switch $argv[$i]
        case --itr
            set i (math $i + 1)
            set -g ITR_PATH $argv[$i]
        case --app
            set i (math $i + 1)
            set -g APP_NAME $argv[$i]
        case --waves
            set i (math $i + 1)
            set -g WAVES_FILE $argv[$i]
        case --coder-model
            set i (math $i + 1)
            set -g CODER_MODEL $argv[$i]
        case --lead-model
            set i (math $i + 1)
            set -g LEAD_MODEL $argv[$i]
        case --max-fix-iterations
            set i (math $i + 1)
            set -g MAX_FIX_ITERATIONS $argv[$i]
        case --cu-timeout
            set i (math $i + 1)
            set -g CU_TIMEOUT $argv[$i]
        case --lead-timeout
            set i (math $i + 1)
            set -g LEAD_TIMEOUT $argv[$i]
        case --dry-run
            set -g DRY_RUN true
        case --help
            usage
    end
    set i (math $i + 1)
end

if test -z "$ITR_PATH" -o -z "$APP_NAME"
    echo "Error: --itr and --app are required"
    usage
end

# Resolve to absolute path
if not string match -r '^/' "$ITR_PATH"
    set -g ITR_PATH (realpath "$ITR_PATH")
end

if not test -d "$ITR_PATH"
    echo "Error: ITR path does not exist: $ITR_PATH"
    exit 1
end

# Configuration
set -g WORK_DIR (dirname "$ITR_PATH")
set -g FEEDBACK_DIR "$WORK_DIR/$APP_NAME.feedback"
set -g CONVERGENCE_FILE "$WORK_DIR/$APP_NAME.convergence.json"
set -g LOG_DIR "$WORK_DIR/$APP_NAME.logs"

mkdir -p "$FEEDBACK_DIR" "$LOG_DIR"

# Wave detection
function detect_waves
    set -l itr_path $argv[1]
    set -l waves_file $argv[2]

    if test -n "$waves_file" -a -f "$waves_file"
        cat "$waves_file"
        return
    end

    # Simple: all CUs in wave 1
    set -l cus (ls "$itr_path"/cu-*.itr 2>/dev/null | sort)
    set -l cu_ids
    for cu in $cus
        set -a cu_ids (string replace -r '\.itr$' '' (basename "$cu"))
    end

    echo "[{\"wave\": 1, \"cus\": [$(printf '"%s",' $cu_ids | string trim -r -c ',')]}]"
end

# ── Coder execution ───────────────────────────────────────────────

function run_coder
    set -l cu_file $argv[1]
    set -l model $argv[2]

    set -l cu_id (string replace -r '\.itr$' '' (basename "$cu_file"))
    set -l log_file "$LOG_DIR/$cu_id.log"

    echo "  [coder] $cu_id with $model"

    # Use opencode run with --format json for clean output
    opencode run \
        --model "$model" \
        --agent coder \
        --format json \
        --auto \
        "Implement CU $cu_id from ITR. Read the CU frame file at $cu_file, implement the changes described, write feedback to $FEEDBACK_DIR/$cu_id.feedback.txt, then commit." \
        > "$log_file" 2>&1

    return $status
end

function wait_for_process
    set -l pid $argv[1]
    set -l timeout $argv[2]
    set -l label $argv[3]
    set -l elapsed 0

    while kill -0 $pid 2>/dev/null
        if test $elapsed -ge $timeout
            echo "  ✗ $label timeout after {$timeout}s, killing"
            kill $pid 2>/dev/null
            return 1
        end
        sleep 5
        set elapsed (math $elapsed + 5)
        printf "."
    end
    echo ""
    wait $pid
    return $status
end

# ── Code lead execution ───────────────────────────────────────────

function run_code_lead
    set -l wave $argv[1]
    set -l model $argv[2]
    set -l max_iterations $argv[3]

    set -l log_file "$LOG_DIR/lead-wave-$wave.log"

    echo "  [lead] Wave $wave review with $model"

    opencode run \
        --model "$model" \
        --agent verifier \
        --format json \
        --auto \
        "You are the code lead. Review wave $wave implementation.

1. Check feedback files in $FEEDBACK_DIR for ESCALATED status
2. Fix any escalations
3. Run verification tests for implemented CUs
4. Write verification report to $FEEDBACK_DIR/wave-$wave-verification.txt

Max fix iterations: $max_iterations" \
        > "$log_file" 2>&1

    return $status
end

# ── Main ──────────────────────────────────────────────────────────

echo "═══════════════════════════════════════════════════════════════"
echo "  LLMDD Wave Executor"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  ITR:           $ITR_PATH"
echo "  App:           $APP_NAME"
echo "  Coder model:   $CODER_MODEL"
echo "  Lead model:    $LEAD_MODEL"
echo "  CU timeout:    {$CU_TIMEOUT}s"
echo "  Lead timeout:  {$LEAD_TIMEOUT}s"
echo ""

# Load waves
echo "Loading waves..."
set -l waves_json (detect_waves "$ITR_PATH" "$WAVES_FILE")
echo "$waves_json" > "$LOG_DIR/waves.json"

set -l num_waves (echo "$waves_json" | jq length)
echo "Found $num_waves waves"
echo ""

echo "{\"waves\": [], \"status\": \"RUNNING\"}" > "$CONVERGENCE_FILE"

# Execute waves
set -l wave 1
while test $wave -le $num_waves
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Wave $wave / $num_waves"
    echo "═══════════════════════════════════════════════════════════════"

    set -l wave_cus (echo "$waves_json" | jq -r ".[$(math $wave - 1)].cus[]")
    echo "  CUs: $wave_cus"
    echo ""

    # Build CU file paths
    set -l cu_files
    for cu_id in $wave_cus
        set -l cu_file "$ITR_PATH/$cu_id.itr"
        if test -f "$cu_file"
            set -a cu_files "$cu_file"
        else
            echo "  ⚠ Not found: $cu_file"
        end
    end

    if test (count $cu_files) -eq 0
        echo "  No CUs, skipping"
        set wave (math $wave + 1)
        continue
    end

    if test "$DRY_RUN" = true
        echo "  [dry-run] Would execute:"
        for f in $cu_files
            echo "    - "(basename "$f")
        end
        set wave (math $wave + 1)
        continue
    end

    # Run coders sequentially (each takes 30-60s, parallel doesn't help much)
    echo "  Running "(count $cu_files)" coder(s)..."
    set -l all_passed true
    for cu_file in $cu_files
        set -l cu_id (string replace -r '\.itr$' '' (basename "$cu_file"))
        run_coder "$cu_file" "$CODER_MODEL"
        if test $status -ne 0
            echo "  ✗ $cu_id failed"
            set all_passed false
        else
            echo "  ✓ $cu_id completed"
        end
    end
    echo ""

    # Code lead review
    echo "  Running code lead..."
    run_code_lead "$wave" "$LEAD_MODEL" "$MAX_FIX_ITERATIONS"
    if test $status -eq 0
        echo "  ✓ Lead review completed"
    else
        echo "  ✗ Lead review failed"
    end
    echo ""

    # Check escalations
    set -l escalations 0
    for cu_id in $wave_cus
        set -l fb "$FEEDBACK_DIR/$cu_id.feedback.txt"
        if test -f "$fb"
            if grep -q "STATUS=ESCALATION" "$fb" 2>/dev/null
                set escalations (math $escalations + 1)
                echo "  ⚠ Escalation: $cu_id"
            end
        end
    end

    if test $escalations -gt 0
        echo "  ⚠ Wave $wave: $escalations escalation(s)"
    else
        echo "  ✓ Wave $wave completed"
    end
    echo ""

    set wave (math $wave + 1)
end

# Summary
echo "═══════════════════════════════════════════════════════════════"
echo "  Done. Waves: $num_waves"
echo "═══════════════════════════════════════════════════════════════"
