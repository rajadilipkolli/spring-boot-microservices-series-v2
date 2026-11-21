#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# undeploy.sh — Unified Kubernetes undeployment script for all overlays
#
# Usage:
#   ./undeploy.sh [dev|ci|staging|prod]
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OVERLAY="${1:-dev}"
UNDEPLOY_TIMEOUT="${UNDEPLOY_TIMEOUT:-300}"
UNINSTALL_ENVOY_GATEWAY="${UNINSTALL_ENVOY_GATEWAY:-false}"
ENVOY_GATEWAY_VERSION="${ENVOY_GATEWAY_VERSION:-v1.7.5}"

if [[ ! -d "${SCRIPT_DIR}/overlays/${OVERLAY}" ]]; then
  echo "ERROR: Unknown overlay '${OVERLAY}'. Directory '${SCRIPT_DIR}/overlays/${OVERLAY}' does not exist." >&2
  exit 1
fi

echo "=== Destroying overlay: '${OVERLAY}' ==="
kubectl delete -k "${SCRIPT_DIR}/overlays/${OVERLAY}/" --ignore-not-found=true

if [[ "$OVERLAY" == "prod" ]]; then
  echo "Destroying Strimzi operator..."
  kubectl delete -f "${SCRIPT_DIR}/overlays/prod/strimzi-operator.yaml" --ignore-not-found=true
  echo "Cleaning up persistent volumes..."
  kubectl delete pvc --all -n retailstore --ignore-not-found=true
fi

echo "Waiting for namespace deletion to complete..."
elapsed=0
while true; do
  output=$(kubectl get namespace retailstore 2>&1 || true)
  if echo "$output" | grep -qi "NotFound"; then
    break
  fi
  if [ "$elapsed" -ge "$UNDEPLOY_TIMEOUT" ]; then
    echo "Timed out after ${UNDEPLOY_TIMEOUT}s waiting for namespace deletion." >&2
    if command -v jq >/dev/null 2>&1; then
      echo "Force clearing namespace finalizers..."
      kubectl get namespace retailstore -o json | jq '.spec.finalizers=[]' > ns-without-finalizers.json
      kubectl replace --raw /api/v1/namespaces/retailstore/finalize -f ./ns-without-finalizers.json || true
      rm -f ns-without-finalizers.json
    fi
    exit 1
  fi
  echo "Namespace still terminating, waiting..."
  sleep 5
  elapsed=$((elapsed + 5))
done

if [[ "$UNINSTALL_ENVOY_GATEWAY" == "true" ]]; then
  echo "Opt-in Envoy Gateway uninstall requested. Deleting Envoy Gateway (${ENVOY_GATEWAY_VERSION})..."
  kubectl delete -f "https://github.com/envoyproxy/gateway/releases/download/${ENVOY_GATEWAY_VERSION}/install.yaml" --ignore-not-found=true
fi

echo "=== Undeployment of '${OVERLAY}' complete ==="
