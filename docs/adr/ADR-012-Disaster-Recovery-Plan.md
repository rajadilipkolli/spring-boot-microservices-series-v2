# ADR-012: Disaster Recovery Architecture and Business Continuity Strategy

## Status
Accepted

## Context
A catastrophic event (regional cloud datacenter outage, widespread fiber cut, or severe cyber incident) could render an entire cloud region or cluster permanently or prolonged offline.

To protect business operations and maintain regulatory compliance, the platform requires an explicit Disaster Recovery (DR) plan defining Recovery Point Objectives (RPO), Recovery Time Objectives (RTO), replication topologies, and standardized failover procedures.

## Decision
We establish an **Active-Passive (Warm Standby) Regional Disaster Recovery Architecture**:

1. **Recovery Metrics (SLOs)**:
   - **Recovery Point Objective (RPO)**: **< 15 minutes** (maximum acceptable data loss window for asynchronous transactions).
   - **Recovery Time Objective (RTO)**: **< 30 minutes** (maximum acceptable duration to restore full operational capability in the secondary region).

2. **Regional Topology**:
   - **Primary Region (Active)**: Full production cluster running multi-replica microservices, CNPG PostgreSQL clusters, Strimzi Kafka HA clusters, and active ingress.
   - **Secondary Region (Warm Standby)**:
     - Minimal footprint Kubernetes cluster with core networking, namespace definitions, and operators deployed.
     - Stateless microservice Deployments scaled to 0 or 1 replica.
     - CloudNativePG cluster configured as a replica cluster continuously streaming WAL archives from geo-replicated object storage (S3 cross-region replication or GCS multi-region buckets).
     - Kafka MirrorMaker 2 (MM2) actively replicating topic records and consumer offsets from primary to secondary Kafka brokers.

3. **Data Protection & Backup Cadence**:
   - **PostgreSQL**: Continuous WAL archiving + nightly full logical snapshots (`pg_dump`) and physical base backups via Barman Cloud.
   - **Keycloak**: Nightly automated realm configuration export (`keycloak-realm.json`) stored in version-controlled artifact storage.
   - **Object Storage**: Cross-region replication enabled on all backup buckets with 30-day versioning and immutability (WORM) locks.

4. **Failover Execution (DNS Traffic Switch)**:
   - Managed via Global Server Load Balancing (GSLB) or Route53 / Cloudflare DNS health checks.
   - Failover steps:
     1. Promote secondary CNPG cluster to primary write mode (`cnpg promote`).
     2. Scale microservice Deployments in secondary region from standby to full production replica count.
     3. Update Global DNS records to redirect all customer traffic to the secondary region Ingress IP / LoadBalancer.
     4. Execute synthetic health verification scripts.

## Consequences

### Positive
- **Guaranteed Survivability**: The entire business platform can be reconstituted in an alternate region within 30 minutes of a total primary region loss.
- **Cost Effective**: Warm standby keeps compute costs minimal in the secondary region while keeping database and message storage continuous and up-to-date.
- **Ransomware / Corruption Protection**: Immutable cross-region backups protect against malicious or accidental data deletion.

### Negative / Trade-offs
- Egress Bandwidth Costs: Continuous cross-region replication for PostgreSQL WALs and Kafka MirrorMaker incurs cross-regional data transfer costs.
- Failback Complexity: Failing back to the original region once restored requires reversing replication directions and performing a scheduled maintenance window.

## Verification & Compliance
- Semi-annual DR drill: The engineering team must execute a simulated failover to the secondary region and verify RTO/RPO targets using `docs/runbooks/disaster-recovery.md`.
- Automated daily backup verification jobs must validate that PostgreSQL backup archives are readable and pass integrity verification.
