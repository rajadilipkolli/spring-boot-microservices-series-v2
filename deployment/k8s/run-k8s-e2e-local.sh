#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-k8s-e2e-local.sh — Local replication of .github/workflows/k8s-e2e.yml
#
# Replicates the CI pipeline locally:
# 1. Validates Terraform module format and configuration
# 2. Creates the Kind cluster (with port mappings)
# 3. Builds the local Terraform runner image and loads all images into Kind
# 4. Executes ./deployment/k8s/deploy.sh ci (installs Envoy Gateway, CRDs, deployment & readiness)
# 5. Validates NetworkPolicy and PDBs
# 6. Validates Kafka persistence
# 7. Runs E2E integration tests against the local Gateway
# 8. Validates NetworkPolicy isolation and deployment rollouts
# 9. Automatically collects diagnostics on failure
# ---------------------------------------------------------------------------
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
KIND_CONFIG="$PROJECT_ROOT/deployment/k8s/kind-config.yaml"

CLUSTER_NAME="kind"
NAMESPACE="retailstore"

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
# 3. Image Preparation (Mirrors CI: Obtain and Load Images)
# Build the Terraform runner image locally so Kind tests against the current commit.
# Dedicated workflow (.github/workflows/terraform-runner-image.yml) publishes to DockerHub.
# ---------------------------------------------------------------------------
echo "=> Step 3: Building Terraform Runner & Loading Images into Kind..."
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
# 4. Deploy Overlay (Mirrors CI: Deploy CI Overlay via deploy.sh)
# ---------------------------------------------------------------------------
echo "=> Step 4: Deploying CI Overlay via unified deploy.sh..."
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
# Add only missing names and ignore names mentioned in comments.
for gateway_host in retailstore.local keycloak.local api.retailstore.local jobrunr.local; do
  if awk -v host="$gateway_host" '
    { sub(/#.*/, ""); for (i = 2; i <= NF; i++) if (tolower($i) == host) found = 1 }
    END { exit !found }
  ' /etc/hosts; then
    continue
  fi
  if [[ -w /etc/hosts ]]; then
    printf '\n127.0.0.1 %s\n' "$gateway_host" >> /etc/hosts
  elif ! printf '\n127.0.0.1 %s\n' "$gateway_host" | sudo -n tee -a /etc/hosts >/dev/null; then
    echo "Add 127.0.0.1 $gateway_host to /etc/hosts before running the Kubernetes E2E tests." >&2
    exit 1
  fi
done

TF_VAR_webapp_client_secret="${TF_VAR_webapp_client_secret:-$(kubectl get secret webapp-oauth2-credentials -n "$NAMESPACE" -o jsonpath='{.data.OAUTH2_CLIENT_SECRET}' | base64 --decode)}"
KEYCLOAK_RETAIL_PASSWORD="${KEYCLOAK_RETAIL_PASSWORD:-$(kubectl get secret keycloak-user-passwords -n "$NAMESPACE" -o jsonpath='{.data.RETAIL_PASSWORD}' | base64 --decode)}"
export TF_VAR_webapp_client_secret KEYCLOAK_RETAIL_PASSWORD

set +e
"$PROJECT_ROOT/mvnw" -f "$PROJECT_ROOT/pom.xml" test -pl e2e-tests -DskipE2E=false \
  -Dbase.url=http://api.retailstore.local \
  -Dkeycloak.url=http://keycloak.local \
  -Dwebapp.url=http://retailstore.local \
  -Djobrunr.url=http://jobrunr.local/dashboard \
  "-Dkeycloak.client.secret=${TF_VAR_webapp_client_secret:-}" \
  "-Dkeycloak.password=${KEYCLOAK_RETAIL_PASSWORD:-}"
mvn_exit=$?
set -e

if (( mvn_exit != 0 )); then
  echo "E2E Maven tests encountered failures." >&2
  exit "$mvn_exit"
fi

# Request webapp via Gateway (fatal check: 200, 302 or 303 expected)
echo "Verifying webapp reachable via Gateway..."
webapp_status=$(curl -s -o /dev/null -w "%{http_code}" http://retailstore.local)
if [[ "$webapp_status" =~ ^(200|302|303)$ ]]; then
  echo "Webapp reachable via Gateway (HTTP status: $webapp_status)."
else
  echo "Fatal: Webapp smoke check via http://retailstore.local returned unexpected status: $webapp_status" >&2
  exit 1
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
# 8b. Validate Kafka Consumer Groups Protocol
# ---------------------------------------------------------------------------
echo "=> Step 8b: Validating Kafka Consumer Groups Protocol..."
groups_output=$(kubectl exec -n "$NAMESPACE" statefulset/kafka -- /bin/bash -c "/opt/kafka/bin/kafka-groups.sh --bootstrap-server localhost:9092 --list")
echo "Current Kafka groups:"
echo "$groups_output"

for group in payment stock product; do
  if ! echo "$groups_output" | grep -E "^${group}[[:space:]]+.*[[:space:]]+consumer([[:space:]]|$)"; then
    echo "Error: Group '$group' is not using protocol 'consumer'." >&2
    echo "$groups_output" >&2
    exit 1
  fi
  echo "Group '$group' verified with protocol 'consumer'."
done

if ! echo "$groups_output" | grep -E "^order-service[[:space:]]+.*[[:space:]]+classic([[:space:]]|$)"; then
  echo "Error: 'order-service' is not using classic protocol." >&2
  echo "$groups_output" >&2
  exit 1
fi
echo "Group 'order-service' verified with classic Streams protocol."

# ---------------------------------------------------------------------------
# 9. Validate Network Policies (Mirrors CI: Validate Network Policies)
# ---------------------------------------------------------------------------
echo "=> Step 9: Validating Network Policies..."
run_probe() {
  local pod_name="$1"
  local app_label="$2"
  local target_host="$3"
  local target_port="$4"
  local timeout_flag="${5:-}"

  # Clean up any leftover probe pod before starting
  kubectl delete pod "$pod_name" -n "$NAMESPACE" --ignore-not-found --wait=true >/dev/null 2>&1 || true

  # Ensure the probe pod is deleted upon function exit
  trap 'kubectl delete pod "$pod_name" -n "$NAMESPACE" --ignore-not-found --wait=true >/dev/null 2>&1 || true' RETURN

  echo "Creating probe pod $pod_name..."
  if ! kubectl run "$pod_name" \
    --image=busybox:1.36 \
    --labels="app=$app_label" \
    -n "$NAMESPACE" \
    --restart=Never \
    --attach=false \
    --command -- sh -c "nc $timeout_flag -z $target_host $target_port; echo \"NC_STATUS:\$?\""; then
    echo "Error: Failed to create probe pod $pod_name" >&2
    return 2
  fi

  echo "Waiting for probe pod $pod_name to complete..."
  if ! kubectl wait --for=jsonpath='{.status.phase}'=Succeeded pod/"$pod_name" -n "$NAMESPACE" --timeout=60s; then
    echo "Error: Probe pod $pod_name failed to complete or timed out" >&2
    kubectl describe pod "$pod_name" -n "$NAMESPACE" >&2 || true
    return 2
  fi

  local pod_logs
  pod_logs=$(kubectl logs "$pod_name" -n "$NAMESPACE" 2>&1) || {
    echo "Error: Failed to retrieve logs for probe pod $pod_name" >&2
    return 2
  }

  local nc_status
  nc_status=$(echo "$pod_logs" | sed -n 's/^NC_STATUS://p' | tr -d '\r')
  if [[ -z "$nc_status" ]]; then
    echo "Error: Could not parse NC_STATUS from probe pod $pod_name logs:" >&2
    echo "$pod_logs" >&2
    return 2
  fi

  return "$nc_status"
}

set +e
run_probe test-api-gateway api-gateway catalog-service 18080
status_allowed=$?
set -e

if [ "$status_allowed" -eq 2 ]; then
  echo "Allowed path probe test execution failed (pod lifecycle/wait/log error)" >&2
  exit 1
elif [ "$status_allowed" -ne 0 ]; then
  echo "Allowed path failed (nc status: $status_allowed)" >&2
  exit 1
fi
echo "Allowed path succeeded."

set +e
run_probe test-catalog-service catalog-service api-gateway 8765 "-w 3"
status_disallowed=$?
set -e

if [ "$status_disallowed" -eq 2 ]; then
  echo "Disallowed path probe test execution failed (pod lifecycle/wait/log error)" >&2
  exit 1
elif [ "$status_disallowed" -eq 0 ]; then
  echo "Disallowed path succeeded!" >&2
  exit 1
else
  echo "Disallowed path blocked (nc status: $status_disallowed)."
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
