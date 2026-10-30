# Operational Runbook: Kafka Outage (Cluster Degradation & Broker Failure)

## 1. Symptoms
- **Alert Triggered**: `KafkaBrokerDown`, `UnderReplicatedPartitions`, `NoLeaderPartitions`, or `KafkaConnectionFailed`.
- **Client Impact**: Orders cannot proceed through the saga lifecycle; `order-service` buffers or fails on Kafka dispatch; `payment-service` and `inventory-service` stop receiving new events.
- **Application Logs**:
  - `org.apache.kafka.common.errors.TimeoutException: Topic orders not present in metadata after 60000 ms`.
  - `org.apache.kafka.common.errors.NotLeaderOrFollowerException`.
  - `org.springframework.kafka.KafkaException: Send failed; nested exception is org.apache.kafka.common.errors.NetworkException`.

## 2. Dashboards
- **Kafka Cluster Overview (Grafana)**: `http://grafana:3000/d/strimzi-kafka` (Broker status, UnderReplicatedPartitions, OfflinePartitionsCount, ActiveControllerCount).
- **Kafka Producer / Consumer Rates**: `http://grafana:3000/d/kafka-throughput` (Messages in/sec, Bytes in/sec per topic).
- **Microservice Resiliency Dashboard**: `http://grafana:3000/d/resilience4j` (Event publisher retry rates and fallback counters).

## 3. Diagnosis
1. **Check Kafka Broker Pods & Controller**:
   - In Strimzi (Prod):
     ```bash
     kubectl get kafka -n retailstore
     kubectl get pods -n retailstore -l strimzi.io/name=retail-cluster-kafka
     ```
   - In Base / Dev (KRaft):
     ```bash
     kubectl get pods -n retailstore -l app=kafka
     ```
2. **Inspect Broker Logs for Fatal Errors**:
   ```bash
   kubectl logs -n retailstore <kafka-pod-name> --tail=200
   ```
   *Look for disk full errors, KRaft quorum partition isolation, or JVM OutOfMemory errors.*
3. **Verify Partition Leadership & Under-Replicated Partitions**:
   ```bash
   kubectl exec -it <kafka-pod-name> -n retailstore -- /opt/kafka/bin/kafka-topics.sh \
     --bootstrap-server localhost:9092 --describe --under-replicated-partitions
   ```
4. **Check Storage Utilization**:
   ```bash
   kubectl exec -it <kafka-pod-name> -n retailstore -- df -h /var/lib/kafka/data
   ```

## 4. Mitigation
1. **Restarting an Unresponsive Broker**:
   If a single broker is deadlocked or in CrashLoopBackOff:
   ```bash
   kubectl delete pod <unresponsive-broker-pod> -n retailstore
   ```
   *The StatefulSet will recreate the pod; Kafka will replay the local segment log and rejoin the cluster ISR.*
2. **Broker Disk Space Exhaustion**:
   If log directories are full:
   - Temporarily reduce retention period on non-critical topics:
     ```bash
     kubectl exec -it <kafka-pod-name> -n retailstore -- /opt/kafka/bin/kafka-configs.sh \
       --bootstrap-server localhost:9092 --entity-type topics --entity-name order-events \
       --alter --add-config retention.ms=3600000
     ```
   - Expand the PersistentVolumeClaim size via K8s volume expansion.
3. **KRaft Controller Quorum Recovery**:
   If the active controller is lost and election cannot complete, restart all broker pods sequentially to reform the KRaft consensus quorum.

## 5. Escalation
- **Level 1 (On-Call SRE)**: Check pod states, restart hung broker, assess under-replicated partitions.
- **Level 2 (Messaging / Streaming Specialist)**: Escalate if partition leader election fails or persistent volume corruption occurs.
- **Level 3 (Platform Engineering & Data Infrastructure)**: Escalate if cross-AZ network partitions sever broker-to-broker connectivity.

## 6. Recovery Verification
1. Verify `UnderReplicatedPartitions` drops to 0:
   ```bash
   kubectl exec -it <kafka-pod-name> -n retailstore -- /opt/kafka/bin/kafka-topics.sh \
     --bootstrap-server localhost:9092 --describe --under-replicated-partitions
   ```
   *Output must be empty.*
2. Confirm active controller count is exactly 1:
   - Prometheus metric `kafka_controller_kafkacontroller_activecontrollercount == 1`.
3. Verify event flow between `order-service` and `payment-service`:
   - Send test order via Gateway and confirm status transitions to `CONFIRMED`.
   - Verify `orders_completed_total` increases in Prometheus.
