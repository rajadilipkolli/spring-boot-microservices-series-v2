# Non-Functional Requirements Specification: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`

This document defines the non-functional quality attributes, Service Level Objectives (SLOs), security boundaries, and resilience metrics for `inventory-service`.

---

## 1. Quality Attributes Index

| ID | Dimension | Metric / Target | Evidence Status | Reference Source |
| :--- | :--- | :--- | :--- | :--- |
| `INV-NFR-001` | Availability | 99.9% uptime | Target | `docs/runbooks/SLO-Definitions.md` |
| `INV-NFR-002` | Latency (Read/Write) | P99 < 500ms | Target | `docs/runbooks/SLO-Definitions.md` |
| `INV-NFR-003` | Messaging Lag | Lag < 1000 messages | Target | `docs/runbooks/SLO-Definitions.md` |
| `INV-NFR-004` | High Availability & PDB | `minAvailable: 1` | Configured | `deployment/k8s/base/pdb.yaml` |
| `INV-NFR-005` | Autoscaling | KEDA Kafka ScaledObject | Configured | `deployment/k8s/overlays/autoscaling/` |
| `INV-NFR-006` | Kubernetes Hardening | Non-root, read-only root FS | Configured | `deployment/k8s/base/inventory-service.yaml` |
| `INV-NFR-007` | Network Segmentation | Calico / K8s NetworkPolicy | Configured | `deployment/k8s/base/network-policies.yaml` |
| `INV-NFR-008` | Service Authentication | Direct Service Auth Check | Needs-Clarification | `INV-Q-005` |

---

## 2. Requirement Specifications

### INV-NFR-001: Availability SLO Target
- **ID**: `INV-NFR-001`
- **Category**: Non-Functional Requirement
- **Dimension**: Availability
- **Lifecycle Status**: Approved
- **Evidence Status**: Target
- **Metric**: Maintain 99.9% availability during business hours (error budget: 0.1% failed requests).
- **Reference**: Linked directly to operational baseline [SLO-Definitions.md](../../../../docs/runbooks/SLO-Definitions.md).

---

### INV-NFR-002: Latency SLO Target
- **ID**: `INV-NFR-002`
- **Category**: Non-Functional Requirement
- **Dimension**: Performance
- **Lifecycle Status**: Approved
- **Evidence Status**: Target
- **Metric**: P99 response time under 500ms across all synchronous REST endpoints under nominal load.
- **Reference**: [SLO-Definitions.md](../../../../docs/runbooks/SLO-Definitions.md).

---

### INV-NFR-003: Kafka Consumer Lag Target
- **ID**: `INV-NFR-003`
- **Category**: Non-Functional Requirement
- **Dimension**: Resilience & Scalability
- **Lifecycle Status**: Approved
- **Evidence Status**: Target
- **Metric**: Kafka consumer lag for group `stock` on topic `orders` must remain under 1000 messages. Lag exceeding 1000 triggers alerts.
- **Reference**: [SLO-Definitions.md](../../../../docs/runbooks/SLO-Definitions.md) and [kafka-lag.md](../../../../docs/runbooks/kafka-lag.md).

---

### INV-NFR-004: Pod Disruption Budget
- **ID**: `INV-NFR-004`
- **Category**: Non-Functional Requirement
- **Dimension**: High Availability
- **Lifecycle Status**: Approved
- **Evidence Status**: Configured
- **Metric**: Base Kubernetes deployment defines 1 replica with a PodDisruptionBudget ensuring `minAvailable: 1`. During voluntary evictions, at least one pod must remain operational.
- **Reference**: `deployment/k8s/base/pdb.yaml`.

---

### INV-NFR-005: Event-Driven Autoscaling (KEDA)
- **ID**: `INV-NFR-005`
- **Category**: Non-Functional Requirement
- **Dimension**: Scalability
- **Lifecycle Status**: Approved
- **Evidence Status**: Configured
- **Metric**: Autoscaling overlay configures KEDA `ScaledObject` (`inventory-service-scaler`) monitoring consumer group `stock` on topic `orders`. Scales pods from `minReplicaCount: 1` to `maxReplicaCount: 5` with a lag threshold of `2`. Base manifest has no HPA configured.
- **Reference**: `deployment/k8s/overlays/autoscaling/keda.yaml`.

---

### INV-NFR-006: Kubernetes Container Security Hardening
- **ID**: `INV-NFR-006`
- **Category**: Non-Functional Requirement
- **Dimension**: Security
- **Lifecycle Status**: Approved
- **Evidence Status**: Configured
- **Metric**: Pod runs in hardened mode:
  - `runAsNonRoot: true`
  - `runAsUser: 1000`
  - `runAsGroup: 1000`
  - `fsGroup: 1000`
  - `readOnlyRootFilesystem: true`
  - `allowPrivilegeEscalation: false`
  - `capabilities: drop: [ALL]`
  - `seccompProfile: type: RuntimeDefault`
  - Writable storage limited to temporary in-memory volume at `/tmp`.
- **Reference**: `deployment/k8s/base/inventory-service.yaml`.

---

### INV-NFR-007: Kubernetes Network Policy Isolation
- **ID**: `INV-NFR-007`
- **Category**: Non-Functional Requirement
- **Dimension**: Security & Network Isolation
- **Lifecycle Status**: Approved
- **Evidence Status**: Configured
- **Metric**: Network isolation enforced via `retailstore` namespace network policies:
  - Default deny-all ingress and egress (`default-deny-all`).
  - Ingress permitted from pods with label `app in [catalog-service, order-service, payment-service, api-gateway, retail-store-webapp]`.
  - Egress permitted to `postgresql`, `kafka`, `config-server`, `service-registry`, DNS (53), and HTTPS (443).
- **Reference**: `deployment/k8s/base/network-policies.yaml`.

---

### INV-NFR-008: Service-Level Authentication & Authorization Boundary
- **ID**: `INV-NFR-008`
- **Category**: Non-Functional Requirement
- **Dimension**: Security
- **Lifecycle Status**: Approved
- **Evidence Status**: Needs-Clarification
- **Metric**: `inventory-service` contains **no** Spring Security dependencies, OAuth2 filters, or JWT validation tokens in its classpath. All HTTP requests arriving at port 18181 are accepted anonymously if they reach the service. Perimeter authentication relies entirely on upstream `api-gateway` and NetworkPolicies.
- **Reference**: `inventory-service/pom.xml` and linked question `INV-Q-005`.
