# ADR-001: Kubernetes Deployment Strategy and Multi-Layer Kustomize Overlay Architecture

## Status
Accepted

## Context
The Spring Boot Microservices platform comprises 8 distinct service modules (`api-gateway`, `catalog-service`, `inventory-service`, `order-service`, `payment-service`, `retail-store-webapp`, `config-server`, and `service-registry`) alongside supporting data infrastructure (PostgreSQL, Redis, Apache Kafka, Keycloak). 

Deploying this platform across heterogeneous target environments—local developer machines, CI integration test clusters, staging qualification environments, and high-availability production clusters—requires a configuration management strategy that avoids copy-paste duplication, enforces strict environment isolation, and maintains production security hardening without compromising local development agility.

Prior configuration patterns suffered from drifting manifest duplicates, accidental leaking of production hardening into single-node development clusters (leading to unschedulable pods), and brittle parameterization.

## Decision
We adopt a declarative multi-layer **Kustomize overlay architecture** rooted in `deployment/k8s/`:

1. **Base Layer (`deployment/k8s/base/`)**:
   - Declares the single source of truth for canonical Kubernetes resources: `Deployments`, `Services`, `ServiceAccounts` (with `automountServiceAccountToken: false`), standard `Ingress`, baseline `NetworkPolicies`, baseline `ConfigMaps`, and development dummy `Secrets`.
   - Single-node topology with minimal resource footprint suitable for any standard cluster.

2. **Hardening Base Layer (`deployment/k8s/overlays/hardening-base/`)**:
   - Reusable cross-cutting overlay defining enterprise security baseline: non-root execution (`runAsNonRoot: true`, `readOnlyRootFilesystem: true`, dropping all capabilities `drop: ["ALL"]`), standard PodDisruptionBudgets (`minAvailable: 1`), and strict `NetworkPolicies`.

3. **Environment Overlays**:
   - **`dev` / `ci`**: Inherits directly from `base`. Configures ephemeral single-replica resources, relaxed resource requests, and fast recycling without production anti-affinity or multi-node scheduling constraints.
   - **`staging`**: Inherits from `hardening-base`. Enforces production-identical security and container constraints with scaled-down replica counts (e.g., 2 replicas) to validate multi-instance concurrency and session affinity.
   - **`prod`**: Inherits from `hardening-base`. Enforces multi-replica high availability (minimum 3 replicas per stateless microservice), strict zonal `podAntiAffinity` (`topologyKey: topology.kubernetes.io/zone`), horizontal pod autoscalers (KEDA / HPA), and ExternalSecrets for credential management.

4. **Cross-Cutting Feature Overlays**:
   - **`autoscaling`**: Overlays KEDA `ScaledObject` and HPA resources dynamically responding to CPU, memory, and Kafka consumer lag metrics.
   - **`observability`**: Injects OpenTelemetry LGTM sidecars, collector OTLP endpoints (`http://otel-collector:4318`), and standardized telemetry resource labels.

## Consequences

### Positive
- **DRY (Don't Repeat Yourself)**: Core deployment topology is written once in `base`; environment differences are isolated to declarative JSON/YAML patches.
- **Security by Default in Production**: Hardened security contexts, resource quotas, and network policies are enforced deterministically in staging and production.
- **Developer Simplicity**: Local and CI environments remain lightweight and require zero cloud provider dependencies or multi-node requirements.
- **GitOps Friendly**: Kustomize overlays are natively supported by Argo CD and `kubectl apply -k`, allowing continuous delivery without templating engines.

### Negative / Trade-offs
- Patch composition complexity: Multiple overlay layers require careful path management (`../../base`, `../../overlays/hardening-base`) and precise resource target selectors.
- Schema debugging: Kustomize silent failure modes (e.g., duplicate top-level keys) require automated validation in CI pipelines (`kubectl kustomize`).

## Verification & Compliance
- CI verification must run `kubectl kustomize` against all overlays (`base`, `dev`, `ci`, `staging`, `prod`, `autoscaling`, `observability`) on every pull request.
- Production deployments must strictly reject any deployment manifest originating from `dev` or raw `base`.
