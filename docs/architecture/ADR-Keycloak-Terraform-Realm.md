# Architecture Decision Record: Terraform-Managed Keycloak Realm

## Status
Accepted

## Context

The `retailstore` Keycloak realm was previously managed via a static JSON export
(`deployment/realm-config/retailstore-realm.json`) baked into a ConfigMap
(`keycloak-realm-configmap.yaml`) and imported by Keycloak at startup using the
`--import-realm` flag.

This approach had several problems:

1. **Not idempotent in production**: `--import-realm` only runs on first startup
   or when the realm does not exist. Incremental changes (new clients, roles, or
   users) require manual Admin API calls or full realm recreation.

2. **Drift risk**: The JSON export in `realm-config/` and the ConfigMap YAML are
   two sources of truth that can diverge without any validation gate.

3. **Secret leakage**: The ConfigMap contained hashed user credentials from the
   Keycloak export. While hashed, these are unnecessarily present in the Git
   history and Kubernetes API.

4. **No audit trail for changes**: Mutations to the realm have no corresponding
   commit or plan/apply log.

## Decision

We adopt the **`mrparkers/keycloak` Terraform provider** to declare the
`retailstore` realm as code, applied by a **one-off Kubernetes Job** in the prod
overlay after Keycloak becomes healthy.

### Provider

| Component | Choice | Rationale |
|---|---|---|
| Provider | `mrparkers/keycloak ~> 4.4` | Canonical community provider; comprehensive resource coverage; matches Keycloak 26.x |
| State backend | `kubernetes` backend | Zero extra infrastructure; state persisted as a Secret in the `retailstore` namespace; accessible only to the runner's ServiceAccount |
| Terraform version | `>= 1.9` | Stable; shipped in `hashicorp/terraform:1.9` base image |

### Runtime topology (prod)

```
Keycloak Deployment (start, no --import-realm)
        │ readiness: /health/ready
        ▼
keycloak-terraform-runner Job
  initContainer: wait-for-keycloak (curl /health/ready)
  container:     terraform-runner  (init → plan → apply)
        │ Admin REST API  port 8080
        ▼
Keycloak Admin API
        │ writes realm, client, roles, users
        ▼
PostgreSQL (Keycloak's backing DB)
```

### Network paths required

| Source | Destination | Port | Protocol | Policy |
|---|---|---|---|---|
| `keycloak-terraform-runner` | `keycloak` (in-cluster) | 8080 | HTTP | `allow-apps-to-apps` NetworkPolicy |
| `keycloak-terraform-runner` | Terraform Registry | 443 | HTTPS | `default-deny-all` allows egress 443 |
| `keycloak-terraform-runner` | Kubernetes API | 6443 | HTTPS | `default-deny-all` allows egress 6443 (state backend) |

### Resources managed by Terraform

| Resource | Terraform type |
|---|---|
| `retailstore` realm | `keycloak_realm` |
| `retailstore-webapp` client | `keycloak_openid_client` |
| Client ID / IP / Host mappers | `keycloak_openid_client_user_session_note_mapper` |
| Realm role `user` | `keycloak_role` |
| Client role `ADMIN` | `keycloak_role` |
| User `raja` + password + realm role | `keycloak_user`, `keycloak_user_password`, `keycloak_user_roles` |
| User `retail` + password + roles | `keycloak_user`, `keycloak_user_password`, `keycloak_user_roles` |

### Resources NOT managed by Terraform

Built-in clients (`account`, `admin-cli`, `broker`, `realm-management`,
`security-admin-console`) and the 13 default client scopes are created
automatically by Keycloak and are not imported or managed. The built-in
composite role `default-roles-retailstore` is referenced by data source only.

### base/dev/CI: forward-looking decision debt

Base, dev, and CI overlays **retain** the `--import-realm` flow unchanged.
This is an intentional trade-off:

- CI requires a known-working Keycloak realm without a real Terraform runner image
  build step in the CI pipeline.
- The static import is sufficient for unit and integration testing where realm
  drift is not a concern.

**Aligning CI to the Terraform flow is recorded as future work.** A subsequent
ADR or ticket should:
1. Build and push the Terraform runner image in CI.
2. Apply the prod overlay in CI and wait for the Job.
3. Remove the `keycloak-realm-configmap.yaml` from the base overlay entirely.

## Consequences

### Positive

- **Idempotent**: `terraform apply` is a no-op if the realm matches the declared
  state. Incremental changes are applied safely.
- **Auditable**: Every realm change is a Git commit with a Terraform plan diff.
- **Secret hygiene**: Hashed credential blobs are no longer committed to Git.
  User passwords are sourced from Kubernetes Secrets at runtime.
- **Single source of truth**: The `.tf` files replace the JSON export and the
  YAML ConfigMap as authoritative realm definition.

### Negative

- **New image to build and maintain**: `dockertmt/mmv2-keycloak-terraform-runner`
  must be built, pushed, and loaded into Kind before the prod overlay can be
  applied. This adds a step to the deployment surface.
- **State coupling**: Terraform state is stored in a Kubernetes Secret. Losing
  that Secret (e.g. accidental namespace deletion) requires `terraform import`
  to reconcile.
- **Startup ordering dependency**: The Terraform Job must complete before
  Keycloak-dependent services can authenticate users. A failed Job blocks the
  deployment until `backoffLimit` is exhausted.
- **Keycloak provider lag**: The `mrparkers/keycloak` provider lags behind the
  Keycloak release cycle. Keycloak 27+ API changes may require a provider update.

### Production validation

After applying the prod overlay, verify the realm:

```bash
# Job completed
kubectl wait --namespace retailstore --for=condition=complete \
  job/keycloak-terraform-runner --timeout=600s

# Realm exists
kubectl exec -n retailstore deployment/keycloak -- \
  /opt/keycloak/bin/kcadm.sh get realms/retailstore \
  --server http://localhost:8080 --realm master \
  --user admin --password <KEYCLOAK_ADMIN_PASSWORD> | grep '"realm" : "retailstore"'

# Direct Access Grant smoke test
curl -sf -X POST http://keycloak.local/realms/retailstore/protocol/openid-connect/token \
  -d "client_id=retailstore-webapp" \
  -d "client_secret=<OAUTH2_CLIENT_SECRET>" \
  -d "grant_type=password" \
  -d "username=retail" \
  -d "password=<RETAIL_PASSWORD>" | grep access_token
```
