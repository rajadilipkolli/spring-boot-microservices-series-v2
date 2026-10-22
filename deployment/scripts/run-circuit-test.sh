#!/usr/bin/env bash
# Simple runner to execute circuit breaker checks in e2e-tests
# Usage: ./run-circuit-test.sh

# Default host/port (can be overridden via env)
: ${HOST:=localhost}
: ${PORT:=8765}

# Enable strict circuit-breaker induction by default when using this wrapper.
: ${CB_STRICT:=true}
export CB_STRICT

echo "Running circuit breaker checks against http://${HOST}:${PORT}"
./mvnw test -pl e2e-tests -Dtest=ResilienceE2ETest -DskipE2E=false -Dbase.url=http://${HOST}:${PORT}
