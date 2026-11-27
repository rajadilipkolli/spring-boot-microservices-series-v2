# ADR-011: High Availability, Topology Spread, and Zero-Downtime Deployment Strategies

## Status
Accepted

## Context
Production workloads require high resilience against hardware failures, node evictions, cluster maintenance upgrades, and sudden traffic spikes. Single-replica deployments or unconstrained scheduling expose the application to complete downtime if a Kubernetes worker node experiences a hardware fault or reboot.

Furthermore, rolling updates can cause dropped HTTP requests if new pods accept traffic before they are fully initialized or if terminating pods are terminated abruptly before finishing in-flight requests.

## Decision
We enforce an enterprise **High Availability (HA) and Zero-Downtime Architecture** across staging and production overlays:

1. **Multi-Replica Deployment Topology**:
   - In production (`overlays/prod/`), all stateless microservices (`api-gateway`, `catalog-service`, `inventory-service`, `order-service`, `payment-service`, `retail-store-webapp`) run with a minimum of **3 replicas**.
   - In staging (`overlays/staging/`), services run with a minimum of **2 replicas** to test multi-instance behavior.

2. **Failure Domain Isolation (Zonal Anti-Affinity)**:
   - Deployments enforce `podAntiAffinity` using `topologyKey: topology.kubernetes.io/zone`:
     - Preferred or required anti-affinity ensures that replicas of the same service are scheduled on different physical nodes and across distinct cloud availability zones.
     - A single zone or rack outage cannot bring down more than 1 replica of any service.

3. **Disruption Protection (PodDisruptionBudget - PDB)**:
   - Every microservice deployment is paired with a `PodDisruptionBudget` configured with `minAvailable: 1` or `maxUnavailable: 1`.
   - Node drains (during Kubernetes cluster upgrades or autoscaling scale-down) are prevented from evicting pods if doing so would violate the minimum quorum.

4. **Zero-Downtime Rolling Updates**:
   - `Deployment.spec.strategy.rollingUpdate` is configured with:
     - `maxSurge: 25%` (allows Kubernetes to provision new pods before terminating old ones).
     - `maxUnavailable: 0` (guarantees that 100% of existing capacity remains available during a rollout).

5. **Graceful Shutdown & Health Probes**:
   - Spring Boot graceful shutdown is enabled: `server.shutdown: graceful` with a 30-second timeout window (`spring.lifecycle.timeout-per-shutdown-phase: 30s`).
   - Container lifecycle hooks include a `preStop` sleep hook (`sleep 5`) to allow ingress controllers and kube-proxy iptables rules to drain before the JVM begins closing socket listeners.
   - Probes separate liveness from readiness:
     - **Liveness**: `/actuator/health/liveness` (checks if the JVM is responsive; triggers pod restart only if deadlocked).
     - **Readiness**: `/actuator/health/readiness` (checks database, Kafka, and cache connectivity; removes pod from service endpoints if unready, without restarting).

## Consequences

### Positive
- **Fault-Tolerant Operation**: Survives sudden loss of any individual Kubernetes node or availability zone without user disruption.
- **Zero-Downtime Releases**: Rolling upgrades seamlessly transition user traffic without dropping active connections or returning 502/503 errors.
- **Maintenance Safety**: Automated cluster OS patching and node drain operations proceed without violating service availability SLOs.

### Negative / Trade-offs
- Increased Resource Requirements: Running 3 replicas per service in production requires a minimum cluster capacity of 3 worker nodes and sufficient CPU/RAM buffers.
- Overlay Specialization: Local `dev` and `ci` overlays must explicitly strip anti-affinity and PDBs to run cleanly on single-node environments.

## Verification & Compliance
- Chaos experiments (`ADR-013`) must terminate worker nodes while running continuous Gatling/E2E load tests to verify zero dropped requests.
- `kubectl get pdb -n retailstore` must confirm all microservices have active, satisfied disruption budgets.
