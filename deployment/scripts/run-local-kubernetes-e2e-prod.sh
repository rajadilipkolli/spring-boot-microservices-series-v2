#!/usr/bin/env bash
set -Eeuo pipefail

###############################################################################
# Configuration
###############################################################################

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

if command -v cygpath >/dev/null 2>&1; then
  PROJECT_ROOT="$(cygpath -m "$PROJECT_ROOT")"
fi


KIND_CONFIG="$PROJECT_ROOT/deployment/k8s/kind-config.yaml"
CI_OVERLAY="$PROJECT_ROOT/deployment/k8s/overlays/prod"
E2E_SCRIPT="$PROJECT_ROOT/test-em-all.sh"

CLUSTER_NAME="kind"
NAMESPACE="retailstore"

HOSTS_ENTRY="127.0.0.1 retailstore.local api.retailstore.local keycloak.local jobrunr.local"

INGRESS_MANIFEST="https://raw.githubusercontent.com/kubernetes/ingress-nginx/64780b1fed3af99f4eccbc3fdad7ad785e8a83b6/deploy/static/provider/kind/deploy.yaml"

POD_CREATION_TIMEOUT_SECONDS="${POD_CREATION_TIMEOUT_SECONDS:-300}"

SKIP_CLUSTER=false
TEARDOWN=false

###############################################################################
# Configuration validation
###############################################################################

if [[ ! "$POD_CREATION_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  printf 'FAIL: POD_CREATION_TIMEOUT_SECONDS must be a positive integer.\n' >&2
  exit 1
fi

read -r -a hosts_entry_fields <<< "$HOSTS_ENTRY"
HOST_ALIASES=("${hosts_entry_fields[@]:1}")

###############################################################################
# Docker images
###############################################################################

IMAGES=(
  "dockertmt/mmv2-config-server:0.0.1-SNAPSHOT"
  "dockertmt/mmv2-service-registry-25:0.0.1-SNAPSHOT"
  "dockertmt/mmv2-api-gateway:0.0.1-SNAPSHOT"
  "dockertmt/mmv2-catalog-service:0.0.1-SNAPSHOT"
  "dockertmt/mmv2-inventory-service:0.0.1-SNAPSHOT"
  "dockertmt/mmv2-order-service:0.0.1-SNAPSHOT"
  "dockertmt/mmv2-payment-service:0.0.1-SNAPSHOT"
  "dockertmt/mmv2-retail-store-webapp:0.0.1-SNAPSHOT"
)

###############################################################################
# Helper functions
###############################################################################

step() {
  printf '\n=== %s ===\n' "$1"
}

ok() {
  printf 'OK: %s\n' "$1"
}

warn() {
  printf 'WARN: %s\n' "$1" >&2
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

###############################################################################
# Usage
###############################################################################

usage() {
  cat <<'EOF'
Usage: ./run-local-kubernetes-e2e.sh [options]

Options:
  --skip-cluster  Reuse the existing Kind cluster.
  --teardown      Delete the Kind cluster and remove local host entries.
  --help          Show this help.
EOF
}

###############################################################################
# Arguments
###############################################################################

while (($# > 0)); do
  case "$1" in
    --skip-cluster)
      SKIP_CLUSTER=true
      ;;
    --teardown)
      TEARDOWN=true
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac

  shift
done

###############################################################################
# Required commands
###############################################################################

require_commands() {
  local command_name

  for command_name in "$@"; do
    command -v "$command_name" >/dev/null 2>&1 ||
      fail "Required command '$command_name' was not found."
  done
}

###############################################################################
# Hosts file helpers
###############################################################################

hosts_file_contains_alias() {
  local alias_regex

  alias_regex="${1//./\\.}"

  grep -Eq \
    "(^|[[:space:]])${alias_regex}([[:space:]]|$)" \
    /etc/hosts
}

hosts_file_contains_all_aliases() {
  local alias

  for alias in "${HOST_ALIASES[@]}"; do
    if ! hosts_file_contains_alias "$alias"; then
      return 1
    fi
  done

  return 0
}

add_hosts_entry() {
  local alias

  if hosts_file_contains_all_aliases; then
    warn "Hosts entries already present; skipping."
    return
  fi

  printf '%s\n' "$HOSTS_ENTRY" |
    sudo tee -a /etc/hosts >/dev/null ||
    fail "Could not add local aliases to /etc/hosts."

  for alias in "${HOST_ALIASES[@]}"; do
    hosts_file_contains_alias "$alias" ||
      fail "Alias '$alias' was not added to /etc/hosts."
  done

  ok "Added and verified /etc/hosts entries."
}

remove_hosts_entry() {
  local alias
  local alias_regex
  local sed_args=()

  for alias in "${HOST_ALIASES[@]}"; do
    alias_regex="${alias//./\\.}"
    sed_args+=(
      -e
      "/(^|[[:space:]])${alias_regex}([[:space:]]|$)/d"
    )
  done

  sudo sed -i -E \
    "${sed_args[@]}" \
    /etc/hosts ||
    fail "Could not remove local aliases from /etc/hosts."

  for alias in "${HOST_ALIASES[@]}"; do
    if hosts_file_contains_alias "$alias"; then
      fail "Alias '$alias' remains in /etc/hosts after removal."
    fi
  done

  ok "Removed and verified local hosts entries."
}

###############################################################################
# Wait for pod creation
###############################################################################

wait_for_pod_creation() {
  local label="$1"
  local description="$2"
  local deadline=$((SECONDS + POD_CREATION_TIMEOUT_SECONDS))

  until kubectl get pods \
    -n "$NAMESPACE" \
    -l "$label" \
    --no-headers \
    2>/dev/null |
    awk 'NF { found=1 } END { exit(found ? 0 : 1) }'
  do

    if ((SECONDS >= deadline)); then

      warn \
        "Timed out after ${POD_CREATION_TIMEOUT_SECONDS}s waiting for ${description} pod creation."

      printf '%s\n' \
        "Current pod status:" >&2

      kubectl get pods \
        -n "$NAMESPACE" \
        -o wide >&2 ||
        true

      printf '%s\n' \
        "Recent namespace events:" >&2

      kubectl get events \
        -n "$NAMESPACE" \
        --sort-by='.lastTimestamp' >&2 ||
        true

      return 1
    fi

    printf \
      '  waiting for %s pods...\n' \
      "$description"

    sleep 2
  done
}

###############################################################################
# Kubernetes diagnostics
###############################################################################

collect_diagnostics() {
  local diagnostics_dir="k8s-diagnostics"

  mkdir -p "$diagnostics_dir"

  kubectl get pods -A \
    > "$diagnostics_dir/pods.txt" \
    2>&1 ||
    true

  kubectl get events \
    -n "$NAMESPACE" \
    > "$diagnostics_dir/events.txt" \
    2>&1 ||
    true

  kubectl describe pods \
    -n "$NAMESPACE" \
    > "$diagnostics_dir/pods-describe.txt" \
    2>&1 ||
    true

  kubectl get deployments \
    -n "$NAMESPACE" \
    > "$diagnostics_dir/deployments.txt" \
    2>&1 ||
    true

  kubectl get services \
    -n "$NAMESPACE" \
    > "$diagnostics_dir/services.txt" \
    2>&1 ||
    true

  while IFS= read -r pod; do

    local filename

    filename="${pod//\//_}"

    kubectl logs "$pod" \
      -n "$NAMESPACE" \
      --all-containers \
      > "$diagnostics_dir/$filename.log" \
      2>&1 ||
      true

  done < <(
    kubectl get pods \
      -n "$NAMESPACE" \
      -o name \
      2>/dev/null ||
      true
  )

  warn "Diagnostics written to ./$diagnostics_dir/"
}

###############################################################################
# E2E script preparation
###############################################################################

prepare_e2e_script() {
  local source_script="$1"
  local prepared_script="$2"

  if [[ ! -f "$source_script" ]]; then
    fail "E2E script was not found: $source_script"
  fi

  if [[ ! -r "$source_script" ]]; then
    fail "E2E script is not readable: $source_script"
  fi

  # Convert CRLF -> LF into a temporary file.
  #
  # This is particularly important when the repository is checked out on
  # Windows and the shell script contains CRLF line endings.
  #
  # The original source file is NOT modified.
  if command -v dos2unix >/dev/null 2>&1; then

    dos2unix \
      < "$source_script" \
      > "$prepared_script" \
      2>/dev/null ||
      cp "$source_script" "$prepared_script"

  else

    tr -d '\r' \
      < "$source_script" \
      > "$prepared_script"

  fi

  chmod +x "$prepared_script" 2>/dev/null || true

  # Verify syntax before execution.
  if ! "${BASH:-bash}" -n "$prepared_script"; then
    fail "E2E script contains a Bash syntax error: $source_script"
  fi
}

###############################################################################
# E2E script diagnostics
###############################################################################

collect_e2e_diagnostics() {
  local diagnostics_dir="k8s-diagnostics"

  mkdir -p "$diagnostics_dir"

  {
    printf '%s\n' "========================================"
    printf '%s\n' "E2E FAILURE DIAGNOSTICS"
    printf '%s\n' "========================================"
    printf '\n'

    printf 'Date: %s\n' "$(date)"
    printf 'Bash version: %s\n' "${BASH_VERSION:-unknown}"
    printf 'Bash executable: %s\n' "$(command -v bash || true)"
    printf 'Project root: %s\n' "$PROJECT_ROOT"
    printf 'E2E script: %s\n' "$E2E_SCRIPT"
    printf '\n'

    printf '%s\n' "E2E script file:"
    ls -la "$E2E_SCRIPT" 2>&1 || true
    printf '\n'

    printf '%s\n' "File type:"
    file "$E2E_SCRIPT" 2>&1 || true
    printf '\n'

    printf '%s\n' "E2E script first 10 lines:"
    sed -n '1,10p' "$E2E_SCRIPT" 2>&1 || true
    printf '\n'

    printf '%s\n' "Environment:"
    printf 'HOST=%s\n' "${HOST:-}"
    printf 'PORT=%s\n' "${PORT:-}"
    printf '\n'

    printf '%s\n' "PATH:"
    printf '%s\n' "$PATH"

  } > "$diagnostics_dir/e2e-launch-diagnostics.txt"

  warn \
    "E2E launch diagnostics written to ./$diagnostics_dir/e2e-launch-diagnostics.txt"
}

###############################################################################
# TEARDOWN
###############################################################################

if [[ "$TEARDOWN" == true ]]; then

  require_commands \
    kind \
    sudo

  step "Tearing down Kind cluster"

  kind delete cluster \
    --name "$CLUSTER_NAME" ||
    true

  remove_hosts_entry

  ok "Teardown complete."

  exit 0
fi

###############################################################################
# VERIFY TOOLS
###############################################################################

step "Verifying required tools"

require_commands \
  bash \
  docker \
  kind \
  kubectl \
  jq \
  curl \
  sudo \
  awk \
  tr \
  tee

ok "All tools found."

###############################################################################
# Verify E2E script
###############################################################################

step "Validating end-to-end test script"

if [[ ! -f "$E2E_SCRIPT" ]]; then
  fail "E2E test script not found: $E2E_SCRIPT"
fi

if [[ ! -r "$E2E_SCRIPT" ]]; then
  fail "E2E test script is not readable: $E2E_SCRIPT"
fi

printf '  E2E script: %s\n' "$E2E_SCRIPT"

E2E_WORK_DIR="$(mktemp -d ./.retailstore-e2e.XXXXXX)"

# Always clean up the temporary E2E script.
cleanup_e2e_temp() {
  rm -rf "$E2E_WORK_DIR" 2>/dev/null || true
}

trap cleanup_e2e_temp EXIT

E2E_PREPARED_SCRIPT="$E2E_WORK_DIR/test-em-all.sh"

prepare_e2e_script \
  "$E2E_SCRIPT" \
  "$E2E_PREPARED_SCRIPT"

ok "E2E script is present and Bash syntax is valid."

###############################################################################
# KIND CLUSTER
###############################################################################

if [[ "$SKIP_CLUSTER" != true ]]; then

  step "Creating Kind cluster '$CLUSTER_NAME'"

  kind delete cluster \
    --name "$CLUSTER_NAME" \
    >/dev/null 2>&1 ||
    true

  kind create cluster \
    --name "$CLUSTER_NAME" \
    --config "$KIND_CONFIG" \
    --wait 120s

  ok "Cluster '$CLUSTER_NAME' is up."

else

  warn "Skipping cluster creation."

  if ! kind get clusters 2>/dev/null |
    grep -Fxq "$CLUSTER_NAME"
  then
    fail \
      "Kind cluster '$CLUSTER_NAME' does not exist, but --skip-cluster was specified."
  fi

fi

###############################################################################
# NGINX INGRESS
###############################################################################

step "Installing NGINX Ingress controller"

kubectl apply -f "$INGRESS_MANIFEST"

kubectl wait \
  --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

ok "NGINX Ingress controller is ready."

###############################################################################
# DOCKER IMAGES
###############################################################################

step "Pulling and loading Docker images"

for image in "${IMAGES[@]}"; do

  printf '  pulling %s ...\n' "$image"

  docker pull "$image"

  printf '  loading %s ...\n' "$image"

  kind load docker-image \
    "$image" \
    --name "$CLUSTER_NAME"

done

ok "All images loaded."

###############################################################################
# OPERATORS
###############################################################################

step "Installing Operators (Cert-Manager, CNPG, Strimzi)"

kubectl create namespace "$NAMESPACE" \
  --dry-run=client \
  -o yaml |
  kubectl apply -f -

###############################################################################
# CERT-MANAGER
###############################################################################

kubectl apply \
  -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.1/cert-manager.yaml

kubectl wait \
  --for=condition=ready pod \
  -l app.kubernetes.io/instance=cert-manager \
  -n cert-manager \
  --timeout=120s ||
  true

sleep 15

###############################################################################
# CLOUDNATIVE-PG
###############################################################################

kubectl apply \
  -f https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/c7be872e75719de35b6c84e97372dbb77e2df605/releases/cnpg-1.22.1.yaml

kubectl wait \
  --for=condition=ready pod \
  -l app.kubernetes.io/name=cloudnative-pg \
  -n cnpg-system \
  --timeout=120s ||
  true

sleep 15

###############################################################################
# STRIMZI
###############################################################################

kubectl apply \
  -f "$PROJECT_ROOT/deployment/k8s/overlays/prod/strimzi-operator.yaml"

kubectl wait \
  --for=condition=ready pod \
  -l name=strimzi-cluster-operator \
  -n "$NAMESPACE" \
  --timeout=120s ||
  true

sleep 15

###############################################################################
# KUSTOMIZE OVERLAY
###############################################################################

step "Applying Kustomize CI overlay"

kubectl apply \
  -k "$CI_OVERLAY"

ok "CI overlay applied."

###############################################################################
# INFRASTRUCTURE ROLLOUTS
###############################################################################

step "Waiting for infrastructure rollouts"

###############################################################################
# POSTGRES / CNPG
###############################################################################

wait_for_pod_creation \
  'cnpg.io/cluster=postgresql-ha,cnpg.io/podRole=instance' \
  'PostgreSQL'

kubectl wait \
  --for=condition=ready pod \
  -l 'cnpg.io/cluster=postgresql-ha,cnpg.io/podRole=instance' \
  -n "$NAMESPACE" \
  --timeout=300s

###############################################################################
# REDIS
###############################################################################

kubectl rollout status \
  deployment/redis \
  -n "$NAMESPACE" \
  --timeout=300s

###############################################################################
# KAFKA
###############################################################################

wait_for_pod_creation \
  'strimzi.io/cluster=kafka' \
  'Kafka'

kubectl wait \
  --for=condition=ready pod \
  -l 'strimzi.io/cluster=kafka' \
  -n "$NAMESPACE" \
  --timeout=300s

###############################################################################
# KEYCLOAK
###############################################################################

kubectl rollout status \
  deployment/keycloak \
  -n "$NAMESPACE" \
  --timeout=600s

###############################################################################
# WEBAPP HOST ALIASES
###############################################################################

step "Waiting for webapp hostAliases patch"

kubectl wait \
  --namespace "$NAMESPACE" \
  --for=condition=complete \
  job/patch-webapp-hostaliases \
  --timeout=600s

ok "Webapp hostAliases patch applied."

###############################################################################
# APPLICATION ROLLOUTS
###############################################################################

step "Waiting for platform and application rollouts"

for resource in \
  deployment/config-server \
  deployment/service-registry \
  deployment/catalog-service \
  deployment/inventory-service \
  deployment/order-service \
  deployment/payment-service \
  deployment/api-gateway \
  deployment/retail-store-webapp
do

  printf '  waiting for %s ...\n' "$resource"

  kubectl rollout status \
    "$resource" \
    -n "$NAMESPACE" \
    --timeout=600s

done

ok "All application services are ready."

###############################################################################
# FINAL POD READINESS GATE
###############################################################################

step "Waiting for all pods to be Running and fully ready"

pods_timeout_seconds=300
pods_elapsed=0

while true; do

  not_ready="$(
    kubectl get pods \
      -n "$NAMESPACE" \
      -o json \
      2>/dev/null |
      jq -r '
        [
          .items[]
          | select(
              .metadata.name
              | test("apicurio-registry")
              | not
            )
          | select(
              .status.phase != "Succeeded"
            )
          | select(
              (.status.phase != "Running")
              or
              (
                [
                  .status.containerStatuses[]?.ready
                ]
                | any(. == false)
              )
            )
        ]
        | length
      ' \
      2>/dev/null |
      tr -d '\r\n'
  )"

  if [[ ! "$not_ready" =~ ^[0-9]+$ ]]; then
    warn "Could not determine pod readiness; retrying..."
    not_ready=1
  fi

  if ((not_ready == 0)); then
    ok "All pods are Running and ready."
    break
  fi

  printf \
    '  %s pod(s) are not fully ready; elapsed=%ss/%ss\n' \
    "$not_ready" \
    "$pods_elapsed" \
    "$pods_timeout_seconds"

  if ((pods_elapsed >= pods_timeout_seconds)); then

    printf '\n'
    printf '%s\n' "Current pod status:"

    kubectl get pods \
      -n "$NAMESPACE" ||
      true

    printf '\n'
    printf '%s\n' "Current pod status (wide):"

    kubectl get pods \
      -n "$NAMESPACE" \
      -o wide ||
      true

    printf '\n'

    collect_diagnostics

    fail \
      "Timed out after ${pods_timeout_seconds}s waiting for all pods to be ready."

  fi

  sleep 5

  pods_elapsed=$((pods_elapsed + 5))

done

###############################################################################
# HOSTS
###############################################################################

step "Adding local host entries"

add_hosts_entry

###############################################################################
# E2E TEST SUITE
###############################################################################

step "Running end-to-end test suite"

E2E_LOG="k8s-diagnostics/e2e-test-output.log"

mkdir -p k8s-diagnostics

printf '  E2E source:   %s\n' "$E2E_SCRIPT"
printf '  E2E prepared: %s\n' "$E2E_PREPARED_SCRIPT"
printf '  E2E log:      %s\n' "$E2E_LOG"
printf '  Bash:         %s\n' "$(command -v bash)"
printf '  Bash version: %s\n' "${BASH_VERSION:-unknown}"

###############################################################################
# IMPORTANT WINDOWS/GIT-BASH FIX
#
# Do NOT do this:
#
#   HOST=api.retailstore.local PORT=80 "$E2E_SCRIPT" --no-cb-strict
#
# Direct execution can invoke the Windows Bash service layer and result in:
#
#   Catastrophic failure
#   Error code: Bash/Service/E_UNEXPECTED
#
# Instead, explicitly invoke Bash:
#
#   bash "$E2E_PREPARED_SCRIPT"
#
###############################################################################

set +e

(
  export HOST="api.retailstore.local"
  export PORT="443"
  export PROTOCOL="https"

  exec "${BASH:-bash}" \
    "$E2E_PREPARED_SCRIPT" \
    --no-cb-strict
) 2>&1 |
  tee "$E2E_LOG"

test_exit=${PIPESTATUS[0]}

set -e

###############################################################################
# E2E FAILURE
###############################################################################

if ((test_exit != 0)); then

  warn \
    "End-to-end test suite failed with exit code: $test_exit"

  warn \
    "E2E output saved to: $E2E_LOG"

  # Look specifically for the Windows Bash catastrophic failure.
  if grep -Eq \
    'Catastrophic failure|Bash/Service/E_UNEXPECTED' \
    "$E2E_LOG" 2>/dev/null
  then

    warn \
      "Detected Windows/Git Bash Bash/Service/E_UNEXPECTED while launching the E2E script."

    collect_e2e_diagnostics

  fi

  collect_diagnostics

  exit "$test_exit"

fi

ok "End-to-end test suite completed successfully."

###############################################################################
# SMOKE CHECKS
###############################################################################

step "Running smoke checks"

###############################################################################
# RETAIL STORE WEBAPP
###############################################################################

if curl \
  --silent \
  --fail \
  --location \
  --insecure \
  --output /dev/null \
  https://retailstore.local
then

  ok "retail-store-webapp returned HTTP 200."

else

  warn "retail-store-webapp smoke check failed."

fi

###############################################################################
# KEYCLOAK TOKEN ENDPOINT
###############################################################################

client_secret="$(
  kubectl get secret webapp-oauth2-credentials \
    -n "$NAMESPACE" \
    -o jsonpath='{.data.OAUTH2_CLIENT_SECRET}' \
    2>/dev/null |
    base64 -d \
    2>/dev/null ||
    true
)"

if [[ -z "$client_secret" ]]; then

  warn \
    "Could not retrieve Keycloak client secret from Secret " \
    "'webapp-oauth2-credentials' - skipping Keycloak smoke check."

elif curl \
  --silent \
  --fail \
  --location \
  --insecure \
  -X POST \
  https://keycloak.local/realms/retailstore/protocol/openid-connect/token \
  -d 'client_id=retailstore-webapp' \
  -d "client_secret=$client_secret" \
  -d 'grant_type=password' \
  -d 'username=retail' \
  -d 'password=retail1234' |
  grep -q access_token
then

  ok "Keycloak token endpoint returned an access token."

else

  warn "Keycloak token smoke check failed."

fi

###############################################################################
# COMPLETE
###############################################################################

ok "All E2E tests and smoke checks completed successfully."
