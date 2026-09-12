#!/usr/bin/env bash
set -e

ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
POD_CREATION_TIMEOUT_SECONDS="${POD_CREATION_TIMEOUT_SECONDS:-300}"

if [[ ! "$POD_CREATION_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "POD_CREATION_TIMEOUT_SECONDS must be a positive integer." >&2
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
      echo "Current pod status:" >&2
      kubectl get pods -n retailstore -o wide >&2 || true
      echo "Recent namespace events:" >&2
      kubectl get events -n retailstore --sort-by='.lastTimestamp' >&2 || true
      return 1
    fi

    sleep 2
  done
}

echo "Creating namespace first..."
kubectl create namespace retailstore --dry-run=client -o yaml | kubectl apply -f -

echo "Installing Cert-Manager..."
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.1/cert-manager.yaml
kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout="$ROLLOUT_TIMEOUT" || true
# wait for webhook to be up
sleep 15

echo "Installing CloudNativePG..."
kubectl apply -f https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.22/releases/cnpg-1.22.1.yaml
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=cloudnative-pg -n cnpg-system --timeout="$ROLLOUT_TIMEOUT" || true
sleep 15


echo "Applying Strimzi operator explicitly (Operator before Operand)..."
# Operators manage CustomResourceDefinitions (CRDs). Applying them directly 
# ensures CRDs exist before Kustomize applies the Kafka cluster resource.
kubectl apply -f overlays/prod/strimzi-operator.yaml
kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n retailstore --timeout="$ROLLOUT_TIMEOUT" || true
sleep 15

echo "Applying prod overlay..."
kubectl apply -k overlays/prod/

echo "Waiting for rollouts..."

echo "Waiting for CNPG pod to be created..."
wait_for_pod_creation 'cnpg.io/cluster=postgresql-ha,cnpg.io/podRole=instance' 'CNPG'
kubectl wait --for=condition=ready pod -l cnpg.io/cluster=postgresql-ha,cnpg.io/podRole=instance -n retailstore --timeout="$ROLLOUT_TIMEOUT"
kubectl rollout status deployment/redis -n retailstore --timeout="$ROLLOUT_TIMEOUT"

echo "Waiting for Strimzi Kafka pod to be created..."
wait_for_pod_creation 'strimzi.io/cluster=kafka' 'Kafka'
kubectl wait --for=condition=ready pod -l strimzi.io/cluster=kafka -n retailstore --timeout="$ROLLOUT_TIMEOUT"
kubectl rollout status deployment/keycloak -n retailstore --timeout="$ROLLOUT_TIMEOUT"

echo "Waiting for infrastructure microservices..."
kubectl rollout status deployment/config-server -n retailstore --timeout="$ROLLOUT_TIMEOUT"
kubectl rollout status deployment/service-registry -n retailstore --timeout="$ROLLOUT_TIMEOUT"

echo "Waiting for business microservices..."
for svc in catalog-service inventory-service order-service payment-service api-gateway retail-store-webapp; do
  kubectl rollout status deployment/$svc -n retailstore --timeout="$ROLLOUT_TIMEOUT"
done

echo "Production Deployment complete."
echo "Access URLs:"
echo "Webapp (HTTPS): https://retailstore.local"
echo "Keycloak (HTTPS): https://keycloak.local"
