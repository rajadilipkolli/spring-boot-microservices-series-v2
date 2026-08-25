# Kubernetes Deployment Guide

This guide describes how to deploy the Spring Boot microservices on Kubernetes using Kind, Minikube, or another Kubernetes cluster.

---

## 🗺️ Understanding Kubernetes – A Simple Overview

> **No prior Kubernetes knowledge required.** This section explains what Kubernetes is and how it works in plain language, using everyday analogies.

### 🏢 What is Kubernetes?

Think of Kubernetes like the **manager of a large office building**.

- Your **applications** (microservices) are the **employees** doing actual work.
- **Kubernetes** is the building manager who decides:
  - Which floor (server) each employee sits on.
  - What happens when an employee calls in sick (crashes) — hire a replacement immediately.
  - How many employees are needed when it gets busy (auto-scaling).
  - Who is allowed through the front door (security / ingress).

Without Kubernetes you would have to manually restart crashed apps, manually move apps between servers, and manually decide how many copies to run. Kubernetes does all of this **automatically**.

---

### 🧱 Key Building Blocks

| Concept                | Plain English Analogy            | What it does in this project                                                             |
|------------------------|----------------------------------|------------------------------------------------------------------------------------------|
| **Cluster**            | The whole office building        | A group of computers that run your apps together                                         |
| **Node**               | A single floor of the building   | One physical or virtual machine in the cluster                                           |
| **Pod**                | One desk on a floor              | The smallest unit — holds one running app container                                      |
| **Deployment**         | HR policy for a team             | Declares how many copies of an app should run and keeps them healthy                     |
| **StatefulSet**        | A department with assigned desks | Like a Deployment, but for apps that need a stable identity (e.g. Kafka, databases)      |
| **Service**            | The internal phone directory     | Gives each app a stable address so other apps can find it                                |
| **Ingress**            | The front-door receptionist      | Routes outside traffic (web browser requests) to the right internal app                  |
| **Namespace**          | A separate wing of the building  | Logical boundary to group related resources — all retailstore apps live in `retailstore` |
| **ConfigMap / Secret** | Employee handbook / safe         | Stores configuration values and sensitive data (passwords, API keys)                     |

---

### 🏗️ How the Retail Store Fits Together

Here is how a customer's web request travels through the system:

```
Browser / curl
     │
     ▼
┌─────────────────────────────────────┐
│           NGINX Ingress             │  ← Front door: routes api.retailstore.local
│         (api.retailstore.local)     │    to the right service
└──────────────┬──────────────────────┘
               │
               ▼
┌──────────────────────────┐
│       API Gateway        │  ← Reception desk: decides which service
│       (port 8765)        │    should handle the request
└──┬──────┬──────┬─────────┘
   │      │      │
   ▼      ▼      ▼
Catalog  Inventory  Order  ← The actual worker services
Service  Service    Service
   │                  │
   │     ┌────────────┘
   ▼     ▼
 Kafka (message bus)  ← Like an internal memo system between services
   │
   ▼
Payment Service       ← Reacts to order events asynchronously
```

Each box above is one or more **Pods** managed by a **Deployment** (or **StatefulSet** for Kafka and PostgreSQL). A **Service** resource gives each box a stable internal address.

---

### 🔄 What Happens When Something Goes Wrong?

Kubernetes is **self-healing**:

1. A Pod crashes → Kubernetes automatically starts a replacement within seconds.
2. A Node (machine) dies → Kubernetes reschedules all Pods onto healthy Nodes.
3. Load spikes → (with autoscaling enabled) Kubernetes adds more Pod copies automatically.

This means the retail store can survive individual failures without manual intervention.

---

### 📦 Namespaces Used in This Project

| Namespace       | Purpose                                                                                                          |
|-----------------|------------------------------------------------------------------------------------------------------------------|
| `retailstore`   | All application services (catalog, order, payment, etc.) and infrastructure (Kafka, PostgreSQL, Redis, Keycloak) |
| `ingress-nginx` | The NGINX Ingress Controller that handles external HTTP traffic                                                  |

---

### 🗂️ Directory Layout at a Glance

```
deployment/k8s/
├── base/          # Core manifests shared by all environments
├── overlays/
│   ├── dev/       # Local development tweaks
│   ├── ci/        # CI/CD-specific settings (image pull policy, replica counts)
│   ├── prod/      # Production settings (TLS, higher replica counts)
│   ├── observability/  # Tracing & metrics stack
│   └── autoscaling/    # Horizontal Pod Autoscalers
```

**Kustomize** is the tool that combines the `base` with an `overlay` to produce the final set of manifests for a given environment — no template engines needed.

---


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

Repeat for config-server, api-gateway, inventory-service, order-service,
payment-service, and retail-store-webapp. The service-registry image is named
`dockertmt/mmv2-service-registry-25:0.0.1-SNAPSHOT` (note the `-25` suffix),
not the generic `mmv2-service-registry` pattern. Alternatively, build the
images locally and load those image tags into Kind.

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
CLIENT_SECRET=$(kubectl get secret webapp-oauth2-credentials -n retailstore -o jsonpath='{.data.OAUTH2_CLIENT_SECRET}' | base64 -d)
curl --fail -X POST http://keycloak.local/realms/retailstore/protocol/openid-connect/token \
  -d 'client_id=retailstore-webapp' -d "client_secret=$CLIENT_SECRET" \
  -d 'grant_type=password' \
  -d 'username=retail' -d 'password=retail1234' | grep access_token
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

## 5. Kafka Topic Inspection

| Command                                                                                                                          | Purpose                                                                                                                                                                                                          |
|----------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `kubectl exec kafka-0 -n retailstore -- bin/kafka-topics.sh --describe --topic __consumer_offsets --bootstrap-server kafka:9092` | Show detailed description (partitions, replication factor, ISR, etc.) for the `__consumer_offsets` internal topic. Useful for verifying that the replication factor matches the Kafka StatefulSet replica count. |
