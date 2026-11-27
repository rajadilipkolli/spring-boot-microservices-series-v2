# ADR-003: PostgreSQL Persistence Architecture and CloudNativePG Operator

## Status
Accepted

## Context
Multiple microservices (`catalog-service`, `inventory-service`, `order-service`, `payment-service`, and `keycloak`) require ACID-compliant relational persistence.

In earlier configurations, database instances ran as ephemeral standalone containers or single-replica Deployments with manually mounted host paths. This model is inadequate for production due to:
1. No automatic failover or high availability during node crashes.
2. Inability to perform non-disruptive PostgreSQL version minor/major upgrades.
3. Lack of automated, continuous Point-In-Time-Recovery (PITR) and continuous WAL (Write-Ahead Logging) archiving.
4. Risk of split-brain or data corruption during network partitions.

## Decision
We standardize on **CloudNativePG (CNPG)** as the enterprise Kubernetes-native PostgreSQL operator for staging and production environments, while maintaining standalone single-container PostgreSQL for development and CI:

1. **Production Cluster Topology (CNPG `Cluster` CRD)**:
   - **Instance Count**: 3 instances (1 Primary, 2 Streaming Standbys) distributed across availability zones via anti-affinity.
   - **Replication Mode**: Synchronous or high-priority asynchronous streaming replication with `minSyncReplicas: 1` and `maxSyncReplicas: 1` to guarantee zero committed transaction loss on primary node failure.
   - **Failover & Election**: Automated failover powered by Raft-based consensus embedded in CNPG; failover typically completes within < 10 seconds.
   - **Storage**: Independent dedicated PersistentVolumeClaims (PVCs) per pod using high-IOPS storage classes (`gp3` or local SSDs with CSI).

2. **Continuous Backup & Point-In-Time-Recovery (PITR)**:
   - Automated WAL archiving to object storage (e.g., S3/MinIO/GCS) via Barman Cloud integration native to CNPG.
   - Nightly base backups scheduled via `ScheduledBackup` CRD, combined with continuous WAL archiving, enabling PITR with recovery point objective (RPO) < 5 minutes.

3. **Connection Pooling & Service Architecture**:
   - Each CNPG cluster generates 3 distinct Kubernetes services:
     - `<cluster>-rw`: Routes write traffic exclusively to the current primary instance.
     - `<cluster>-ro`: Routes read-only analytical traffic across healthy standby replicas.
     - `<cluster>-r`: Routes read-only traffic including primary (for reporting).
   - Microservices connect to `<cluster>-rw` using HikariCP connection pools configured with optimal pool sizes (`maximumPoolSize: 10-20`) and TCP keepalive parameters.

4. **Database Schema Evolution**:
   - Managed strictly via **Liquibase** embedded within each microservice application.
   - Migrations are versioned, idempotent, and executed automatically on service startup before accepting traffic (`spring.liquibase.enabled: true`).

## Consequences

### Positive
- **High Availability**: Automated primary failure detection and promotion without human intervention.
- **Data Durability**: Continuous WAL archiving guarantees recovery even in the event of total storage volume loss.
- **Safe Maintenance**: Zero-downtime rolling maintenance for minor PostgreSQL version updates and OS kernel security patches.
- **Declarative Operations**: Database configuration, extensions (e.g., `pg_trgm`), and user credentials managed declaratively via Kubernetes custom resources.

### Negative / Trade-offs
- CRD Dependency: Requires installing the CloudNativePG operator in the cluster (`cnpg-system` namespace).
- Storage costs: 3-node replication triples storage consumption for persistent volumes.

## Verification & Compliance
- Chaos testing must confirm that killing the primary PostgreSQL pod causes the standby to promote cleanly and Spring Boot microservices reconnect automatically within HikariCP retry limits.
- Nightly automated backup verification jobs must restore test snapshots to validate backup integrity.
