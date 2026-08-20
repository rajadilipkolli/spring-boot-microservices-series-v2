#!/usr/bin/env bash
set -e

echo "Destroying dev overlay..."
kubectl delete -k overlays/dev/ --ignore-not-found=true

echo "Waiting for namespace deletion to complete..."
while kubectl get namespace retailstore > /dev/null 2>&1; do
  echo "Namespace still terminating, waiting..."
  sleep 5
done

echo "Undeployment complete."
