#!/usr/bin/env bash
set -e

echo "Destroying prod overlay..."
# Delete Kustomize resources first so that operators can cleanly finalize their operands (Kafka, CNPG)
kubectl delete -k overlays/prod/ --ignore-not-found=true

echo "Destroying Strimzi operator..."
kubectl delete -f overlays/prod/strimzi-operator.yaml --ignore-not-found=true

# Optionally clean up any persistent volume claims to ensure a completely clean slate for the next deploy
echo "Cleaning up persistent volumes..."
kubectl delete pvc --all -n retailstore --ignore-not-found=true

echo "Waiting for namespace deletion to complete..."
UNDEPLOY_TIMEOUT="${UNDEPLOY_TIMEOUT:-300}"
elapsed=0
while true; do
  output=$(kubectl get namespace retailstore 2>&1 || true)
  if echo "$output" | grep -qi "NotFound"; then
    break
  fi
  if [ "$elapsed" -ge "$UNDEPLOY_TIMEOUT" ]; then
    echo "Timed out after $UNDEPLOY_TIMEOUTs waiting for namespace deletion." >&2
    # Force delete the namespace finalizers if it gets stuck (common with operators)
    echo "Force clearing namespace finalizers..."
    kubectl get namespace retailstore -o json | jq '.spec.finalizers=[]' > ns-without-finalizers.json
    kubectl replace --raw /api/v1/namespaces/retailstore/finalize -f ./ns-without-finalizers.json || true
    rm -f ns-without-finalizers.json
    exit 1
  fi
  echo "Namespace still terminating, waiting..."
  sleep 5
  elapsed=$((elapsed + 5))
done

echo "Production undeployment complete."
