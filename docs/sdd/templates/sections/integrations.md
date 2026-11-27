# Integrations and Messaging Specification

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`
- **Messaging System**: Apache Kafka
- **Event Outbox**: Spring Modulith Events Externalization

---

## 1. Kafka Topics and Consumer Groups *(Mandatory)*

| Topic Name | Direction | Consumer Group / Producer | Key Serialization | Value Serialization | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `input-topic` | Inbound (Consume) | `service-group` | String | String / JSON | Inbound commands or events. |
| `output-topic` | Outbound (Produce) | Modulith Outbox | String | Byte Array / JSON | Outbound domain results. |

---

## 2. Event Schemas *(Mandatory)*

### 2.1 Event: `[EventName]`
- **Topic**: `[topic-name]`
- **Java Class / Record**: `<repo-relative-path>#<EventRecord>`
- **Partition Key**: `[key field]`
- **Header Structure**: Flat JSON without Java type headers (`add.type.headers=false`).

#### Payload Schema
```json
{
  "orderId": 1001,
  "status": "ACCEPT",
  "source": "SERVICE_NAME",
  "attemptId": "attempt-uuid"
}
```

---

## 3. Resilience, Retries, and Dead Letter Topics (DLT) *(Mandatory)*
- **Retry Mechanism**: Spring Kafka `@RetryableTopic`
- **Backoff Configuration**: Initial delay `1000ms`, multiplier `2.0`.
- **Topic Naming Convention**: Suffixing strategy with index value (e.g., `-retry-<suffix>`, `-dlt-<suffix>`).
- **DLT Handling**: Logged via `@DltHandler` for diagnosis; manual reprocessing required.
- **Optimistic Lock Retries**: In-memory retry via `@Retryable(includes = OptimisticLockingFailureException.class, maxRetries = 3)`.

---

## 4. Transactional Outbox & Externalization *(Mandatory)*
- **Spring Modulith Outbox**: `spring.modulith.events.externalization.mode=outbox`
- **Completion Mode**: `archive` (events moved to archive table upon completion).
- **Outbox Persistence Tables**: `event_publication` and `event_publication_archive`.
- **Acknowledgment Mode & Guarantees**: Dual-write guarantees between database and Kafka broker. At-least-once delivery; consumer idempotency required.
