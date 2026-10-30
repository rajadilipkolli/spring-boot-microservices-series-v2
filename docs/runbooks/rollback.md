# Operational Runbook: Emergency Rollback (Microservices and GitOps Releases)

## 1. Symptoms
- **Context**: A recent deployment introduced critical defects: service crash looping, severe regression in business logic, sudden spike in error rate (> 5%), or unhandled database locking.
- **Alerts**: `HighErrorRate`, `ServerDown`, `OrderFailureRate`, or failed Argo Rollouts canary analysis.
- **Objective**: Restore the microservices platform to the last known stable release within < 5 minutes.

## 2. Dashboards
- **Argo CD Application Dashboard**: `http://argocd:8080/applications/retailstore-prod` (Sync status, revision history, diffs).
- **Service Deployment Dashboard**: `http://grafana:3000/d/deployments` (Rollout progress, pod health, container image versions).
- **SLO & Error Budget Dashboard**: `http://grafana:3000/d/slo-metrics` (Availability budget consumption).

## 3. Diagnosis
1. **Identify the newly released service and version**:
   ```bash
   kubectl get deployments -n retailstore -o custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image
   ```
2. **Review deployment rollout history**:
   ```bash
   kubectl rollout history deployment/<failing-service> -n retailstore
   ```
3. **Inspect Argo CD Sync History**:
   ```bash
   argocd app history retailstore-prod
   ```
4. **Determine if database migrations (Liquibase) were executed**:
   - Check if the release included new Liquibase changeSets in PostgreSQL.
   - *Crucial*: If a destructive schema change occurred (e.g., column drop), rolling back application code alone may cause database mapping errors.

## 4. Mitigation Procedures

### Procedure A: Fast Emergency Kubernetes Rollback (< 2 minutes)
If immediate traffic mitigation is required before GitOps commits can be pushed:
1. **Roll back the specific deployment**:
   ```bash
   kubectl rollout undo deployment/<service-name> -n retailstore
   ```
2. **Monitor the rollback status**:
   ```bash
   kubectl rollout status deployment/<service-name> -n retailstore
   ```
3. **Pause Argo CD Auto-Sync (to prevent re-deploying bad Git commit)**:
   ```bash
   argocd app set retailstore-prod --sync-policy manual
   ```

### Procedure B: Declarative GitOps Rollback (Recommended)
1. **Revert the Git Commit**:
   In the Git repository:
   ```bash
   git revert <bad-commit-sha> -m "Emergency Rollback: Revert release <version>"
   git push origin main
   ```
2. **Trigger Immediate Argo CD Sync**:
   ```bash
   argocd app sync retailstore-prod --prune --force
   ```
3. **Verify GitOps Synchronization**:
   ```bash
   argocd app wait retailstore-prod --health --timeout 300
   ```

### Procedure C: Handling Database Migration Reversals
If the application rollback fails due to incompatible database changes:
1. Identify the applied changeset ID from the `databasechangelog` table:
   ```sql
   SELECT id, author, filename, dateexecuted FROM databasechangelog ORDER BY dateexecuted DESC LIMIT 3;
   ```
2. Execute the corresponding Liquibase rollback script if a backward-compatible rollback changeSet was defined.
3. If schema changes are non-destructive (e.g., added columns or nullable fields), leave schema in place and run older application code.

## 5. Escalation
- **Level 1 (On-Call SRE)**: Execute immediate deployment undo, verify pod recycling, notify service owner.
- **Level 2 (Release Lead / Service Authors)**: Escalate to identify root cause defect, coordinate Git revert, analyze telemetry logs.
- **Level 3 (Engineering Director)**: Escalate if rollback fails to stabilize service or customer transaction data was corrupted.

## 6. Recovery Verification
1. Verify running container image matches the previous stable version:
   ```bash
   kubectl get deployment <service-name> -n retailstore -o jsonpath='{.spec.template.spec.containers[0].image}'
   ```
2. Verify all pods are running and healthy:
   ```bash
   kubectl get pods -n retailstore -l app=<service-name>
   ```
3. Confirm error rate drops below 0.1% on Grafana dashboards:
   - `http_server_requests_seconds_count{status=~"5.."}` drops to 0.
4. Verify core business flows operate normally via E2E smoke test:
   ```bash
   curl -i http://api-gateway:8765/catalog-service/api/catalog/products
   ```
