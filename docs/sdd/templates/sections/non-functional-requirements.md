# Non-Functional Requirements Specification

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`

This document defines the non-functional quality attributes, Service Level Objectives (SLOs), security controls, and resilience characteristics for `<service-name>`.

---

## 1. Quality Attributes Index *(Mandatory)*

| ID | Dimension | Metric / Target | Evidence Status | Reference Source |
| :--- | :--- | :--- | :--- | :--- |
| `<SVC>-NFR-001` | Availability | 99.9% uptime | Target | `docs/runbooks/SLO-Definitions.md` |
| `<SVC>-NFR-002` | Latency | P99 < 500ms | Target | `docs/runbooks/SLO-Definitions.md` |
| `<SVC>-NFR-003` | Replicas | 1 replica | Configured | `deployment/k8s/base/...` |

---

## 2. Requirement Specifications *(Mandatory)*

### <SVC>-NFR-001: Availability SLO Target

- **ID**: `<SVC>-NFR-001`
- **Category**: Non-Functional Requirement
- **Dimension**: Reliability & Availability
- **Lifecycle Status**: Approved
- **Evidence Status**: Target
- **Target Metric**: 99.9% availability during business hours.
- **Reference**: [SLO-Definitions.md](../../../../docs/runbooks/SLO-Definitions.md)

### <SVC>-NFR-002: Latency SLO Target

- **ID**: `<SVC>-NFR-002`
- **Category**: Non-Functional Requirement
- **Dimension**: Performance
- **Lifecycle Status**: Approved
- **Evidence Status**: Target
- **Target Metric**: P99 response latency under 500ms; consumer processing lag under 1000 messages.
- **Reference**: [SLO-Definitions.md](../../../../docs/runbooks/SLO-Definitions.md)

### <SVC>-NFR-003: High Availability & Pod Disruption Budget

- **ID**: `<SVC>-NFR-003`
- **Category**: Non-Functional Requirement
- **Dimension**: Scalability & Resilience
- **Lifecycle Status**: Approved
- **Evidence Status**: Configured
- **Target Metric**: `minAvailable: 1` enforced via Kubernetes PDB. Single replica in base manifests; autoscaling configured via KEDA/HPA overlay.
- **Reference**: `deployment/k8s/base/pdb.yaml`
