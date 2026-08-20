#!/usr/bin/env bash
set -Eeuo pipefail

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

step() { printf '\n=== %s ===\n' "$1"; }
ok() { printf 'OK: %s\n' "$1"; }
warn() { printf 'WARN: %s\n' "$1" >&2; }
fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }

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

require_commands() {
  local command_name
  for command_name in "$@"; do
    command -v "$command_name" >/dev/null 2>&1 || fail "Required command '$command_name' was not found. Rebuild the Codespace or install it first."
  done
}

add_hosts_entry() {
  if grep -Eq '(^|[[:space:]])retailstore\.local([[:space:]]|$)' /etc/hosts; then
    warn "Hosts entries already present; skipping."
  else
    printf '%s\n' "$HOSTS_ENTRY" | sudo tee -a /etc/hosts >/dev/null
    ok "Added /etc/hosts entries."
  fi
}

remove_hosts_entry() {
  sudo sed -i '\|retailstore\.local|d' /etc/hosts
  ok "Removed local hosts entries."
}

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
  require_commands kind sudo
  step "Tearing down Kind cluster"
  kind delete cluster --name "$CLUSTER_NAME" || true
  remove_hosts_entry
  ok "Teardown complete."
  exit 0
fi

step "Verifying required tools"
require_commands docker kind kubectl jq curl sudo
ok "All tools found."

if [[ "$SKIP_CLUSTER" != true ]]; then
  step "Creating Kind cluster '$CLUSTER_NAME'"
  kind delete cluster --name "$CLUSTER_NAME" >/dev/null 2>&1 || true
  kind create cluster --name "$CLUSTER_NAME" --config deployment/k8s/kind-config.yaml --wait 120s
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

step "Applying Kustomize CI overlay"
kubectl apply -k deployment/k8s/overlays/ci/
ok "CI overlay applied."

step "Waiting for infrastructure rollouts"
for resource in \
  statefulset/postgresql \
  deployment/redis \
  statefulset/kafka \
  deployment/keycloak; do
  kubectl rollout status "$resource" -n "$NAMESPACE" --timeout=300s
done

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
  kubectl rollout status "$resource" -n "$NAMESPACE" --timeout=300s
done
ok "All application services are ready."

step "Adding local host entries"
add_hosts_entry

step "Running end-to-end test suite"
set +e
HOST=api.retailstore.local PORT=80 ./test-em-all.sh --no-cb-strict
test_exit=$?
set -e

if ((test_exit == 0)); then
  step "Running smoke checks"
  if curl --silent --fail --output /dev/null http://retailstore.local; then
    ok "retail-store-webapp returned HTTP 200."
  else
    warn "retail-store-webapp smoke check failed."
  fi

  if curl --silent --fail -X POST http://keycloak.local/realms/retailstore/protocol/openid-connect/token \
    -d 'client_id=retailstore-webapp' \
    -d 'grant_type=password' \
    -d 'username=alice' \
    -d 'password=alice' | grep -q access_token; then
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
