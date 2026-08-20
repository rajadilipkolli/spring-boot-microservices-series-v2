#!/usr/bin/env bash
set -e

ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"

echo "Applying dev overlay..."
kubectl apply -k overlays/dev/

echo "Waiting for rollouts..."
kubectl rollout status statefulset/postgresql -n retailstore --timeout="$ROLLOUT_TIMEOUT"
kubectl rollout status deployment/redis -n retailstore --timeout="$ROLLOUT_TIMEOUT"
kubectl rollout status statefulset/kafka -n retailstore --timeout="$ROLLOUT_TIMEOUT"
kubectl rollout status deployment/keycloak -n retailstore --timeout="$ROLLOUT_TIMEOUT"

kubectl rollout status deployment/config-server -n retailstore --timeout="$ROLLOUT_TIMEOUT"
kubectl rollout status deployment/service-registry -n retailstore --timeout="$ROLLOUT_TIMEOUT"

for svc in catalog-service inventory-service order-service payment-service api-gateway retail-store-webapp; do
  kubectl rollout status deployment/$svc -n retailstore --timeout="$ROLLOUT_TIMEOUT"
done

echo "Deployment complete."
echo "Access URLs:"
echo "Webapp: http://retailstore.local"
echo "Keycloak: http://keycloak.local"
echo "JobRunr: http://jobrunr.local/dashboard"
