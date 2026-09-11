#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="C:/tools/git/spring-boot-microservices-series-v2"
KIND_CONFIG="$PROJECT_ROOT/deployment/k8s/kind-config.yaml"
CI_OVERLAY="$PROJECT_ROOT/deployment/k8s/overlays/prod"
E2E_SCRIPT="$PROJECT_ROOT/test-em-all.sh"

CLUSTER_NAME="kind"
NAMESPACE="retailstore"
HOSTS_ENTRY="127.0.0.1 retailstore.local api.retailstore.local keycloak.local jobrunr.local"
INGRESS_MANIFEST="https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml"
SKIP_CLUSTER=false
TEARDOWN=false

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

# Prints a section heading for the current deployment step.
step() { printf '\n=== %s ===\n' "$1"; }
# Prints a successful status message.
ok() { printf 'OK: %s\n' "$1"; }
# Prints a warning message to standard error.
warn() { printf 'WARN: %s\n' "$1" >&2; }
# Prints a failure message to standard error and exits the script.
fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }

# Displays the supported command-line options.
usage() {
  cat <<'EOF'
Usage: ./run-local-kubernetes-e2e.sh [options]

Options:
  --skip-cluster  Reuse the existing Kind cluster.
  --teardown      Delete the Kind cluster and remove local host entries.
  --help          Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --skip-cluster) SKIP_CLUSTER=true ;;
    --teardown) TEARDOWN=true ;;
    --help) usage; exit 0 ;;
    *) fail "Unknown option: $1" ;;
  esac
  shift
done

# Verifies that every command passed as an argument is available.
require_commands() {
  local command_name
  for command_name in "$@"; do
    command -v "$command_name" >/dev/null 2>&1 || fail "Required command '$command_name' was not found. Rebuild the Codespace or install it first."
  done
}

# Adds the local retail-store hostnames to /etc/hosts when absent.
add_hosts_entry() {
  if grep -Eq '(^|[[:space:]])retailstore\.local([[:space:]]|$)' /etc/hosts; then
    warn "Hosts entries already present; skipping."
  else
    printf '%s\n' "$HOSTS_ENTRY" >> /etc/hosts 2>/dev/null || true
    ok "Added /etc/hosts entries."
  fi
}

# Removes the local retail-store hostnames from /etc/hosts.
remove_hosts_entry() {
  sed -i '\|retailstore\.local|d' /etc/hosts 2>/dev/null || true
  ok "Removed local hosts entries."
}

# Captures Kubernetes pod, event, and log diagnostics after a failure.
collect_diagnostics() {
  local diagnostics_dir="k8s-diagnostics"
  mkdir -p "$diagnostics_dir"
  kubectl get pods -A > "$diagnostics_dir/pods.txt" || true
  kubectl get events -n "$NAMESPACE" > "$diagnostics_dir/events.txt" || true
  kubectl describe pods -n "$NAMESPACE" > "$diagnostics_dir/pods-describe.txt" || true
  while IFS= read -r pod; do
    local filename
    filename="${pod//\//_}"
    kubectl logs "$pod" -n "$NAMESPACE" --all-containers > "$diagnostics_dir/$filename.log" 2>/dev/null || true
  done < <(kubectl get pods -n "$NAMESPACE" -o name 2>/dev/null || true)
  warn "Diagnostics written to ./$diagnostics_dir/"
}

if [[ "$TEARDOWN" == true ]]; then
  require_commands kind
  step "Tearing down Kind cluster"
  kind delete cluster --name "$CLUSTER_NAME" || true
  remove_hosts_entry
  ok "Teardown complete."
  exit 0
fi

step "Verifying required tools"
require_commands docker kind kubectl jq curl
ok "All tools found."

if [[ "$SKIP_CLUSTER" != true ]]; then
  step "Creating Kind cluster '$CLUSTER_NAME'"
  kind delete cluster --name "$CLUSTER_NAME" >/dev/null 2>&1 || true
  kind create cluster --name "$CLUSTER_NAME" --config "$KIND_CONFIG" --wait 120s
  ok "Cluster '$CLUSTER_NAME' is up."
else
  warn "Skipping cluster creation."
fi

step "Installing NGINX Ingress controller"
kubectl apply -f "$INGRESS_MANIFEST"
kubectl wait --namespace ingress-nginx --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller --timeout=120s
ok "NGINX Ingress controller is ready."

step "Pulling and loading Docker images"
for image in "${IMAGES[@]}"; do
  printf '  pulling %s ...\n' "$image"
  docker pull "$image"
  printf '  loading %s ...\n' "$image"
  kind load docker-image "$image" --name "$CLUSTER_NAME"
done
ok "All images loaded."

step "Installing Operators (Cert-Manager, CNPG, Strimzi)"
kubectl create namespace retailstore --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.1/cert-manager.yaml
kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=120s || true
sleep 15

kubectl apply -f https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.22/releases/cnpg-1.22.1.yaml
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=cloudnative-pg -n cnpg-system --timeout=120s || true
sleep 15

kubectl apply -f "$PROJECT_ROOT/deployment/k8s/overlays/prod/strimzi-operator.yaml"
kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n retailstore --timeout=120s || true
sleep 15

step "Applying Kustomize CI overlay"
kubectl apply -k "$CI_OVERLAY"
ok "CI overlay applied."

step "Waiting for infrastructure rollouts"
until kubectl get pod -l cnpg.io/cluster=postgresql-ha,cnpg.io/podRole=instance -n "$NAMESPACE" | grep postgresql; do sleep 2; done
kubectl wait --for=condition=ready pod -l cnpg.io/cluster=postgresql-ha,cnpg.io/podRole=instance -n "$NAMESPACE" --timeout=300s
kubectl rollout status deployment/redis -n "$NAMESPACE" --timeout=300s

until kubectl get pod -l strimzi.io/cluster=kafka -n "$NAMESPACE" | grep kafka; do sleep 2; done
kubectl wait --for=condition=ready pod -l strimzi.io/cluster=kafka -n "$NAMESPACE" --timeout=300s
kubectl rollout status deployment/keycloak -n "$NAMESPACE" --timeout=600s

step "Waiting for webapp hostAliases patch"
# This Job patches retail-store-webapp's pod template (hostAliases for
# keycloak.local). It internally waits for a keycloak pod to be Ready before
# patching. We wait here AFTER Keycloak has rolled out successfully, so the
# job completes almost immediately at this point.
kubectl wait --namespace "$NAMESPACE" --for=condition=complete job/patch-webapp-hostaliases --timeout=600s
ok "Webapp hostAliases patch applied."

step "Waiting for platform and application rollouts"
for resource in \
  deployment/config-server \
  deployment/service-registry \
  deployment/catalog-service \
  deployment/inventory-service \
  deployment/order-service \
  deployment/payment-service \
  deployment/api-gateway \
  deployment/retail-store-webapp; do
  kubectl rollout status "$resource" -n "$NAMESPACE" --timeout=600s
done
ok "All application services are ready."

step "Waiting for all pods to be Running and fully ready"
# Final gate before running tests: every pod (excluding completed Job pods
# like patch-webapp-hostaliases) must be Running with all containers ready,
# regardless of which resource is still settling.
pods_timeout_seconds=300
pods_elapsed=0
while true; do
  not_ready=$(kubectl get pods -n "$NAMESPACE" -o json | jq -r '
    [.items[] | select(.metadata.name | test("apicurio-registry") | not)
      | select(.status.phase != "Succeeded")
      | select(
          (.status.phase != "Running") or
          ([.status.containerStatuses[]?.ready] | any(. == false))
        )
      | .metadata.name
    ] | length')
  if [ "$not_ready" -eq 0 ]; then
    ok "All pods are Running and ready."
    break
  fi
  if [ "$pods_elapsed" -ge "$pods_timeout_seconds" ]; then
    kubectl get pods -n "$NAMESPACE"
    fail "Timed out after ${pods_timeout_seconds}s waiting for all pods to be ready."
  fi
  sleep 5
  pods_elapsed=$((pods_elapsed + 5))
done

step "Adding local host entries"
add_hosts_entry

step "Running end-to-end test suite"
set +e
HOST=api.retailstore.local PORT=80 "$E2E_SCRIPT" --no-cb-strict
test_exit=$?
set -e

if ((test_exit == 0)); then
  step "Running smoke checks"
  if curl --silent --fail --output /dev/null http://retailstore.local; then
    ok "retail-store-webapp returned HTTP 200."
  else
    warn "retail-store-webapp smoke check failed."
  fi

  # Fetch the client secret from the cluster Secret rather than hard-coding it.
  client_secret="$(kubectl get secret webapp-oauth2-credentials -n "$NAMESPACE" -o jsonpath='{.data.OAUTH2_CLIENT_SECRET}' 2>/dev/null | base64 -d 2>/dev/null || true)"
  if [[ -z "$client_secret" ]]; then
    warn "Could not retrieve Keycloak client secret from Secret 'webapp-oauth2-credentials' - skipping Keycloak smoke check."
  elif curl --silent --fail -X POST http://keycloak.local/realms/retailstore/protocol/openid-connect/token \
    -d 'client_id=retailstore-webapp' \
    -d "client_secret=$client_secret" \
    -d 'grant_type=password' \
    -d 'username=retail' \
    -d 'password=retail1234' | grep -q access_token; then
    ok "Keycloak token endpoint returned an access token."
  else
    warn "Keycloak token smoke check failed."
  fi
  ok "All E2E tests passed."
else
  warn "Some tests failed; collecting diagnostics."
  collect_diagnostics
  exit "$test_exit"
fi
