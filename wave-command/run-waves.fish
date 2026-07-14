#!/usr/bin/env fish

# run-waves.fish - Execute CUs in parallel waves with code lead review
#
# Usage:
#   ./run-waves.fish --itr <itr-path> --app <app-name> [--waves <waves-json>]

# ── Argument parsing ──────────────────────────────────────────────

function usage
    echo "Usage: ./run-waves.fish --itr <itr-path> --app <app-name> [--waves <waves-json>]"
    echo ""
    echo "Options:"
    echo "  --itr <path>       Path to compiled ITR directory"
    echo "  --app <name>       Application name"
    echo "  --waves <json>     Optional JSON file defining wave structure"
    echo "  --coder-model <m>  Model for coders (default: gemini-2.5-flash)"
    echo "  --lead-model <m>   Model for code lead (default: opencode-zen-big-pickle)"
    echo "  --max-fix-iterations <n>  Max fix iterations per wave (default: 3)"
    echo "  --dry-run          Show what would be executed without running"
    echo "  --help             Show this help"
    exit 1
end

# Defaults
set -g ITR_PATH ""
set -g APP_NAME ""
set -g WAVES_FILE ""
set -g CODER_MODEL "gemini-2.5-flash"
set -g LEAD_MODEL "opencode-zen-big-pickle"
set -g MAX_FIX_ITERATIONS 3
set -g DRY_RUN false

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
        case --dry-run
            set -g DRY_RUN true
        case --help
            usage
        case '*'
            echo "Unknown option: $argv[$i]"
            usage
    end
    set i (math $i + 1)
end

# Validate required arguments
if test -z "$ITR_PATH"
    echo "Error: --itr is required"
    usage
end

if test -z "$APP_NAME"
    echo "Error: --app is required"
    usage
end

# Resolve ITR_PATH to absolute path
if not string match -r '^/' "$ITR_PATH"
    set -g ITR_PATH (realpath "$ITR_PATH")
end

if not test -d "$ITR_PATH"
    echo "Error: ITR path does not exist: $ITR_PATH"
    exit 1
end

# ── Configuration ─────────────────────────────────────────────────

set -g WORK_DIR (dirname "$ITR_PATH")
set -g FEEDBACK_DIR "$WORK_DIR/$APP_NAME.feedback"
set -g CONVERGENCE_FILE "$WORK_DIR/$APP_NAME.convergence.json"
set -g LOG_DIR "$WORK_DIR/$APP_NAME.logs"

# Create directories
mkdir -p "$FEEDBACK_DIR"
mkdir -p "$LOG_DIR"

# ── Wave detection ────────────────────────────────────────────────

function detect_waves
    set -l itr_path $argv[1]
    set -l waves_file $argv[2]

    if test -n "$waves_file" -a -f "$waves_file"
        cat "$waves_file"
        return
    end

    # Auto-detect waves from CU dependencies
    set -l cus (ls "$itr_path"/cu-*.itr 2>/dev/null | sort)
    set -l wave_num 1
    set -l remaining $cus

    echo "["
    set -l first true

    while test (count $remaining) -gt 0
        set -l next_remaining
        set -l wave_cus

        for cu in $remaining
            set -l cu_id (basename "$cu" .itr)
            set -l has_deps false
            for other in $remaining
                if test "$other" != "$cu"
                    set -l other_id (basename "$other" .itr)
                    if string match -r "cu-0[0-9]+" "$cu_id" >/dev/null
                        and string match -r "cu-0[0-9]+" "$other_id" >/dev/null
                        set -l num1 (echo "$cu_id" | grep -oP '\d+')
                        set -l num2 (echo "$other_id" | grep -oP '\d+')
                        if test "$num2" -lt "$num1" 2>/dev/null
                            set has_deps true
                            break
                        end
                    end
                end
            end

            if test "$has_deps" = true
                set -a next_remaining $cu
            else
                set -a wave_cus $cu
            end
        end

        if test (count $wave_cus) -eq 0
            set wave_cus $remaining
            set remaining
        else
            set remaining $next_remaining
        end

        if test "$first" = true
            set first false
        else
            echo ","
        end

        set -l cu_ids
        for cu in $wave_cus
            set -a cu_ids (basename "$cu" .itr)
        end

        echo "  {\"wave\": $wave_num, \"cus\": [$(string join ',' $cu_ids)]}"
        set wave_num (math $wave_num + 1)
    end

    echo "]"
end

# ── Coder execution ───────────────────────────────────────────────

function run_coder
    set -l cu_file $argv[1]
    set -l itr_path $argv[2]
    set -l feedback_dir $argv[3]
    set -l model $argv[4]
    set -l log_dir $argv[5]

    set -l cu_id (basename "$cu_file" .itr)
    set -l log_file "$log_dir/$cu_id.log"

    echo "  [coder] Starting $cu_id with model $model"

    opencode --agent coder \
        --model "$model" \
        --prompt "Implement CU $cu_id from ITR at $itr_path. Read the CU frame file $cu_file, implement it, write feedback to $feedback_dir/$cu_id.feedback.txt, and commit." \
        > "$log_file" 2>&1; or return 1

    echo "  [coder] ✓ $cu_id completed"
    return 0
end

function run_coder_parallel
    set -l cu_files $argv
    set -l pids

    for cu_file in $cu_files
        run_coder "$cu_file" "$ITR_PATH" "$FEEDBACK_DIR" "$CODER_MODEL" "$LOG_DIR" &
        set -a pids $last_pid
    end

    set -l all_passed true
    for pid in $pids
        wait $pid
        if test $status -ne 0
            set all_passed false
        end
    end

    if test "$all_passed" = true
        return 0
    else
        return 1
    end
end

# ── Code lead execution ───────────────────────────────────────────

function run_code_lead
    set -l wave $argv[1]
    set -l itr_path $argv[2]
    set -l feedback_dir $argv[3]
    set -l lead_model $argv[4]
    set -l log_dir $argv[5]
    set -l max_iterations $argv[6]

    set -l log_file "$log_dir/lead-wave-$wave.log"

    echo "  [lead] Starting code lead review for wave $wave"

    opencode --agent verifier \
        --model "$lead_model" \
        --prompt "You are the code lead. Review wave $wave implementation.

1. Check feedback files in $feedback_dir for ESCALATED status
2. Fix any escalations
3. Run verification tests for implemented CUs
4. Track convergence iterations
5. Write verification report to $feedback_dir/wave-$wave-verification.txt

Max fix iterations: $max_iterations
If tests don't converge after $max_iterations, write DIAGNOSIS report." \
        > "$log_file" 2>&1; or return 1

    echo "  [lead] ✓ Wave $wave review completed"
    return 0
end

# ── Main execution ────────────────────────────────────────────────

echo "═══════════════════════════════════════════════════════════════"
echo "  LLMDD Wave Executor"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  ITR:           $ITR_PATH"
echo "  App:           $APP_NAME"
echo "  Coder model:   $CODER_MODEL"
echo "  Lead model:    $LEAD_MODEL"
echo "  Max fix iters: $MAX_FIX_ITERATIONS"
echo "  Feedback dir:  $FEEDBACK_DIR"
echo "  Log dir:       $LOG_DIR"
echo ""

# Detect or load waves
echo "Detecting waves..."
set -l waves_json (detect_waves "$ITR_PATH" "$WAVES_FILE")
echo "$waves_json" > "$LOG_DIR/waves.json"

set -l num_waves (echo "$waves_json" | jq length)
echo "Found $num_waves waves"
echo ""

# Initialize convergence file
echo "{\"waves\": [], \"status\": \"RUNNING\"}" > "$CONVERGENCE_FILE"

# Execute waves
set -l wave 1
while test $wave -le $num_waves
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Wave $wave / $num_waves"
    echo "═══════════════════════════════════════════════════════════════"

    # Get CUs for this wave
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
            echo "  ⚠ CU file not found: $cu_file"
        end
    end

    if test (count $cu_files) -eq 0
        echo "  No CU files found for wave $wave, skipping"
        set wave (math $wave + 1)
        continue
    end

    if test "$DRY_RUN" = true
        echo "  [dry-run] Would execute:"
        for cu_file in $cu_files
            echo "    - "(basename "$cu_file")
        end
        set wave (math $wave + 1)
        continue
    end

    # Run parallel coders for this wave
    echo "  Launching "(count $cu_files)" parallel coder(s)..."
    echo ""

    run_coder_parallel $cu_files
    set -l coder_status $status

    echo ""

    # Code lead review
    echo "  Launching code lead review..."
    echo ""

    run_code_lead "$wave" "$ITR_PATH" "$FEEDBACK_DIR" "$LEAD_MODEL" "$LOG_DIR" "$MAX_FIX_ITERATIONS"
    set -l lead_status $status

    echo ""

    # Check for escalations
    set -l escalations 0
    for cu_id in $wave_cus
        set -l feedback_file "$FEEDBACK_DIR/$cu_id.feedback.txt"
        if test -f "$feedback_file"
            if grep -q "STATUS=ESCALATION" "$feedback_file" 2>/dev/null
                set escalations (math $escalations + 1)
                echo "  ⚠ Escalation in $cu_id"
            end
        end
    end

    # Update convergence file
    set -l wave_cus_json (echo "$wave_cus" | jq -R . | jq -s .)
    jq ".waves += [{\"wave\": $wave, \"cus\": $wave_cus_json, \"escalations\": $escalations, \"coder_status\": $coder_status, \"lead_status\": $lead_status}]" "$CONVERGENCE_FILE" > "$CONVERGENCE_FILE.tmp"
    mv "$CONVERGENCE_FILE.tmp" "$CONVERGENCE_FILE"

    if test $escalations -gt 0
        echo "  ⚠ Wave $wave completed with $escalations escalation(s)"
        echo "  Code lead should have addressed these"
    else
        echo "  ✓ Wave $wave completed cleanly"
    end

    echo ""
    set wave (math $wave + 1)
end

# ── Final E2E verification ────────────────────────────────────────

if test "$DRY_RUN" = false
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Final E2E Verification"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""

    set -l e2e_cu "$ITR_PATH/cu-e2e-verification.itr"
    if test -f "$e2e_cu"
        echo "  Running E2E tests..."
        echo ""

        run_code_lead "e2e" "$ITR_PATH" "$FEEDBACK_DIR" "$LEAD_MODEL" "$LOG_DIR" "$MAX_FIX_ITERATIONS"
        set -l e2e_status $status

        if test $e2e_status -eq 0
            echo "  ✓ E2E verification passed"
        else
            echo "  ✗ E2E verification failed"
        end
    else
        echo "  No E2E verification CU found, skipping"
    end

    echo ""
end

# ── Summary ───────────────────────────────────────────────────────

echo "═══════════════════════════════════════════════════════════════"
echo "  Summary"
echo "═══════════════════════════════════════════════════════════════"
echo ""

if test "$DRY_RUN" = true
    echo "  [dry-run] No changes made"
else
    echo "  Waves executed: $num_waves"
    echo "  Feedback files: "(ls "$FEEDBACK_DIR"/*.feedback.txt 2>/dev/null | wc -l)
    echo "  Log files: $LOG_DIR"
    echo "  Convergence: $CONVERGENCE_FILE"
end

echo ""
echo "Done."
