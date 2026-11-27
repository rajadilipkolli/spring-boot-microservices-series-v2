# Operational Runbook: Database Failure (PostgreSQL / CloudNativePG)

## 1. Symptoms
- **Alert Triggered**: `DatabaseDown`, `PostgresConnectionPoolExhausted`, or widespread HTTP 500 errors across `catalog-service`, `order-service`, `inventory-service`, `payment-service`, or `keycloak`.
- **Client Impact**: Orders cannot be created or queried; product lookups fail; authentication fails in Keycloak.
- **Application Logs**: `org.postgresql.util.PSQLException: Connection to localhost:5432 refused`, `CannotGetJdbcConnectionException`, `HikariPool-1 - Connection is not available, request timed out after 30000ms`.

## 2. Dashboards
- **PostgreSQL / CNPG Grafana Dashboard**: `http://grafana:3000/d/cnpg-cluster` (Replication lag, active connections, WAL generation rate, disk usage).
- **HikariCP Connection Pool Dashboard**: `http://grafana:3000/d/hikaricp-metrics` (Active connections, pending threads, acquire time).
- **Kubernetes PVC / Storage Dashboard**: Disk usage and IOPS saturation on persistent volumes.

## 3. Diagnosis
1. **Check CloudNativePG Cluster Status**:
   ```bash
   kubectl get cluster -n retailstore
   kubectl describe cluster postgresql -n retailstore
   ```
   *Look for `Status: Cluster in healthy state` vs `Failing over` or `Degraded`.*
2. **Identify Primary and Standby Pods**:
   ```bash
   kubectl get pods -n retailstore -l cnpg.io/cluster=postgresql -L role
   ```
3. **Inspect PostgreSQL Engine Logs**:
   ```bash
   kubectl logs postgresql-1 -n retailstore -c postgres --tail=200
   ```
   *Check for disk full errors (`No space left on device`), transaction log corruption, or out-of-memory kills.*
4. **Inspect Connection Pool & Lock Contention**:
   Connect via psql to the read-write service:
   ```bash
   kubectl exec -it postgresql-1 -n retailstore -c postgres -- psql -U appuser -d orderdb -c "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"
   kubectl exec -it postgresql-1 -n retailstore -c postgres -- psql -U appuser -d orderdb -c "SELECT pid, query, age(clock_timestamp(), query_start) FROM pg_stat_activity WHERE state != 'idle' ORDER BY age DESC LIMIT 5;"
   ```

## 4. Mitigation
1. **Automated / Manual Primary Failover**:
   If the primary node is unresponsive and CNPG has not triggered failover:
   ```bash
   kubectl cnpg promote postgresql <standby-instance-pod> -n retailstore
   ```
2. **Disk Space Exhaustion**:
   If PersistentVolumes are 100% full due to WAL accumulation:
   ```bash
   # Resize PVC dynamically if supported by StorageClass:
   kubectl patch pvc -n retailstore <pvc-name> -p '{"spec":{"resources":{"requests":{"storage":"50Gi"}}}}'
   ```
3. **Deadlock / Long-Running Query Termination**:
   If stuck queries are exhausting the connection pool:
   ```bash
   SELECT pg_terminate_backend(<pid>);
   ```
4. **Corrupted Instance Rebuild**:
   If an individual replica has corrupted storage, trigger CNPG instance re-cloning:
   ```bash
   kubectl cnpg restart postgresql -n retailstore
   # Or delete the broken pod so CNPG re-creates and re-syncs it from the primary:
   kubectl delete pod <broken-standby-pod> -n retailstore
   ```

## 5. Escalation
- **Level 1 (On-Call SRE)**: Triage cluster status, verify failover progression, clear stuck connections.
- **Level 2 (Database Administrator / Lead SRE)**: Escalate if WAL archiving fails, replication is broken across all standbys, or manual PITR restoration is required.
- **Level 3 (Platform Storage Team / Cloud Vendor)**: Escalate for SAN / EBS volume detachment or hardware I/O degradation.

## 6. Recovery Verification
1. Verify CNPG reports primary status and all standbys synchronized:
   ```bash
   kubectl cnpg status postgresql -n retailstore
   ```
2. Check that application connection pools have re-established connections:
   - Verify HikariCP metric `hikaricp_connections_active` stabilizes.
   - Verify zero connection timeout exceptions in application logs:
   ```bash
   kubectl logs -n retailstore deployment/order-service --tail=50 | grep -i "hikaripool"
   ```
3. Test end-to-end database write and read through API Gateway:
   ```bash
   curl -i http://api-gateway:8765/catalog-service/api/catalog/products
   ```
