# Integrations and Messaging Specification: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`
- **Messaging System**: Apache Kafka
- **Event Outbox Mechanism**: Spring Modulith Transactional Outbox

---

## 1. Exact Kafka Topic Mappings

| Topic Name | Direction | Consumer Group / Listener ID | Key Strategy | Value Payload | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `orders` | Inbound (Consume) | `stock` (`inventory-orders`) | `orderId` (String) | `OrderDto` (JSON) | Inbound order saga commands (`NEW`, `CONFIRMED`, `ROLLBACK`). |
| `productTopic` | Inbound (Consume) | `product` (`products`) | Aggregate ID (String) | `ProductDto` (JSON) | Inbound product catalog creations and deletions from Catalog Service. |
| `stock-orders` | Outbound (Produce) | Modulith Outbox | `orderId` (String) | Flat `OrderDto` (JSON) | Outbound inventory processing outcomes (`ACCEPT` or `REJECT`) with `source = "INVENTORY"`. |

---

## 2. Event Schemas and Cross-Service Correlation

### 2.1 Outbound Event: `InventoryProcessedEvent`
- **Class Reference**: `inventory-service/src/main/java/com/example/inventoryservice/inventory/events/InventoryProcessedEvent.java#InventoryProcessedEvent`
- **Destination Topic**: `stock-orders`
- **Key**: `String.valueOf(order.orderId())`
- **Serialization Mechanics**:
  - Annotated with `@Externalized("stock-orders")` and `@JsonValue` on `OrderDto order`.
  - Wrapped with custom `ByteArrayJacksonJsonMessageConverter` configured to return empty Kafka headers:
    ```java
    @Override
    protected Headers initialRecordHeaders(Message<?> message) {
        return new RecordHeaders();
    }
    ```
  - **Flat JSON without Type Headers**: Prevents leaking internal Spring Modulith or Inventory Java class names across service boundaries. Produces raw, flat `OrderDto` JSON matching downstream Kafka Streams deserialization expectations.

### 2.2 Cross-Service Correlation with Order Service
- **Downstream Consumer**: `order-service` via `KafkaStreamsConfig.java`.
- **Join Correlation Key**: `correlateByAttempt` combines `orderId + ":" + orderDto.attemptId()`.
- **Stream Join**: Joins `payment-orders` stream with `stock-orders` stream over a 30-second window with a 30-second grace period:
  ```java
  JoinWindows.ofTimeDifferenceAndGrace(Duration.ofSeconds(30), Duration.ofSeconds(30))
  ```
- **Join Aggregator**: Invokes `OrderManageService.confirm(paymentOrder, stockOrder)`. Non-null outcomes are republished to topic `orders` keyed by `orderId`.

### 2.3 Upstream Producer: Catalog Service
- **Producer**: `catalog-service` via `OutboxPublisher.java` and Spring Cloud Stream binding `inventory-out-0`.
- **Destination**: `productTopic`.
- **Payload**: `ProductDto`.

---

## 3. Resilience, Retries, and Error Handling

### 3.1 Non-Blocking Retry Configuration
Inbound Kafka listeners in `KafkaListenerConfig.java` configure non-blocking retries using `@RetryableTopic`:

| Listener ID | Bound Topic | Suffixing Strategy | Retry Suffix | DLT Suffix | Backoff Policy |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `inventory-orders` | `orders` | `SUFFIX_WITH_INDEX_VALUE` | `-retry-inventory` | `-dlt-inventory` | Delay: `1000ms`, Multiplier: `2.0` |
| `products` | `productTopic` | `SUFFIX_WITH_INDEX_VALUE` | `-retry-product` | `-dlt-product` | Delay: `1000ms`, Multiplier: `2.0` |

> [!IMPORTANT]
> **Correct Topic Suffixes**:
> The exact topics generated dynamically by the framework are:
> - `orders-retry-inventory-0`, `orders-retry-inventory-1`, ..., `orders-dlt-inventory`
> - `productTopic-retry-product-0`, ..., `productTopic-dlt-product`
> *(Any mention of generic `orders-retry-0` in legacy READMEs is incorrect and flagged in `INV-Q-001`)*.
>
> **Unspecified Framework-Default Attempts**: The `@RetryableTopic` annotation does not declare `attempts = ...`, so it defaults to the Spring Kafka framework default (3 attempts).

### 3.2 Dead Letter Topic (DLT) Handling
- Handled by `@DltHandler` in `KafkaListenerConfig#dlt`.
- Behavior: Logs error message and topic name (`log.error("Received dead-letter message : {} from topic {}", ...)`).
- Does **not** perform automatic recovery, database mutation, or alerting; dead-letter triage requires manual runbook intervention.

### 3.3 Optimistic Locking In-Memory Retries
- Methods modifying stock entities (`InventoryOrderManageServiceImpl#confirm` and `reserve`) are annotated with:
  ```java
  @Retryable(
      includes = OptimisticLockingFailureException.class,
      maxRetries = 3,
      multiplier = 2.0,
      delay = 1000)
  ```
- Automatically retries up to 3 times with exponential backoff on concurrent database updates before failing.

---

## 4. Transactional Outbox and Delivery Guarantees

- **Outbox Mode**: Configured in `application.yml`:
  ```yaml
  spring:
    modulith:
      events:
        externalization:
          mode: outbox
        completion-mode: archive
  namastack:
    outbox:
      retry:
        exclude-exceptions:
          - java.lang.RuntimeException
      instance:
        graceful-shutdown-timeout: 1s
  ```
- **Delivery Guarantees**:
  - Events are saved into `event_publication` in the exact same local database transaction as the inventory state mutation.
  - A background poller dispatches events to Kafka and archives them to `event_publication_archive`.
  - **At-Least-Once Delivery**: Network partitions or pod crashes during publishing can cause re-deliveries. Downstream consumers must be idempotent.
  - **No Distributed 2PC / Atomic Offset Commits**: Kafka offset commits and database transactions are independent. There is no distributed two-phase commit (2PC) or exactly-once semantic between Kafka consumer offset commits and database updates.
  - **No Restart Republication Guarantee**: The service does not guarantee automatic republishing of uncommitted outbox events immediately upon container restart without manual or job execution.
