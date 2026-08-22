#!/usr/bin/env bash
set -e

echo "Destroying dev overlay..."
kubectl delete -k overlays/dev/ --ignore-not-found=true

echo "Waiting for namespace deletion to complete..."
UNDEPLOY_TIMEOUT="${UNDEPLOY_TIMEOUT:-300}"
elapsed=0
while true; do
  output=$(kubectl get namespace retailstore 2>&1)
  status=$?
  if [ $status -eq 0 ]; then
    if [ "$elapsed" -ge "$UNDEPLOY_TIMEOUT" ]; then
      echo "Timed out after ${UNDEPLOY_TIMEOUT}s waiting for namespace deletion." >&2
      exit 1
    fi
    echo "Namespace still terminating, waiting..."
    sleep 5
    elapsed=$((elapsed + 5))
    continue
  fi
  if echo "$output" | grep -qi "NotFound"; then
    break
  fi
  echo "Failed to check namespace status: $output" >&2
  exit 1
done

echo "Undeployment complete."
