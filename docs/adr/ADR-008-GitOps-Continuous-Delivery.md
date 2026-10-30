# ADR-008: GitOps Continuous Delivery with Argo CD and Progressive Canary Rollouts

## Status
Accepted

## Context
Deploying microservices manually using imperative `kubectl apply` commands introduces configuration drift, lack of deployment history, manual approval bottlenecks, and elevated risk of widespread outages during broken releases.

In a microservices platform with 8 independent services, safe continuous delivery requires:
1. Declarative single source of truth stored in Git.
2. Continuous automated drift detection and self-healing.
3. Progressive delivery mechanisms (canary deployments) capable of testing new releases with a small fraction of real production traffic before full cutover.
4. Automatic rollback upon error rate or latency spikes without operator intervention.

## Decision
We standardize on **GitOps Continuous Delivery via Argo CD** and **Progressive Delivery via Argo Rollouts**:

1. **Argo CD Application Architecture**:
   - The repository `spring-boot-microservices-series-v2` contains deployment specifications organized by Kustomize overlays.
   - Argo CD `Application` CRDs represent each environment:
     - `retailstore-dev`: Points to `deployment/k8s/overlays/dev` (automated sync enabled, self-heal enabled).
     - `retailstore-staging`: Points to `deployment/k8s/overlays/staging` (automated sync enabled, self-heal enabled).
     - `retailstore-prod`: Points to `deployment/k8s/overlays/prod` (manual sync or automated sync with strict prune protection).

2. **Progressive Delivery via Argo Rollouts (`Rollout` CRD)**:
   - For mission-critical user-facing services (`api-gateway`, `order-service`, `retail-store-webapp`), standard Kubernetes `Deployment` resources are managed as Argo `Rollout` resources in production.
   - **Canary Strategy**:
     - Step 1: Route 10% of traffic to the new canary version.
     - Step 2: Pause for 5 minutes while running automated metric analysis.
     - Step 3: Route 25% of traffic, pause for 5 minutes.
     - Step 4: Route 50% of traffic, pause for 10 minutes.
     - Step 5: Full promotion (100%).

3. **Automated Rollback via Prometheus Analysis (`AnalysisTemplate`)**:
   - During the canary pause steps, Argo Rollouts executes Prometheus queries against the OTel/Prometheus stack:
     - **Success Rate SLI**: `sum(rate(http_server_requests_seconds_count{status!~"5..", job="<service>"}[2m])) / sum(rate(http_server_requests_seconds_count{job="<service>"}[2m])) >= 0.99`
     - **Latency SLI**: `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{job="<service>"}[2m])) by (le)) <= 0.500`
   - If the error rate exceeds 1% or P99 latency exceeds 500ms, the rollout immediately aborts, routes 100% of traffic back to the stable replica set, and marks the rollout as degraded.

## Consequences

### Positive
- **Zero-Downtime Safe Deployments**: New code is exercised with synthetic and live canary traffic before committing 100% of production traffic.
- **Automated Blast-Radius Containment**: Broken releases affect at most 10% of users and automatically roll back within minutes.
- **Auditability and Drift Detection**: Git commit log represents the exact state of running clusters; unauthorized direct cluster modifications are automatically reconciled.

### Negative / Trade-offs
- Additional Controllers: Requires installing the Argo CD and Argo Rollouts controllers in the cluster.
- Complexity: Canary traffic routing requires integration with Ingress controllers (e.g., ingress-nginx canary annotations or service mesh).

## Verification & Compliance
- Pull requests modifying manifests must be validated against Argo CD linting.
- Staging rollouts must run canary analysis to verify that `AnalysisTemplate` metrics accurately evaluate service health.
