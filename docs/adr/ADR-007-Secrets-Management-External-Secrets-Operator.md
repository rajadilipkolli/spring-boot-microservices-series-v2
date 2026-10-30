# ADR-007: Secrets Management and External Secrets Operator (ESO) Architecture

## Status
Accepted

## Context
Microservices handle sensitive credentials including:
- PostgreSQL database passwords (`postgres-credentials`)
- Keycloak admin and service account credentials (`keycloak-admin-credentials`, `keycloak-user-passwords`)
- Spring Cloud Config Server HTTP basic credentials (`config-server-credentials`)
- OAuth2 client secrets (`webapp-oauth2-credentials`)
- Ingress HTTP basic authentication (`jobrunr-auth`)

Storing credentials in plaintext within Git repositories or static Kubernetes manifests creates severe security compliance violations. Hardcoded credentials in `base/secrets.yaml` risk leaking into production environments or colliding with dynamically injected secrets.

## Decision
We standardize on **External Secrets Operator (ESO)** with pluggable **SecretStore** backends:

1. **Environment Segregation & Provider Architecture**:
   - **Base / Dev / CI**: Static `deployment/k8s/base/secrets.yaml` containing clearly labeled throwaway development mock credentials (e.g., `demo-throwaway-pg-pass`).
   - **Staging / Prod**: Managed via External Secrets Operator (`external-secrets.io/v1beta1`). The static `base/secrets.yaml` objects are excluded or deleted via Kustomize patches to prevent collisions.
   - **SecretStore Implementation**:
     - Standardized on **Kubernetes SecretStore** (`spec.provider.kubernetes`) or enterprise vaults (HashiCorp Vault / AWS Secrets Manager / Azure Key Vault).
     - The `SecretStore` connects to a dedicated, encrypted secrets namespace or external key management service.

2. **ExternalSecret Resources**:
   - Declared for each microservice dependency:
     - `config-server-credentials`: Sourced from remote secret `config-server-secret`.
     - `keycloak-admin-credentials`: Sourced from remote secret `keycloak-admin-secret`.
     - `postgres-credentials`: Sourced from remote secret `postgres-appuser-secret`.
     - `webapp-oauth2-credentials`: Sourced from remote secret `webapp-oauth-secret`.
   - Refresh Interval: Configured to `refreshInterval: "1h"` to synchronize secret rotations automatically without requiring operator intervention.

3. **Injection Pattern**:
   - Target Kubernetes `Secret` objects are synthesized by ESO in the `retailstore` namespace.
   - Microservice Deployments consume secrets via `secretKeyRef` or `envFrom.secretRef`, ensuring container filesystems remain unexposed to plaintext files unless explicitly mounted.

4. **Secret Scanning in CI**:
   - Continuous scanning via Gitleaks and TruffleHog in GitHub Actions workflows to prevent credentials from ever being committed to Git.

## Consequences

### Positive
- **Zero Plaintext in Git**: Production secrets are never stored in the Git repository.
- **Automated Synchronization**: Secrets rotated in the upstream secret store are automatically updated in Kubernetes `Secret` resources within 1 hour.
- **Auditability**: Access to sensitive credentials is fully audited by the upstream secret provider's access logs.
- **Safe Development Defaults**: Developers can run `kubectl apply -k deployment/k8s/overlays/dev` or `docker compose up` out-of-the-box using safe mock defaults.

### Negative / Trade-offs
- Controller Dependency: Cluster must have External Secrets Operator installed and running.
- Eventual Consistency: Pods reading secrets via environment variables require a restart or deployment rollout to pick up rotated secrets.

## Verification & Compliance
- Staging and production overlays must render zero plaintext passwords in `stringData` or `data` fields.
- `kubectl get externalsecrets -n retailstore` must report `STATUS: SecretSynced` for all declared credentials.
