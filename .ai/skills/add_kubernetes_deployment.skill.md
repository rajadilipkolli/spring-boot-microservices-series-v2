---
name: Add Kubernetes Deployment
description: Deploy and validate the microservices stack on Kind using the Kubernetes CI overlay
---

# Skill: Add Kubernetes Deployment

This skill describes how to deploy and validate the complete microservices stack on Kubernetes while preserving the repository's CI workflow and service contracts.

## Standards
- **Cluster**: Use Kind with `deployment/k8s/kind-config.yaml` and its port mappings for ports 80 and 443.
- **Images**: Use the `dockertmt/mmv2-<service>:0.0.1-SNAPSHOT` image convention and load images into Kind before applying manifests.
- **Ingress**: Install the pinned ingress-nginx manifest used by `.github/workflows/k8s-e2e.yml`.
- **Overlay**: Use `deployment/k8s/overlays/ci/` for deterministic local and CI validation.
- **Validation**: Run rollout, pod-readiness, ingress, E2E, webapp, and Keycloak checks with explicit timeouts.

## Steps

### 1. Review the Deployment Surface

Inspect the relevant manifests before changing them:

- `deployment/k8s/kind-config.yaml`
- `deployment/k8s/base/`
- `deployment/k8s/overlays/ci/`
- `.github/workflows/k8s-e2e.yml`

Confirm service names, image tags, labels, ingress hosts, namespaces, and dependency ordering.

### 2. Provision Kind

Create a fresh cluster using `deployment/k8s/kind-config.yaml`. Keep the Kind version and node image pinned in the workflow, and preserve `extraPortMappings` for ports 80 and 443.

### 3. Obtain and Load Images

Pull every configured service image and load it with `kind load docker-image --name kind`. A failed pull or load must stop the workflow. Ensure the CI overlay uses `imagePullPolicy: IfNotPresent` so Kubernetes uses the loaded image.

### 4. Install Ingress

Apply the pinned ingress-nginx Kind manifest and wait for the controller pod to become ready before applying application manifests. Use an explicit timeout and allow failure to propagate.

### 5. Apply the Overlay

Apply the complete stack with:

```bash
kubectl apply -k deployment/k8s/overlays/ci/
```

Do not apply only individual services when validating the complete deployment.

### 6. Wait in Dependency Order

Wait for infrastructure first: PostgreSQL, Redis, Kafka, and Keycloak. Then wait for config-server and service-registry. Finally wait for catalog, inventory, order, payment, API gateway, and webapp. Use both `kubectl rollout status` and `kubectl wait --for=condition=ready pod` with explicit timeouts.

### 7. Run End-to-End Validation

Map `retailstore.local`, `api.retailstore.local`, `keycloak.local`, and `jobrunr.local` to the ingress address. Run:

```bash
HOST=api.retailstore.local PORT=80 ./test-em-all.sh --no-cb-strict
```

Then verify webapp access and a seeded Keycloak Direct Access Grant token request. The E2E flow indirectly validates config-server, Eureka, gateway routing, and the distributed catalog/inventory/order/payment path.

### 8. Capture Failure Diagnostics

On failure, collect pod status, cluster events, resource descriptions, rollout status, and logs for infrastructure and application pods. Upload the diagnostics as a CI artifact so failed runs can be investigated without reproducing the cluster immediately.

### 9. Tear Down the Cluster

Use an `if: always()` workflow step to delete the named Kind cluster, including after a failed provisioning, rollout, or validation step. Do not mask failures in the main deployment or validation steps.

### 10. Update Documentation and Verification

When changing the deployment contract, update `deployment/KUBERNETES_DEPLOYMENT_GUIDE.md`, the root `ReadMe.md`, and this skill when applicable. Validate workflow YAML, shell syntax, Kustomize rendering, and the narrowest available Kubernetes checks before broad integration testing.

### Security Standards
Ensure new workloads follow Pod Security restricted standard (runAsNonRoot: true, allowPrivilegeEscalation: false, readOnlyRootFilesystem: true, capabilities dropped). Network policies must be updated to explicitly allow ingress/egress for the new workload.
