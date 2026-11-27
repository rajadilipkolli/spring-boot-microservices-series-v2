# ADR-002: Kafka Architecture, Clustering, and Event Sourcing Strategy

## Status
Accepted

## Context
The microservices ecosystem relies on asynchronous event-driven messaging for core business workflows, specifically the distributed order processing saga coordinating `order-service`, `payment-service`, and `inventory-service`.

Requirements differ markedly between development/CI and production environments:
1. **Development & CI**: Fast startup, zero external operator dependencies, minimal memory footprint (< 1GB RAM total for messaging), and ZooKeeper-free simplicity.
2. **Production & Staging**: Zero message loss guarantee, high availability during node failover, automated partition rebalancing, rolling upgrades, and enterprise monitoring via Prometheus JMX metrics.

## Decision
We adopt a bifurcated messaging infrastructure architecture:

1. **Development & CI: Lightweight KRaft (Apache Kafka 3.9+)**:
   - Single-node KRaft-mode broker container deployed via Docker Compose and `k8s/base/kafka.yaml`.
   - ZooKeeper is eliminated entirely. Broker acts as its own controller with node ID 1.
   - Topics configured with single partition and replication factor 1.

2. **Production: Strimzi Kafka Operator Clustered HA**:
   - Strimzi Kafka Operator manages a 3-broker clustered Kafka deployment across failure domains (`topologyKey: topology.kubernetes.io/zone`).
   - Cluster configuration enforces:
     - `offsets.topic.replication.factor: 3`
     - `transaction.state.log.replication.factor: 3`
     - `transaction.state.log.min.isr: 2`
     - `default.replication.factor: 3`
     - `min.insync.replicas: 2`
   - Unclean leader election is strictly disabled (`unclean.leader.election.enable: false`).

3. **Topic Topology and Partitioning**:
   - Domain event topics: `orders`, `payment-orders`, `stock-orders`, `order-events`.
   - Partitions: Minimum 3 partitions per topic in staging/prod to support concurrent consumer group processing.
   - Partition Key: Order ID (`orderId`) is strictly used as the Kafka message key to guarantee strict total ordering of state transitions per order saga instance.

4. **Producer & Consumer Reliability Settings**:
   - **Producers**: `acks=all`, `enable.idempotence=true`, `retries=Integer.MAX_VALUE`, `max.in.flight.requests.per.connection=5`.
   - **Consumers**: `enable.auto.commit=false` with Spring Kafka manual offset acknowledgment modes (`AckMode.RECORD` or `AckMode.MANUAL_IMMEDIATE`) after downstream state transitions commit to PostgreSQL.
   - **Error Handling & Retries**: Spring Kafka `@RetryableTopic` with non-blocking exponential backoff (1s, 2s) and routing to dedicated Dead Letter Queues (`orders-dlt-payment`, `orders-dlt-inventory`).

## Consequences

### Positive
- **Guaranteed Consistency**: `acks=all` combined with `min.insync.replicas=2` and idempotent producers guarantees that acknowledged order events will survive the ungraceful crash of any single Kafka broker.
- **Ordered Saga Execution**: Partitioning by `orderId` ensures all events for a given order lifecycle are processed sequentially by consumer instances.
- **Resource Efficiency**: Developers and CI pipelines run a single lightweight broker without operator overhead.
- **Automated Lifecycle in Production**: Strimzi automates certificate rotation, partition rebalancing, topic creation via CRDs (`KafkaTopic`), and rolling upgrades.

### Negative / Trade-offs
- Operational divergence: Local developers run raw Kafka manifests while production runs Strimzi CRDs. Kustomize overlays in production delete local Kafka StatefulSets and substitute Strimzi manifests.
- Consumer lag risk: High partition counts require active monitoring to prevent consumer group imbalances.

## Verification & Compliance
- Chaos tests must verify that terminating one broker in a 3-node cluster does not cause data loss or pause order event ingestion.
- Prometheus alerts (`KafkaConsumerLag`) trigger if consumer lag exceeds 1000 messages for more than 5 minutes.
