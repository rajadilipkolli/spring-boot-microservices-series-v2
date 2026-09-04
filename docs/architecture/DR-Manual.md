# Disaster Recovery & High Availability Manual

## 1. Objectives

- **Recovery Point Objective (RPO)**: 5 minutes. (Maximum acceptable data loss).
- **Recovery Time Objective (RTO)**: 15 minutes. (Maximum time to restore full service after a catastrophic failure).

## 2. PostgreSQL HA & Backup Strategy (CloudNativePG)

We utilize the **CloudNativePG** operator to maintain a Highly Available PostgreSQL cluster in production.

### Architecture
- **Instances**: 3 nodes (1 Primary, 2 Synchronous Replicas).
- **Automated Failover**: Managed automatically by the operator.
- **Backups**: 
  - **WAL Archiving**: Continuous WAL archiving to S3/MinIO.
  - **Full Backups**: Daily at 02:00 UTC.
  - **Retention**: 30 days.

### Restore Procedure (PITR)
To perform a Point-in-Time Recovery (PITR):
1. Locate the exact timestamp required for recovery (e.g., 2026-08-30T10:00:00Z).
2. Create a new Cluster resource specifying the ootstrap.recovery section pointing to the S3 backup bucket.
3. Validate data integrity in the recovered cluster.
4. Update application connection strings to point to the new recovered cluster service.

### Validated Restore Run (Test)
- **Date**: 2026-08-30
- **Result**: SUCCESS. Simulated primary disk failure; operator promoted replica within 4 seconds. Simulated data corruption; PITR restored from WALs to exactly 5 minutes prior successfully.

## 3. Disaster Recovery Environment (Hot Standby)
In the event of a total region failure, a secondary Kubernetes cluster operates in a hot-standby mode.
- Storage volumes (S3) are cross-region replicated.
- DNS failover triggers automatically via Route53 health checks if the primary ingress goes offline for > 2 minutes.
- CloudNativePG replica cluster boots from the replicated S3 bucket.

In the event of a total region failure, a secondary Kubernetes cluster is activated from hot-standby. S3 backup objects must be cross-region replicated before cutover. The repository does not currently define cross-region Kafka replication or a Redis/Sentinel production manifest, so operators must verify those external controls rather than infer readiness from Kubernetes pod status.

### Activation and readiness gates

1. **Declare failover**: record the recovery point, stop automated Route53 failover while validation is in progress, and confirm the primary region cannot accept writes. Do not operate both regions as writers.
2. **Configuration and secrets**: reconcile the same reviewed GitOps revision in the secondary cluster; restore database, Kafka, Redis, Keycloak, TLS, and application credentials from the approved secret store. Compare ConfigMap revisions and secret names (never secret values) with the primary inventory. Gate: every referenced ConfigMap and Secret exists before starting dependent workloads.
3. **PostgreSQL**: confirm S3 replication includes the required base backup and WAL through the recovery point, apply the tested `bootstrap.recovery` manifest, and wait for `Ready`. Gate: three instances are healthy, the expected primary is writable, and integrity queries confirm the selected recovery point.
4. **Kafka**: activate the secondary Strimzi cluster and the separately provisioned cross-region replication process. Gate: the Kafka resource is `Ready`, the required brokers/controllers are in quorum, all application topics exist, and replication lag is within the declared RPO. If no replicated topic data is available, stop; the full-service RPO cannot be met.
5. **Redis/Sentinel**: activate the three Redis nodes and three Sentinels, then promote or discover the secondary master. Gate: `SENTINEL CKQUORUM <master-name>` succeeds, `SENTINEL get-master-addr-by-name <master-name>` returns the promoted endpoint, and replicas report an online master link.
6. **Keycloak**: apply the production overlay and keep public ingress disabled. Gate: all three replicas are available, `keycloak-discovery` has three addresses, and the JGroups `ISPN000094` view contains all three members using the `kubernetes`/`DNS_PING` configuration defined in `ADR-HA-Strategies.md`.
7. **Applications**: update regional endpoints in configuration, restart config-server and then dependent services, and verify every rollout. Gate: health/readiness endpoints pass and no service is using a primary-region database, Kafka, Redis, Keycloak, or secret endpoint.
8. **End-to-end smoke tests**: through the standby ingress, log in via Keycloak; create and read a catalog item; place an order; verify payment and inventory events are consumed; confirm the database state, Kafka consumer lag, and Redis read/write; and check traces/logs for errors. Use synthetic accounts and remove test data afterward.
9. **Traffic cutover**: enable the Route53 record/health-check change only after all gates pass. Confirm the public hostname resolves to the secondary ingress and repeat the smoke tests from outside the cluster.

### Rollback criteria

Abort before traffic cutover if any readiness gate fails, the recovered point exceeds the RPO, configuration or secret versions differ, or smoke tests fail. Restore standby mode and return Route53 automation to its previous state. After the secondary accepts writes, do not point traffic back to the primary until database, Kafka, and Redis data have been reconciled and an incident commander approves a new recovery point; DNS-only rollback risks split-brain and data loss.

## 4. Regional Failover Drill

Run the complete procedure quarterly and after material topology changes. Use an isolated secondary cluster and test hostname, restore from cross-region artifacts, execute every readiness gate and smoke test, inject one Keycloak-pod failure after cutover, then record component start/ready times, data-loss measurements, evidence links, rollback outcome, and owners. The drill passes only if all gates and rollback succeed without manual data repair.

No successful end-to-end regional drill is recorded in this repository. The 2026-08-30 component-test record in section 2 covers CloudNativePG failover/PITR only, so it does not establish a full-service 15-minute RTO.
