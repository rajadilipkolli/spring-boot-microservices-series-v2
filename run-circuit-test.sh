#!/usr/bin/env bash
# Simple runner to execute only the circuit breaker checks in test-em-all.sh
# Usage: ./run-circuit-test.sh

# Default host/port (can be overridden via env)
: ${HOST:=localhost}
: ${PORT:=8765}

# Enable strict circuit-breaker induction by default when using this wrapper.
: ${CB_STRICT:=true}
export CB_STRICT

echo "Running circuit breaker checks (CB_STRICT=${CB_STRICT}) against http://${HOST}:${PORT}"
bash test-em-all.sh circuit-test
