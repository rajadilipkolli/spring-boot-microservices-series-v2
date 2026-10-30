# Operational Runbook: High Kafka Consumer Lag

## 1. Symptoms
- **Alert Triggered**: `KafkaConsumerLag` (`sum(kafka_consumer_group_lag) by (group, topic) > 1000` for > 5m).
- **Client Impact**: Orders remain in `NEW` state for prolonged periods; order completion delays; customer checkout confirmation is delayed.
- **Metrics**: Grafana shows `kafka_consumer_group_lag` climbing steadily on topics `orders`, `payment-orders`, or `stock-orders`.

## 2. Dashboards
- **Kafka Consumer Lag Dashboard**: `http://grafana:3000/d/kafka-lag` (Consumer group lag per partition, commit rate, offset progression).
- **Service JVM & Thread Dashboard**: `http://grafana:3000/d/jvm-metrics` (Consumer listener threads, GC pause times, CPU throttling).
- **KEDA Autoscaling Dashboard**: `http://grafana:3000/d/keda-autoscaling` (Trigger metrics, replica scale status).

## 3. Diagnosis
1. **Identify the consumer group and partition with lag**:
   ```bash
   kubectl exec -it <kafka-pod> -n retailstore -- /opt/kafka/bin/kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 --describe --group order-service-group
   ```
   *Note `CURRENT-OFFSET`, `LOG-END-OFFSET`, `LAG`, and `CONSUMER-ID`.*
2. **Determine if the lag is caused by a slow consumer or consumer death**:
   - Check if consumer pods are crashing or restarting:
     ```bash
     kubectl get pods -n retailstore -l app=order-service
     ```
   - Check if consumers are experiencing Stop-The-World GC pauses:
     *Inspect `jvm_gc_pause_seconds_max` in Prometheus.*
3. **Check for Frequent Partition Rebalances**:
   Inspect consumer service logs:
   ```bash
   kubectl logs -n retailstore deployment/order-service --tail=200 | grep -i "rebalance"
   ```
   *Look for `CommitFailedException` or `max.poll.interval.ms exceeded`, indicating the consumer took too long processing a batch.*
4. **Inspect Downstream Bottlenecks**:
   - Is `inventory-service` or `payment-service` blocked waiting for PostgreSQL database locks? (Check DB connection pools).
   - Is external Keycloak authentication or API throttling introducing latency?

## 4. Mitigation
1. **Scale Out Consumer Replicas**:
   If partition count allows (e.g., 3 partitions on the topic):
   ```bash
   kubectl scale deployment/<consumer-service> -n retailstore --replicas=3
   ```
   *(Note: Scaling beyond the number of topic partitions will result in idle consumer instances).*
2. **Increase Consumer Concurrency**:
   If scaling pods is constrained, tune Spring Kafka listener container concurrency:
   - Patch `SPRING_KAFKA_LISTENER_CONCURRENCY=3`.
3. **Isolate Poison Pill / Stuck Record**:
   If a single malformed message is repeatedly triggering retries and delaying the consumer queue:
   - Follow [DLQ-Handling.md](DLQ-Handling.md) to inspect error logs and route the offending record to the DLT (`orders-dlt-payment` or `orders-dlt-inventory`).
4. **Relieve Database / Downstream Pressure**:
   - Temporarily increase connection pool size or tune long-running query locks on the target database.

## 5. Escalation
- **Level 1 (On-Call SRE)**: Check consumer group status, scale consumer pods up to partition count, check for poison pills.
- **Level 2 (Service Team Lead)**: Escalate if consumer loop is deadlocked or downstream processing logic has a performance regression.
- **Level 3 (Platform Architect)**: Escalate if topic repartitioning is required under sustained traffic growth.

## 6. Recovery Verification
1. Verify consumer lag trends downwards toward 0:
   ```bash
   kubectl exec -it <kafka-pod> -n retailstore -- /opt/kafka/bin/kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 --describe --group order-service-group
   ```
   *Lag on all partitions must drop below 100.*
2. Verify order processing latency recovers:
   - P99 order completion time returns to < 2.0s.
3. Confirm Prometheus alert `KafkaConsumerLag` transitions to resolved.
