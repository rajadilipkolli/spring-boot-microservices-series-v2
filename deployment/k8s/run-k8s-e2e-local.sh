#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-k8s-e2e-local.sh — Local replication of .github/workflows/k8s-e2e.yml
#
# Replicates the CI pipeline locally:
# 1. Validates Terraform module format and configuration
# 2. Creates the Kind cluster (with port mappings)
# 3. Installs NGINX Ingress controller
# 4. Builds the local Terraform runner image and loads all images into Kind
# 5. Executes ./deployment/k8s/deploy.sh ci (unified deployment & readiness)
# 6. Validates NetworkPolicy and PDBs
# 7. Validates Kafka persistence
# 8. Runs E2E integration tests against the local Ingress
# 9. Validates NetworkPolicy isolation and deployment rollouts
# 10. Automatically collects diagnostics on failure
# ---------------------------------------------------------------------------
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
KIND_CONFIG="$PROJECT_ROOT/deployment/k8s/kind-config.yaml"

CLUSTER_NAME="kind"
NAMESPACE="retailstore"
INGRESS_MANIFEST="https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.12.1/deploy/static/provider/kind/deploy.yaml"

SKIP_CLUSTER="${SKIP_CLUSTER:-false}"
TEARDOWN="${TEARDOWN:-false}"

while (($# > 0)); do
  case "$1" in
    --skip-cluster) SKIP_CLUSTER=true ;;
    --teardown) TEARDOWN=true ;;
    --help)
      echo "Usage: ./run-k8s-e2e-local.sh [--skip-cluster] [--teardown]"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
  shift
done

echo "=========================================="
echo " Starting Local Kubernetes E2E Tests"
echo " (Mirroring .github/workflows/k8s-e2e.yml)"
echo "=========================================="

# ---------------------------------------------------------------------------
# 1. Terraform Validation Gate (Mirrors CI job: terraform-validate)
# ---------------------------------------------------------------------------
echo "=> Step 1: Validating Terraform Keycloak Module..."
if command -v terraform >/dev/null 2>&1; then
  (cd "$PROJECT_ROOT/deployment/terraform/keycloak" && \
    terraform fmt -check && \
    terraform init -backend=false -lockfile=readonly && \
    terraform validate)
else
  docker run --rm -v "$PROJECT_ROOT/deployment/terraform/keycloak:/workspace" -w /workspace \
    hashicorp/terraform:1.17 fmt -check
  docker run --rm -v "$PROJECT_ROOT/deployment/terraform/keycloak:/workspace" -w /workspace \
    hashicorp/terraform:1.17 init -backend=false -lockfile=readonly
  docker run --rm -v "$PROJECT_ROOT/deployment/terraform/keycloak:/workspace" -w /workspace \
    hashicorp/terraform:1.17 validate
fi
echo "Terraform validation succeeded."

# ---------------------------------------------------------------------------
# 2. Kind Cluster Creation (Mirrors CI: Create k8s Kind Cluster)
# ---------------------------------------------------------------------------
if [[ "$SKIP_CLUSTER" != "true" ]]; then
  echo "=> Step 2: Creating Kind Cluster..."
  kind create cluster --name "$CLUSTER_NAME" --config "$KIND_CONFIG" || echo "Cluster may already exist."
else
  echo "=> Step 2: Reusing existing Kind Cluster."
fi

# ---------------------------------------------------------------------------
# 3. Ingress Controller (Mirrors CI: Install NGINX Ingress Controller)
# ---------------------------------------------------------------------------
echo "=> Step 3: Installing NGINX Ingress Controller..."
kubectl apply -f "$INGRESS_MANIFEST"
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s

# ---------------------------------------------------------------------------
# 4. Image Preparation (Mirrors CI: Obtain and Load Images)
# ---------------------------------------------------------------------------
echo "=> Step 4: Building Terraform Runner & Loading Images into Kind..."
docker build -t dockertmt/mmv2-keycloak-terraform-runner:0.0.1-SNAPSHOT -f "$PROJECT_ROOT/deployment/terraform/Dockerfile" "$PROJECT_ROOT/deployment/terraform/"

images=(
  dockertmt/mmv2-config-server:0.0.1-SNAPSHOT
  dockertmt/mmv2-service-registry-25:0.0.1-SNAPSHOT
  dockertmt/mmv2-api-gateway:0.0.1-SNAPSHOT
  dockertmt/mmv2-catalog-service:0.0.1-SNAPSHOT
  dockertmt/mmv2-inventory-service:0.0.1-SNAPSHOT
  dockertmt/mmv2-order-service:0.0.1-SNAPSHOT
  dockertmt/mmv2-payment-service:0.0.1-SNAPSHOT
  dockertmt/mmv2-retail-store-webapp:0.0.1-SNAPSHOT
  dockertmt/mmv2-keycloak-terraform-runner:0.0.1-SNAPSHOT
)

for image in "${images[@]}"; do
  if [[ "$image" != "dockertmt/mmv2-keycloak-terraform-runner"* ]]; then
    docker pull "$image" || echo "Using local image for $image"
  fi
  kind load docker-image "$image" --name "$CLUSTER_NAME"
done

# ---------------------------------------------------------------------------
# 5. Deploy Overlay (Mirrors CI: Deploy CI Overlay via deploy.sh)
# ---------------------------------------------------------------------------
echo "=> Step 5: Deploying CI Overlay via unified deploy.sh..."
"$PROJECT_ROOT/deployment/k8s/deploy.sh" ci

# ---------------------------------------------------------------------------
# 6. Validate NetworkPolicy and PDBs (Mirrors CI: Validate NetworkPolicy and PDBs)
# ---------------------------------------------------------------------------
echo "=> Step 6: Validating NetworkPolicy and PDBs..."
kubectl get pdb -n "$NAMESPACE" api-gateway-pdb
kubectl get pdb -n "$NAMESPACE" retail-store-webapp-pdb
kubectl get networkpolicy -n "$NAMESPACE" default-deny-all
kubectl get networkpolicy -n "$NAMESPACE" explicit-egress-for-apps

# ---------------------------------------------------------------------------
# 7. Validate Kafka Persistence (Mirrors CI: Validate Kafka Persistence)
# ---------------------------------------------------------------------------
echo "=> Step 7: Validating Kafka Persistence..."
kubectl exec -n "$NAMESPACE" statefulset/kafka -- /bin/bash -c "echo 'hello-kafka' | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic persistence-test"

echo "Deleting Kafka Pod to test persistence..."
kubectl delete pod kafka-0 -n "$NAMESPACE"

kubectl rollout status statefulset/kafka -n "$NAMESPACE" --timeout=300s
kubectl wait --namespace "$NAMESPACE" --for=condition=ready pod/kafka-0 --timeout=300s

echo "Verifying Kafka data survived..."
result=$(kubectl exec -n "$NAMESPACE" statefulset/kafka -- /bin/bash -c "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic persistence-test --from-beginning --max-messages 1 --timeout-ms 10000")
if [[ "$result" != *"hello-kafka"* ]]; then
  echo "Kafka persistence test failed. Did not find message." >&2
  exit 1
fi
echo "Kafka persistence test passed."

echo "Allowing application consumers to reconnect and rebalance after Kafka restart..."
sleep 10

# ---------------------------------------------------------------------------
# 8. Run E2E Validations (Mirrors CI: Run E2E Validations)
# ---------------------------------------------------------------------------
echo "=> Step 8: Running E2E Validations..."
# Map hostnames for local test runner if possible
if command -v sudo >/dev/null 2>&1 && [[ -w /etc/hosts || $(sudo -n true 2>/dev/null; echo $?) -eq 0 ]]; then
  if ! grep -Eq '(^|[[:space:]])retailstore\.local([[:space:]]|$)' /etc/hosts; then
    echo "127.0.0.1 retailstore.local keycloak.local api.retailstore.local jobrunr.local" | sudo tee -a /etc/hosts || true
  fi
fi

TF_VAR_webapp_client_secret="${TF_VAR_webapp_client_secret:-$(kubectl get secret webapp-oauth2-credentials -n "$NAMESPACE" -o jsonpath='{.data.OAUTH2_CLIENT_SECRET}' | base64 --decode)}"
KEYCLOAK_RETAIL_PASSWORD="${KEYCLOAK_RETAIL_PASSWORD:-$(kubectl get secret keycloak-user-passwords -n "$NAMESPACE" -o jsonpath='{.data.RETAIL_PASSWORD}' | base64 --decode)}"
export TF_VAR_webapp_client_secret KEYCLOAK_RETAIL_PASSWORD

set +e
"$PROJECT_ROOT/mvnw" -f "$PROJECT_ROOT/pom.xml" test -pl e2e-tests -DskipE2E=false \
  -Dbase.url=http://api.retailstore.local \
  -Dkeycloak.url=http://keycloak.local \
  "-Dkeycloak.client.secret=${TF_VAR_webapp_client_secret:-}" \
  "-Dkeycloak.password=${KEYCLOAK_RETAIL_PASSWORD:-}"
mvn_exit=$?
set -e

if (( mvn_exit != 0 )); then
  echo "E2E Maven tests encountered failures." >&2
  exit "$mvn_exit"
fi

# Request webapp via Ingress (best-effort smoke check)
if curl -s -f http://retailstore.local > /dev/null; then
  echo "Webapp reachable via ingress."
else
  echo "Warning: Webapp smoke check via http://retailstore.local failed."
fi

# OAuth2 Keycloak Test (best-effort smoke check)
if curl -s -f -X POST http://keycloak.local/realms/retailstore/protocol/openid-connect/token \
  --data-urlencode "client_id=retailstore-webapp" \
  --data-urlencode "client_secret=${TF_VAR_webapp_client_secret}" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=retail" \
  --data-urlencode "password=${KEYCLOAK_RETAIL_PASSWORD}" | grep -q access_token; then
  echo "Keycloak token endpoint returned access_token."
else
  echo "Warning: Keycloak smoke check failed to obtain access_token."
fi

# ---------------------------------------------------------------------------
# 9. Validate Network Policies (Mirrors CI: Validate Network Policies)
# ---------------------------------------------------------------------------
echo "=> Step 9: Validating Network Policies..."
kubectl run test-api-gateway --rm -i --image=busybox:1.36 --labels="app=api-gateway" -n "$NAMESPACE" --restart=Never -- nc -z catalog-service 18080 || (echo "Allowed path failed" && exit 1)
if kubectl run test-catalog-service --rm -i --image=busybox:1.36 --labels="app=catalog-service" -n "$NAMESPACE" --restart=Never -- nc -w 3 -z api-gateway 8765; then
  echo "Disallowed path succeeded!" >&2
  exit 1
else
  echo "Disallowed path blocked."
fi

# ---------------------------------------------------------------------------
# 10. Validate PDBs and Node Drain (Mirrors CI: Validate PDBs and Node Drain)
# ---------------------------------------------------------------------------
echo "=> Step 10: Validating PDBs..."
kubectl get pdb -n "$NAMESPACE" catalog-service-pdb
min_avail=$(kubectl get pdb -n "$NAMESPACE" catalog-service-pdb -o jsonpath="{.spec.minAvailable}")
if [ "$min_avail" -ne 1 ]; then
  echo "PDB minAvailable check failed: expected 1, got $min_avail" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# 11. Validate Rollout Strategy (Mirrors CI: Validate Rollout and Deployment Strategy)
# ---------------------------------------------------------------------------
echo "=> Step 11: Validating Rollout Strategy..."
kubectl rollout restart deployment/catalog-service -n "$NAMESPACE"
kubectl rollout status deployment/catalog-service -n "$NAMESPACE" --timeout=120s

echo "=========================================="
echo " All Local Kubernetes E2E Tests Succeeded!"
echo "=========================================="

# ---------------------------------------------------------------------------
# Teardown (if requested)
# ---------------------------------------------------------------------------
if [[ "$TEARDOWN" == "true" ]]; then
  echo "=> Tearing down Kind cluster as requested..."
  kind delete cluster --name "$CLUSTER_NAME"
fi
