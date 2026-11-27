# payment-service

Payment microservice for managing customer balances, processing payment reservations, handling confirmations and rollbacks, and integrating with other services via Kafka and REST.

## 🛠️ Tech Stack & Architecture

- **Java**: 25
- **Spring Boot**: 4.1.2
- **Spring Cloud**: 2025.1.4
- **Persistence**: PostgreSQL with Liquibase migrations under schema `payment`
- **Data Access**: **jOOQ** with generated records (`CustomersRecord`, `PaymentOperationsRecord`, `LedgerEntriesRecord`, `OutboxEventsRecord`) – **No JPA `@Entity` or Hibernate**
- **Identifiers**: Hypersistence TSID (Time-Sorted Unique Identifiers)
- **Messaging**: Apache Kafka
  - **Inbound Orders Topic**: `orders` (consumer group: `payment`)
  - **Outbound Payments Topic**: `payment-orders`
  - **Retry Topics**: `orders-retry-payment-0`, `orders-retry-payment-1`, `orders-retry-payment-2`
  - **Dead Letter Topic (DLT)**: `orders-dlt-payment`
- **Design Patterns**: Transactional Outbox pattern (`OutboxPublisher`), active reconciliation (`PaymentReconciler` + ShedLock), and three-phase durable operations engine
- **Service Port**: `18085` (Context Path: `/payment-service`)
- **Main Class**: `com.example.paymentservice.PaymentApplication`
- **Package**: `com.example.paymentservice`

---

## 🚀 REST API Endpoints

All endpoints are hosted under context path `/payment-service`:

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/payment-service/api/customers` | Get paginated active customers (`pageNo`, `pageSize`, `sortBy`, `sortDir`) |
| `GET` | `/payment-service/api/customers/{id}` | Get active customer by ID |
| `GET` | `/payment-service/api/customers/name/{name}` | Get customer by name |
| `GET` | `/payment-service/api/customers/by-email` | Get customer by normalized email (`?email=...`) |
| `POST` | `/payment-service/api/customers` | Create new customer (409 Conflict if email exists) |
| `PUT` | `/payment-service/api/customers/{id}` | Update customer details (optimistic locking via `version`) |
| `DELETE` | `/payment-service/api/customers/{id}` | Soft delete customer (sets `deleted = true`) |
| `GET` | `/payment-service/api/customers/{id}/orders` | Get customer orders via Order Service proxy with CircuitBreaker |

---

## 🧪 Testing & Development

### Run tests
```shell
./mvnw clean verify
```

### Run locally with Docker Compose

```shell
docker compose up kafka postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Using Testcontainers at Development Time
You can run `TestPaymentApplication.java` directly from your IDE.
You can also run the application using Maven:

```shell
./mvnw spotless:apply spring-boot:test-run
```

### Running only this Service Locally - Tips

To run only the Payment Service locally with clean logs, follow these steps:

1. Start Kafka and PostgreSQL:
```shell
docker compose up kafka postgres
```

2. In IntelliJ IDEA, open and modify the Run Configuration for main class:
   `com.example.paymentservice.PaymentApplication`
   Set the environment variable:
```text
SPRING_PROFILES_ACTIVE=local
```

---

## 🔗 Useful Links
* Swagger UI: http://localhost:18085/payment-service/swagger-ui.html
* OpenAPI Docs: http://localhost:18085/payment-service/v3/api-docs
* Actuator Endpoint: http://localhost:18085/payment-service/actuator
