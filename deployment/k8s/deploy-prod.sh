#!/usr/bin/env bash
set -e

ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"

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

echo "Waiting for CNPG PostgreSQL cluster to become Ready..."
kubectl wait cluster/postgresql-ha -n retailstore --for=condition=Ready --timeout="$ROLLOUT_TIMEOUT"
kubectl rollout status deployment/redis -n retailstore --timeout="$ROLLOUT_TIMEOUT"

echo "Waiting for Strimzi Kafka cluster to become Ready..."
kubectl wait kafka/kafka -n retailstore --for=condition=Ready --timeout="$ROLLOUT_TIMEOUT"
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
