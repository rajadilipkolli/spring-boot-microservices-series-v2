#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# deploy.sh — Unified Kubernetes deployment script for all overlays
#
# Usage:
#   ./deploy.sh [dev|ci|staging|prod]
#
# Examples:
#   ./deploy.sh          # Defaults to dev overlay
#   ./deploy.sh ci       # Deploys CI overlay
#   ./deploy.sh prod     # Deploys production overlay with operators and CRDs
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OVERLAY="${1:-dev}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
POD_CREATION_TIMEOUT_SECONDS="${POD_CREATION_TIMEOUT_SECONDS:-300}"

if [[ ! -d "${SCRIPT_DIR}/overlays/${OVERLAY}" ]]; then
  echo "ERROR: Unknown overlay '${OVERLAY}'. Directory '${SCRIPT_DIR}/overlays/${OVERLAY}' does not exist." >&2
  echo "Available overlays: $(ls -m "${SCRIPT_DIR}/overlays")" >&2
  exit 1
fi

wait_for_pod_creation() {
  local label="$1"
  local description="$2"
  local deadline=$((SECONDS + POD_CREATION_TIMEOUT_SECONDS))
  local remain

  until
    remain=$((deadline - SECONDS))
    if (( remain <= 0 )); then remain=1; fi
    kubectl get pods -n retailstore -l "$label" --request-timeout="${remain}s" --no-headers 2>/dev/null |
    awk 'NF { found=1 } END { exit(found ? 0 : 1) }'
  do
    if ((SECONDS >= deadline)); then
      echo "Timed out after ${POD_CREATION_TIMEOUT_SECONDS}s waiting for ${description} pod creation." >&2
      kubectl get pods -n retailstore -o wide >&2 || true
      kubectl get events -n retailstore --sort-by='.lastTimestamp' >&2 || true
      return 1
    fi
    sleep 2
  done
}

print_job_diagnostics() {
  local namespace="$1"
  local job_name="$2"

  echo "Job status:" >&2
  kubectl describe job "$job_name" -n "$namespace" >&2 || true
  echo "Job pods:" >&2
  kubectl get pods -n "$namespace" -l "job-name=$job_name" -o wide >&2 || true
  echo "Job logs:" >&2
  kubectl logs -n "$namespace" "job/$job_name" --all-containers=true >&2 || true
}

wait_for_job_terminal_condition() {
  local namespace="$1"
  local job_name="$2"
  local timeout_seconds="$3"
  local deadline=$((SECONDS + timeout_seconds))
  local complete_status
  local failed_status

  while ((SECONDS < deadline)); do
    failed_status="$(kubectl get job "$job_name" -n "$namespace" \
      -o 'jsonpath={.status.conditions[?(@.type=="Failed")].status}' 2>/dev/null || true)"
    complete_status="$(kubectl get job "$job_name" -n "$namespace" \
      -o 'jsonpath={.status.conditions[?(@.type=="Complete")].status}' 2>/dev/null || true)"

    if [[ "$failed_status" == "True" ]]; then
      echo "Job ${job_name} failed." >&2
      print_job_diagnostics "$namespace" "$job_name"
      return 1
    fi

    if [[ "$complete_status" == "True" ]]; then
      return 0
    fi

    sleep 2
  done

  echo "Timed out after ${timeout_seconds}s waiting for Job ${job_name}." >&2
  print_job_diagnostics "$namespace" "$job_name"
  return 1
}

echo "=== Deploying overlay: '${OVERLAY}' ==="

if [[ "$OVERLAY" == "prod" ]]; then
  echo "Ensuring retailstore namespace..."
  kubectl create namespace retailstore --dry-run=client -o yaml | kubectl apply -f -

  echo "Installing Cert-Manager (v1.16.1)..."
  kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.1/cert-manager.yaml
  kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout="$ROLLOUT_TIMEOUT" || true
  sleep 10

  echo "Installing CloudNativePG (v1.22.1)..."
  kubectl apply -f https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.22/releases/cnpg-1.22.1.yaml
  kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=cloudnative-pg -n cnpg-system --timeout="$ROLLOUT_TIMEOUT" || true
  sleep 10

  echo "Installing External Secrets Operator (v0.10.4)..."
  kubectl apply -f https://github.com/external-secrets/external-secrets/releases/download/v0.10.4/external-secrets.yaml
  kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=external-secrets -n external-secrets --timeout="$ROLLOUT_TIMEOUT" || true
  sleep 10

  echo "Applying Strimzi operator explicitly (CRDs & Operator before Custom Resources)..."
  kubectl apply -f "${SCRIPT_DIR}/overlays/prod/strimzi-operator.yaml"
  kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n retailstore --timeout="$ROLLOUT_TIMEOUT" || true
  sleep 10
fi

echo "Applying overlay '${OVERLAY}' via Kustomize..."
kubectl apply -k "${SCRIPT_DIR}/overlays/${OVERLAY}/"

# Wait for patch-webapp-hostaliases Job if present before rollout checks
if kubectl get job/patch-webapp-hostaliases -n retailstore >/dev/null 2>&1; then
  echo "Waiting for webapp hostAliases patch job..."
  wait_for_job_terminal_condition retailstore patch-webapp-hostaliases 300
fi

echo "Waiting for core infrastructure rollouts..."
if [[ "$OVERLAY" == "prod" ]]; then
  echo "Waiting for CNPG PostgreSQL cluster..."
  wait_for_pod_creation 'cnpg.io/cluster=postgresql-ha,cnpg.io/podRole=instance' 'CNPG'
  kubectl wait --for=condition=ready pod -l cnpg.io/cluster=postgresql-ha,cnpg.io/podRole=instance -n retailstore --timeout="$ROLLOUT_TIMEOUT"

  echo "Waiting for Strimzi Kafka cluster..."
  wait_for_pod_creation 'strimzi.io/cluster=kafka' 'Kafka'
  kubectl wait --for=condition=ready pod -l strimzi.io/cluster=kafka -n retailstore --timeout="$ROLLOUT_TIMEOUT"
else
  # Non-prod standard StatefulSets
  if kubectl get statefulset/postgresql -n retailstore >/dev/null 2>&1; then
    kubectl rollout status statefulset/postgresql -n retailstore --timeout="$ROLLOUT_TIMEOUT"
    kubectl wait --namespace retailstore --for=condition=ready pod --selector="app=postgresql" --timeout="$ROLLOUT_TIMEOUT"
  fi
  if kubectl get statefulset/kafka -n retailstore >/dev/null 2>&1; then
    kubectl rollout status statefulset/kafka -n retailstore --timeout="$ROLLOUT_TIMEOUT"
    kubectl wait --namespace retailstore --for=condition=ready pod --selector="app=kafka" --timeout="$ROLLOUT_TIMEOUT"
  fi
fi

if kubectl get deployment/redis -n retailstore >/dev/null 2>&1; then
  kubectl rollout status deployment/redis -n retailstore --timeout="$ROLLOUT_TIMEOUT"
  kubectl wait --namespace retailstore --for=condition=ready pod --selector="app=redis" --timeout="$ROLLOUT_TIMEOUT"
fi
if kubectl get deployment/keycloak -n retailstore >/dev/null 2>&1; then
  kubectl rollout status deployment/keycloak -n retailstore --timeout="$ROLLOUT_TIMEOUT"
  kubectl wait --namespace retailstore --for=condition=ready pod --selector="app=keycloak" --timeout="$ROLLOUT_TIMEOUT"
fi

# Wait for Keycloak Terraform runner Job if present
if kubectl get job/keycloak-terraform-runner -n retailstore >/dev/null 2>&1; then
  echo "Waiting for Keycloak Terraform runner Job..."
  wait_for_job_terminal_condition retailstore keycloak-terraform-runner 600
  echo "Keycloak Terraform runner complete."
fi

echo "Waiting for platform services..."
if kubectl get deployment/config-server -n retailstore >/dev/null 2>&1; then
  kubectl rollout status deployment/config-server -n retailstore --timeout="$ROLLOUT_TIMEOUT"
  kubectl wait --namespace retailstore --for=condition=ready pod --selector="app=config-server" --timeout="$ROLLOUT_TIMEOUT"
fi
if kubectl get deployment/service-registry -n retailstore >/dev/null 2>&1; then
  kubectl rollout status deployment/service-registry -n retailstore --timeout="$ROLLOUT_TIMEOUT"
  kubectl wait --namespace retailstore --for=condition=ready pod --selector="app=service-registry" --timeout="$ROLLOUT_TIMEOUT"
fi

echo "Waiting for business microservices..."
for svc in catalog-service inventory-service order-service payment-service api-gateway retail-store-webapp; do
  if kubectl get deployment/$svc -n retailstore >/dev/null 2>&1; then
    kubectl rollout status deployment/$svc -n retailstore --timeout="$ROLLOUT_TIMEOUT"
  fi
done

# Final check: ensure all pods in namespace are running and ready
echo "Verifying all pods in retailstore are Running and ready..."
timeout_seconds=300
elapsed=0
while true; do
  not_ready=$(kubectl get pods -n retailstore -o json | jq -r '
    [.items[] | select(.status.phase != "Succeeded")
      | select(
          (.status.phase != "Running") or
          ([.status.containerStatuses[]?.ready] | any(. == false))
        )
      | .metadata.name
    ] | length')
  if [ "$not_ready" -eq 0 ]; then
    echo "All pods are Running and ready."
    break
  fi
  if [ "$elapsed" -ge "$timeout_seconds" ]; then
    echo "Timed out after ${timeout_seconds}s waiting for all pods to be ready." >&2
    kubectl get pods -n retailstore
    exit 1
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done

echo "=== Deployment of '${OVERLAY}' complete ==="
if [[ "$OVERLAY" == "prod" ]]; then
  echo "Access URLs:"
  echo "  Webapp (HTTPS): https://retailstore.local"
  echo "  Keycloak (HTTPS): https://keycloak.local"
else
  echo "Access URLs:"
  echo "  Webapp: http://retailstore.local"
  echo "  Keycloak: http://keycloak.local"
  echo "  JobRunr: http://jobrunr.local/dashboard"
fi
