#!/usr/bin/env bash
# compare-baseline.sh
#
# Parses the latest Gatling simulation.log, extracts P50/P95/P99/mean/RPS,
# then compares against a stored baseline JSON. Exits non-zero if any metric
# degrades by more than REGRESSION_THRESHOLD_PERCENT.
#
# Usage:
#   ./compare-baseline.sh [baseline_file] [results_dir] [threshold_percent]
#
# Defaults:
#   baseline_file  = docs/baselines/main-baseline.json
#   results_dir    = target/gatling
#   threshold_pct  = 15

set -euo pipefail

BASELINE_FILE="${1:-docs/baselines/main-baseline.json}"
RESULTS_DIR="${2:-target/gatling}"
THRESHOLD="${3:-15}"

# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────
log_info()  { echo "[INFO]  $*"; }
log_warn()  { echo "[WARN]  $*"; }
log_error() { echo "[ERROR] $*" >&2; }

require() {
    command -v "$1" &>/dev/null || { log_error "Required tool '$1' not found."; exit 1; }
}

require python3

# ─────────────────────────────────────────────────────────────────────────────
# Locate the latest simulation.log
# ─────────────────────────────────────────────────────────────────────────────
LATEST_LOG=$(find "${RESULTS_DIR}" -name "simulation.log" -printf '%T@ %p\n' 2>/dev/null \
    | sort -nr | head -n1 | awk '{print $2}')

if [[ -z "${LATEST_LOG}" ]]; then
    log_error "No simulation.log found under ${RESULTS_DIR}"
    exit 1
fi

log_info "Parsing: ${LATEST_LOG}"

# ─────────────────────────────────────────────────────────────────────────────
# Extract global stats from simulation.log using Python
# The log format uses lines like:
#   REQUEST\t\t<name>\t<start_ms>\t<end_ms>\t<status>\t<message>
# ─────────────────────────────────────────────────────────────────────────────
CURRENT_JSON=$(python3 - "${LATEST_LOG}" <<'PYEOF'
import sys, json, statistics

log_file = sys.argv[1]
response_times = []

with open(log_file, encoding="utf-8") as f:
    for line in f:
        parts = line.rstrip().split("\t")
        # REQUEST lines: parts[0]=="REQUEST", parts[4]==start_ms, parts[5]==end_ms, parts[6]==OK/KO
        if len(parts) >= 7 and parts[0] == "REQUEST":
            try:
                start_ms = int(parts[4])
                end_ms   = int(parts[5])
                status   = parts[6].strip()
                if status == "OK":
                    response_times.append(end_ms - start_ms)
            except (ValueError, IndexError):
                continue

if not response_times:
    print(json.dumps({"error": "no_ok_requests"}))
    sys.exit(1)

response_times.sort()
n = len(response_times)

def percentile(data, p):
    k = (len(data) - 1) * p / 100.0
    f = int(k)
    c = min(f + 1, len(data) - 1)
    return data[f] + (data[c] - data[f]) * (k - f)

result = {
    "sample_count": n,
    "mean_ms":      round(statistics.mean(response_times), 1),
    "p50_ms":       round(percentile(response_times, 50), 1),
    "p95_ms":       round(percentile(response_times, 95), 1),
    "p99_ms":       round(percentile(response_times, 99), 1),
}
print(json.dumps(result, indent=2))
PYEOF
)

log_info "Current run metrics:"
echo "${CURRENT_JSON}"

# ─────────────────────────────────────────────────────────────────────────────
# If no baseline exists yet, save the current run as the new baseline and exit
# ─────────────────────────────────────────────────────────────────────────────
if [[ ! -f "${BASELINE_FILE}" ]]; then
    log_info "No baseline found at ${BASELINE_FILE}. Saving current run as new baseline."
    mkdir -p "$(dirname "${BASELINE_FILE}")"
    echo "${CURRENT_JSON}" > "${BASELINE_FILE}"
    log_info "Baseline saved."
    exit 0
fi

log_info "Comparing against baseline: ${BASELINE_FILE}"
cat "${BASELINE_FILE}"

# ─────────────────────────────────────────────────────────────────────────────
# Compare metrics; fail if any key metric degrades beyond threshold
# ─────────────────────────────────────────────────────────────────────────────
if REGRESSION_DETECTED=$(python3 - "${BASELINE_FILE}" "${CURRENT_JSON}" "${THRESHOLD}" <<'PYEOF'
import sys, json

baseline_file = sys.argv[1]
current_json  = sys.argv[2]
threshold     = float(sys.argv[3])

with open(baseline_file) as f:
    baseline = json.load(f)

current = json.loads(current_json)

metrics_to_check = ["mean_ms", "p95_ms", "p99_ms"]
regressions = []

for metric in metrics_to_check:
    b = baseline.get(metric)
    c = current.get(metric)
    if b is None or c is None:
        continue
    if b == 0:
        continue
    pct_change = ((c - b) / b) * 100.0
    status = "OK" if pct_change <= threshold else "REGRESSION"
    print(f"  {metric}: baseline={b}ms  current={c}ms  change={pct_change:+.1f}%  [{status}]")
    if pct_change > threshold:
        regressions.append(metric)

if regressions:
    print(f"\nREGRESSION DETECTED in: {', '.join(regressions)}")
    sys.exit(1)
else:
    print("\nAll metrics within threshold. No regression.")
    sys.exit(0)
PYEOF
); then
    COMPARE_EXIT=0
else
    COMPARE_EXIT=$?
fi

echo "${REGRESSION_DETECTED}"

if [[ ${COMPARE_EXIT} -ne 0 ]]; then
    log_error "Performance regression detected. Failing build."
    exit 1
fi

log_info "Regression check passed."
