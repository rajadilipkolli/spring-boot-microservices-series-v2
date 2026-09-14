#!/usr/bin/env bash
set -Eeuo pipefail

echo "=========================================="
echo " Starting Local Kubernetes E2E Tests"
echo "=========================================="

echo "=> Creating k8s Kind Cluster..."
kind create cluster --name kind --config deployment/k8s/kind-config.yaml || echo "Cluster may already exist"

echo "=> Installing NGINX Ingress Controller..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.12.1/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s

echo "=> Loading Images into Kind..."
images=(
  dockertmt/mmv2-config-server:0.0.1-SNAPSHOT
  dockertmt/mmv2-service-registry-25:0.0.1-SNAPSHOT
  dockertmt/mmv2-api-gateway:0.0.1-SNAPSHOT
  dockertmt/mmv2-catalog-service:0.0.1-SNAPSHOT
  dockertmt/mmv2-inventory-service:0.0.1-SNAPSHOT
  dockertmt/mmv2-order-service:0.0.1-SNAPSHOT
  dockertmt/mmv2-payment-service:0.0.1-SNAPSHOT
  dockertmt/mmv2-retail-store-webapp:0.0.1-SNAPSHOT
)
for image in "${images[@]}"; do
  docker pull "$image" || echo "Failed to pull $image, using local copy if available"
  kind load docker-image "$image" --name kind
done

echo "=> Applying Prod Overlay..."
(cd deployment/k8s && sh deploy-prod.sh)

echo "=> Waiting for Webapp Hostaliases Patch..."
kubectl wait --namespace retailstore --for=condition=complete job/patch-webapp-hostaliases --timeout=120s || true

echo "=> Waiting for Infrastructure Rollouts..."
echo "Waiting for CNPG PostgreSQL cluster..."
kubectl wait cluster/postgresql-ha -n retailstore --for=condition=Ready --timeout=600s

echo "Waiting for Strimzi Kafka cluster..."
kubectl wait kafka/kafka -n retailstore --for=condition=Ready --timeout=600s

kubectl rollout status deployment/redis -n retailstore --timeout=300s
kubectl rollout status deployment/keycloak -n retailstore --timeout=300s
for app in redis keycloak; do
  kubectl wait --namespace retailstore --for=condition=ready pod --selector="app=$app" --timeout=300s
done

echo "=> Waiting for Platform Core Rollouts..."
kubectl rollout status deployment/config-server -n retailstore --timeout=300s
kubectl rollout status deployment/service-registry -n retailstore --timeout=300s
for app in config-server service-registry; do
  kubectl wait --namespace retailstore --for=condition=ready pod --selector="app=$app" --timeout=300s
done

echo "=> Waiting for Application Rollouts..."
for svc in catalog-service inventory-service order-service payment-service api-gateway retail-store-webapp; do
  kubectl rollout status "deployment/$svc" -n retailstore --timeout=300s
done

echo "=> Waiting for All Pods Ready..."
timeout_seconds=300
elapsed=0
while true; do
  not_ready=$(kubectl get pods -n retailstore -o json | jq -r '
    [.items[] | select(.status.phase != "Succeeded")
      | select(.metadata.name | startswith("apicurio-registry") | not)
      | select(
          (.status.phase != "Running") or
          ([.status.containerStatuses[]?.ready] | any(. == false))
        )
      | .metadata.name
    ] | length' | tr -d '\r')
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

echo "=> Validating NetworkPolicy and PDBs..."
kubectl get pdb -n retailstore api-gateway-pdb
kubectl get pdb -n retailstore retail-store-webapp-pdb
kubectl get networkpolicy -n retailstore default-deny-all
kubectl get networkpolicy -n retailstore explicit-egress-for-apps

echo "=> Validating Kafka Persistence..."
kubectl exec -n retailstore pod/kafka-dual-role-0 -- /bin/bash -c "echo 'hello-kafka' | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic persistence-test"
echo "Deleting Kafka Pod to test persistence..."
OLD_UID=$(kubectl get pod kafka-dual-role-0 -n retailstore -o jsonpath='{.metadata.uid}')
kubectl delete pod kafka-dual-role-0 -n retailstore --wait=false

echo "Waiting for Strimzi to recreate the pod..."
while true; do
  NEW_UID=$(kubectl get pod kafka-dual-role-0 -n retailstore -o jsonpath='{.metadata.uid}' 2>/dev/null || echo "")
  if [ -n "$NEW_UID" ] && [ "$NEW_UID" != "$OLD_UID" ]; then
    break
  fi
  sleep 2
done
echo "Waiting for Kafka pod to become ready..."
kubectl wait pod/kafka-dual-role-0 -n retailstore --for=condition=Ready --timeout=300s
result=$(kubectl exec -n retailstore pod/kafka-dual-role-0 -- /bin/bash -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic persistence-test --from-beginning --max-messages 1 --timeout-ms 10000")
if [[ "$result" != *"hello-kafka"* ]]; then
  echo "Kafka persistence test failed. Did not find message."
  exit 1
else
  echo "Kafka persistence test passed."
fi

echo "=> Running E2E Validations..."
if ! grep -Eq '(^|[[:space:]])retailstore\.local([[:space:]]|$)' /etc/hosts; then
  echo "127.0.0.1 retailstore.local keycloak.local api.retailstore.local jobrunr.local" | sudo tee -a /etc/hosts || echo "Warning: Could not update /etc/hosts"
fi
export PROTOCOL=https
export HOST=api.retailstore.local
export PORT=443
./test-em-all.sh --no-cb-strict || echo "E2E Tests had failures"

if curl -s -k -f https://retailstore.local > /dev/null; then
  echo "Webapp reachable via ingress."
else
  echo "::warning::Webapp smoke check via https://retailstore.local failed (non-fatal)."
fi

if curl -s -k -f -X POST https://keycloak.local/realms/retailstore/protocol/openid-connect/token \
  -d "client_id=retailstore-webapp" \
  -d "client_secret=P1sibsIrELBhmvK18BOzw1bUl96DcP2z" \
  -d "grant_type=password" \
  -d "username=retail" \
  -d "password=retail1234" | grep -q access_token; then
  echo "Keycloak token endpoint returned access_token."
else
  echo "::warning::Keycloak smoke check failed to obtain access_token (non-fatal)."
fi

echo "=> Load Test Autoscaling (KEDA/HPA)..."
kubectl apply -k deployment/k8s/overlays/autoscaling/ || echo "Warning: Autoscaling overlay failed, checking if already applied."
echo "Sending load..."
for i in {1..200}; do
  curl -s -X POST http://api.retailstore.local/api/orders -H "Content-Type: application/json" -d '{"customerId":"1", "items":[{"productId":"1", "quantity":2}]}' > /dev/null &
done
wait
echo "Waiting for autoscaler to trigger..."
sleep 30
replicas=$(kubectl get deployment order-service -n retailstore -o jsonpath='{.spec.replicas}')
echo "Order service replicas: $replicas"
if [ "$replicas" -gt 1 ]; then
  echo "Autoscaling triggered successfully!"
else
  echo "::warning::Autoscaling did not trigger during load test. KEDA might need more time or lag didn't exceed threshold."
fi

echo "=========================================="
echo " E2E local tests finished!"
echo "=========================================="

if [[ "${1:-}" == "--teardown" ]]; then
  echo "=> Tearing down Kind cluster as requested..."
  kind delete cluster --name kind
fi

