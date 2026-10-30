# ADR-013: Chaos Engineering and Automated Fault Injection Strategy

## Status
Accepted

## Context
In a distributed microservices platform, unpredictable failures (sudden pod crashes, network latency spikes, packet loss, broker partitions, database primary failovers) are inevitable. 

Relying on optimistic assumptions or manual testing is insufficient to guarantee that Resilience4j circuit breakers, retry topics, Dead Letter Queues, and Kubernetes auto-recovery mechanisms function properly under real stress. System resilience must be continuously and empirically verified.

## Decision
We standardize on **Chaos Mesh** as the automated fault-injection engine for staging and pre-production environments:

1. **Failure Injection Scenarios (Chaos Experiments)**:
   - **Pod Failure (`PodChaos`)**:
     - *Scenario*: Randomly terminate random replicas of `catalog-service`, `order-service`, or `api-gateway` under load.
     - *Expected Outcome*: Gateway routes traffic to remaining healthy replicas; P99 latency degrades by < 15%; zero dropped HTTP 500 requests to clients.
   - **Network Latency & Jitter (`NetworkChaos`)**:
     - *Scenario*: Inject 200ms latency and 5% packet loss between `order-service` and `catalog-service`.
     - *Expected Outcome*: Resilience4j circuit breaker trips to OPEN after threshold; fallback responses are triggered gracefully or cached responses served; thread pools do not exhaust.
   - **Kafka Broker Outage**:
     - *Scenario*: Terminate 1 Kafka broker in the 3-node Strimzi cluster while an order creation batch is executing.
     - *Expected Outcome*: Idempotent producers retry and reconnect to the new partition leader; Kafka transactions commit with zero message duplication or loss.
   - **Database Primary Crash (`CNPG Failover`)**:
     - *Scenario*: Forcibly kill the PostgreSQL primary pod (`cnpg promote / crash`).
     - *Expected Outcome*: CNPG promotes a standby within 10 seconds; HikariCP connection pools in microservices automatically discard severed connections and reconnect to the new primary without requiring pod restarts.

2. **Automated Pipeline Integration**:
   - Chaos experiments run on a scheduled weekly cadence in staging and as a mandatory stage in pre-release qualification pipelines.
   - Steady-State Hypothesis: During any chaos experiment, the platform's Availability SLO (> 99.9%) and Order Success Rate SLO (> 95%) must not be breached. If an experiment breaches an SLO, the pipeline fails and blocks the release.

3. **Safety Guardrails**:
   - Chaos experiments are strictly disabled in production (`overlays/prod/`) unless explicitly authorized for a scheduled GameDay with active incident commander oversight.
   - Every Chaos Mesh experiment specifies an automatic `duration` (e.g., 5m) and emergency abort trigger.

## Consequences

### Positive
- **Validated Resilience**: Proves that circuit breakers, retries, and failovers work before real incidents occur.
- **Incident Muscle Memory**: Operational teams gain experience diagnosing real degradation symptoms on Grafana dashboards.
- **Continuous Hardening**: Identifies hidden coupling, unhandled exceptions, and misconfigured timeouts proactively.

### Negative / Trade-offs
- Staging Resource Overhead: Requires dedicated cluster resources to run chaos operators and background load generators.
- False Positives: Flaky network conditions during experiments can occasionally fail qualification runs, requiring tuned tolerance thresholds.

## Verification & Compliance
- Chaos experiments must be committed as declarative YAML manifests in `deployment/k8s/chaos/`.
- CI qualification runs must verify that steady-state metrics remain satisfied throughout the fault injection window.
