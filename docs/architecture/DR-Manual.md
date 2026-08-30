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

