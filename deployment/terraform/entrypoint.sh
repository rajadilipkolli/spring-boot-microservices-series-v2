#!/usr/bin/env sh
# ---------------------------------------------------------------------------
# entrypoint.sh — Terraform runner entrypoint
#
# 1. Waits for Keycloak's /health/ready endpoint to return HTTP 200.
# 2. Initialises Terraform (downloads provider into /tmp/.terraform).
# 3. Plans and applies the keycloak module.
# 4. Provisions seed-user passwords through the Keycloak Admin API.
#
# Terraform and password inputs are injected by the Kubernetes Job manifest.
# ---------------------------------------------------------------------------
set -eu

# Keycloak exposes health checks on management port 9000
HEALTH_URL="http://keycloak:9000/health/ready"
MAX_WAIT=300   # seconds
INTERVAL=5

echo "[entrypoint] Waiting for Keycloak to be ready at ${HEALTH_URL} ..."
elapsed=0
until curl -sf "${HEALTH_URL}" 2>/dev/null | grep -q '"status".*"UP"'; do
  if [ "$elapsed" -ge "$MAX_WAIT" ]; then
    echo "[entrypoint] ERROR: Keycloak not ready after ${MAX_WAIT}s." >&2
    exit 1
  fi
  echo "[entrypoint]   not ready yet, sleeping ${INTERVAL}s (${elapsed}/${MAX_WAIT}s elapsed)..."
  sleep "$INTERVAL"
  elapsed=$((elapsed + INTERVAL))
done
echo "[entrypoint] Keycloak is ready."

# Write provider cache + state files into /tmp so the read-only root filesystem
# and non-root volume mount constraints are satisfied.
export TF_DATA_DIR="/tmp/.terraform"
LOCK_TIMEOUT="60s"

echo "[entrypoint] Running terraform init ..."
terraform init -input=false -lockfile=readonly

echo "[entrypoint] Checking for legacy Keycloak provider state ..."
STATE_JSON=$(terraform state pull 2>/dev/null || true)
if [ -n "$STATE_JSON" ] && echo "$STATE_JSON" | jq -e '.resources[]? | select(.provider | contains("registry.terraform.io/mrparkers/keycloak"))' >/dev/null 2>&1; then
  echo "[entrypoint] Legacy mrparkers/keycloak provider found in state. Replacing provider..."
  terraform state replace-provider -auto-approve -lock-timeout="${LOCK_TIMEOUT}" \
    registry.terraform.io/mrparkers/keycloak registry.terraform.io/keycloak/keycloak
  echo "[entrypoint] Provider replacement complete."
else
  echo "[entrypoint] No legacy provider migration necessary."
fi

echo "[entrypoint] Running terraform plan ..."
terraform plan -input=false -lock-timeout="${LOCK_TIMEOUT}" -out=/tmp/tfplan

echo "[entrypoint] Running terraform apply ..."
terraform apply -input=false -lock-timeout="${LOCK_TIMEOUT}" /tmp/tfplan

echo "[entrypoint] Terraform apply complete."

echo "[entrypoint] Provisioning Keycloak user passwords outside Terraform ..."
/provision-keycloak-users.sh
echo "[entrypoint] Keycloak user password provisioning complete."
