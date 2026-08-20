# Kubernetes Deployment Guide

This guide describes how to deploy the Spring Boot microservices on Kubernetes using Kind, Minikube, or another Kubernetes cluster.

## Prerequisites

- **Docker**: Required by Kind.
- **Kind**: Used to create the local cluster.
- **kubectl** and **Kustomize**: Recent kubectl versions include Kustomize.
- **curl**, **jq**, and **sudo**: Required by the E2E validation and local host mapping.
- **NGINX Ingress Controller**: Required for the host-based application routes.

## Cluster Setup and Ingress

### Local Hostnames

Map the application hostnames in `/etc/hosts` (or the Windows hosts file):

```text
127.0.0.1 retailstore.local keycloak.local api.retailstore.local jobrunr.local
```

For Minikube without `minikube tunnel`, use the Minikube IP instead of `127.0.0.1`.

### Create a Kind Cluster

The repository configuration maps host ports 80 and 443 to the Kind control-plane node:

```bash
kind create cluster --name kind \
  --config deployment/k8s/kind-config.yaml --wait 120s
```

### Install NGINX Ingress

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.12.1/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller --timeout=120s
```

## Deployment Instructions

The deployment manifests are packaged using Kustomize under `deployment/k8s/`:

- `base`: Shared resources, stateful components, and service configurations.
- `dev`: Local development overlay.
- `ci`: Deterministic test overlay with `IfNotPresent` image policy.
- `prod`: Production stub with multiple replicas and TLS.
- `observability`: OTEL LGTM stack and tracing configuration.
- `autoscaling`: HorizontalPodAutoscalers for business services.

### Obtaining Images

For a Kind cluster, pull and load every `dockertmt/mmv2-<service>:0.0.1-SNAPSHOT` image:

```bash
docker pull dockertmt/mmv2-catalog-service:0.0.1-SNAPSHOT
kind load docker-image dockertmt/mmv2-catalog-service:0.0.1-SNAPSHOT --name kind
```

Repeat for config-server, service-registry, api-gateway, inventory-service,
order-service, payment-service, and retail-store-webapp. Alternatively, build
the images locally and load those image tags into Kind.

### Deploying the Stack

To apply the `dev` overlay and wait for readiness:

```bash
cd deployment/k8s
./deploy.sh
```

To apply the CI overlay directly:

```bash
kubectl apply -k deployment/k8s/overlays/ci/
```

### Teardown

```bash
kind delete cluster --name kind
```

## CI Workflow Validation Locally

The repository-level [Kubernetes E2E workflow](../.github/workflows/k8s-e2e.yml)
proves that a fresh Kind cluster can run and serve the stack. Reproduce its
sequence locally as follows:

1. Create Kind with `deployment/k8s/kind-config.yaml`, including port mappings for 80 and 443.
2. Pull and load every published `dockertmt/mmv2-<service>:0.0.1-SNAPSHOT` image with `kind load docker-image`.
3. Install ingress-nginx and wait for its controller pod to become ready.
4. Apply `kubectl apply -k deployment/k8s/overlays/ci/`.
5. Wait for PostgreSQL, Redis, Kafka, and Keycloak; then wait for config-server and service-registry; then wait for all application deployments. Use `kubectl rollout status` and `kubectl wait --for=condition=ready pod` with explicit timeouts such as `300s`.
6. Add the local hostnames shown above to `/etc/hosts`.
7. Run the same E2E and external-access checks as CI:

```bash
HOST=api.retailstore.local PORT=80 ./test-em-all.sh --no-cb-strict
curl --fail http://retailstore.local
curl --fail -X POST http://keycloak.local/realms/retailstore/protocol/openid-connect/token \
  -d 'client_id=retailstore-webapp' -d 'grant_type=password' \
  -d 'username=alice' -d 'password=alice' | grep access_token
```

`--no-cb-strict` skips the Docker Compose container-stop circuit-breaker path;
the Kubernetes workflow validates the deployed services through ingress instead.

## Troubleshooting

- **ImagePullBackOff**: Confirm every image was loaded into Kind and that the CI overlay uses `IfNotPresent`.
- **Config-server ordering**: Inspect init containers and config-server logs if dependent pods restart.
- **Service discovery**: Check service-registry logs and pod readiness before testing gateway routes.
- **Keycloak hostname**: Verify `keycloak.local` and `retailstore.local` resolve to the ingress address.
- **Ingress readiness**: Check the ingress-nginx controller pod and events if host requests fail.

## CI Diagnostics

When the workflow fails, download the `k8s-diagnostics` artifact from the failed
GitHub Actions run. It contains:

- `pods.txt`: Pod status across all namespaces.
- `events.txt`: Cluster events for scheduling, probes, image pulls, and mounts.
- `resources.txt`: Retail Store deployments, StatefulSets, and Services.
- `pods-describe.txt` and `workloads-describe.txt`: Resource conditions and events.
- `*-rollout.txt`: Rollout results for each infrastructure, platform, and application workload.
- One log file per application or infrastructure pod, including all containers.

Start with pod phases and events, then inspect matching rollout files, describe
output, and logs for probe failures, image errors, OOMKills, or config and
service-discovery startup failures.
