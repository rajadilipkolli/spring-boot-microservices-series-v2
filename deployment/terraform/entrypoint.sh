#!/usr/bin/env sh
# ---------------------------------------------------------------------------
# entrypoint.sh — Terraform runner entrypoint
#
# 1. Waits for Keycloak's /health/ready endpoint to return HTTP 200.
# 2. Initialises Terraform (downloads provider into /tmp/.terraform).
# 3. Plans and applies the keycloak module.
#
# All Terraform variables are expected to be present as TF_VAR_* env vars
# injected by the Kubernetes Job manifest.
# ---------------------------------------------------------------------------
set -eu

KEYCLOAK_URL="${TF_VAR_keycloak_url:-http://keycloak:8080}"
# Keycloak exposes health checks on management port 9000
HEALTH_URL="http://keycloak:9000/health/ready"
MAX_WAIT=300   # seconds
INTERVAL=5

echo "[entrypoint] Waiting for Keycloak to be ready at ${HEALTH_URL} ..."
elapsed=0
until wget -qO- "${HEALTH_URL}" 2>/dev/null | grep -q '"status".*"UP"'; do
  if [ "$elapsed" -ge "$MAX_WAIT" ]; then
    echo "[entrypoint] ERROR: Keycloak not ready after ${MAX_WAIT}s." >&2
    exit 1
  fi
  echo "[entrypoint]   not ready yet, sleeping ${INTERVAL}s (${elapsed}/${MAX_WAIT}s elapsed)..."
  sleep "$INTERVAL"
  elapsed=$((elapsed + INTERVAL))
done
echo "[entrypoint] Keycloak is ready."

# Terraform init: write provider cache + state files into the writable /tmp
# volume so the read-only root filesystem constraint is satisfied.
# Write provider cache + state files into /tmp so the read-only root filesystem
# and non-root volume mount constraints are satisfied.
export TF_DATA_DIR="/tmp/.terraform"

echo "[entrypoint] Running terraform init ..."
terraform init -input=false

echo "[entrypoint] Running terraform plan ..."
terraform plan -input=false -out=/tmp/tfplan

echo "[entrypoint] Running terraform apply ..."
terraform apply -input=false /tmp/tfplan

echo "[entrypoint] Terraform apply complete."
