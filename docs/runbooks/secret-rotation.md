# Operational Runbook: Secret Rotation and Credential Synchronization

## 1. Symptoms
- **Context**: Scheduled quarterly credential rotation, or emergency rotation triggered by a suspected credential compromise.
- **Alerts (if misconfigured)**: `SecretSyncFailed` in External Secrets Operator, `DatabaseAuthenticationFailed` (PostgreSQL `28P01: password authentication failed`), or `OAuth2ClientAuthenticationFailed` in `retail-store-webapp`.
- **Target Secrets**:
  - `postgres-credentials` (PostgreSQL database passwords)
  - `keycloak-admin-credentials` (Keycloak administrator credentials)
  - `keycloak-user-passwords` (Application demo user passwords)
  - `webapp-oauth2-credentials` (Keycloak OAuth2 client secret)
  - `config-server-credentials` (Spring Cloud Config basic auth)

## 2. Dashboards
- **External Secrets Operator Dashboard**: `http://grafana:3000/d/external-secrets` (Sync status, last sync timestamp, reconciliation error count).
- **Service Authentication Error Rate**: `http://grafana:3000/d/security-audit` (401 Unauthorized / 403 Forbidden rates across microservices).

## 3. Diagnosis
1. **Check ExternalSecret Synchronization Status**:
   ```bash
   kubectl get externalsecrets -n retailstore
   ```
   *Verify all items report `STATUS: SecretSynced`.*
2. **Describe failing ExternalSecret resources**:
   ```bash
   kubectl describe externalsecret <secret-name> -n retailstore
   ```
   *Look for provider authentication errors or missing remote secret keys.*
3. **Verify Kubernetes Secret generation**:
   ```bash
   kubectl get secret <secret-name> -n retailstore -o yaml
   ```

## 4. Mitigation & Rotation Procedures

### Procedure A: Dual-Credential PostgreSQL Password Rotation
To rotate database passwords with zero application downtime:
1. **Add new password alias in PostgreSQL**:
   ```sql
   ALTER USER appuser WITH PASSWORD 'new-secure-password-2026';
   ```
2. **Update secret in upstream SecretStore / Kubernetes SecretStore**:
   Update `postgres-appuser-secret` with the new password.
3. **Trigger Immediate ExternalSecret Sync**:
   ```bash
   kubectl annotate externalsecret postgres-credentials -n retailstore force-sync=$(date +%s) --overwrite
   ```
4. **Perform Rolling Restart of Microservices**:
   Because JVM environment variables are immutable at runtime, trigger a rolling deployment:
   ```bash
   kubectl rollout restart deployment/catalog-service deployment/inventory-service deployment/order-service deployment/payment-service -n retailstore
   ```

### Procedure B: Keycloak Client Secret Rotation
1. Generate new client secret in Keycloak realm `retailstore` for client `retail-store-webapp`.
2. Update the upstream secret store for `webapp-oauth2-credentials`.
3. Force sync `webapp-oauth2-credentials` ExternalSecret.
4. Restart webapp:
   ```bash
   kubectl rollout restart deployment/retail-store-webapp -n retailstore
   ```

### Procedure C: Emergency Rollback
If rotated credentials cause widespread authentication failures:
1. Revert upstream SecretStore value to previous known good password.
2. Force sync the ExternalSecret.
3. Restart affected deployments immediately.

## 5. Escalation
- **Level 1 (On-Call SRE)**: Execute planned rotation playbook and monitor pod restart progression.
- **Level 2 (Security Operations / IAM Lead)**: Escalate if suspected credential compromise requires revoking root admin tokens or rotating cluster CA certificates.
- **Level 3 (Database Administrator)**: Escalate if database user locks occur due to excessive failed connection attempts.

## 6. Recovery Verification
1. Verify all `ExternalSecret` resources are synced:
   ```bash
   kubectl get externalsecrets -n retailstore
   ```
2. Verify zero authentication errors in microservice logs:
   ```bash
   kubectl logs -n retailstore deployment/order-service --tail=50 | grep -E "authentication failed|AccessDenied"
   ```
3. Test end-to-end API and UI login workflows:
   - Perform user login on `retail-store-webapp` via Keycloak SSO.
   - Execute an authenticated API Gateway call with a bearer token.
