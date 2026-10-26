# Inventory Service (`inventory-service`)

The **Inventory Service** manages product stock levels, inventory reservations for incoming orders, and event-driven stock synchronization across the platform. It uses a hybrid persistence layer combining **Spring Data JPA** and **jOOQ** for optimal developer ergonomics and type-safe, high-performance query execution.

---

## 🛠️ Tech Stack & Architecture

- **Java**: Java 25
- **Spring Boot**: 4.1.2
- **Spring Cloud**: 2025.1.4
- **Web Framework**: Spring Boot WebMVC (Servlet stack on embedded Tomcat)
- **Concurrency**: Virtual Threads (`spring.threads.virtual.enabled=true`)
- **Persistence**:
  - **Spring Data JPA**: Domain entity mapping, persistence lifecycle, and optimistic locking (`@Version`)
  - **jOOQ**: Type-safe SQL query building, code generation, and high-performance batch updates
  - **Database**: PostgreSQL 18
  - **Schema Migrations**: Liquibase (JSON format changelogs)
- **Messaging & Event-Driven Architecture**:
  - **Apache Kafka**: Consumers for `orders` and `productTopic` topics; producer for `stock-orders` topic
  - **Spring Modulith**: Transactional outbox event externalization (`spring.modulith.events.externalization.mode=outbox`) with automatic event archiving
- **Resilience**: Two-phase stock validation and reservation pattern, Kafka retry topics (`orders-retry-0,1,2`), and Dead Letter Topic (`orders-dlt`)
- **Service Discovery**: Spring Cloud Netflix Eureka Client
- **Observability**: OpenTelemetry, Micrometer Tracing, Prometheus metrics

---

## 🚀 Running the Service

### Prerequisites & Dependencies via Docker Compose

Start the required infrastructure services (PostgreSQL, Kafka, Redis, PgAdmin, Grafana LGTM):

```shell
# From the inventory-service directory
docker compose -f docker/docker-compose.yml up -d
```

To run only the core dependencies (PostgreSQL, Kafka, Grafana LGTM) for local development:

```shell
docker compose -f docker/docker-compose.yml up postgresql kafka grafana-lgtm -d
```

### Run Locally (Spring Boot)

```shell
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
*(On Windows Command Prompt or PowerShell, use `mvnw.cmd`)*

### Using Testcontainers at Development Time

Run `TestInventoryApplication.java` directly from your IDE, or launch it with Maven:

```shell
./mvnw spotless:apply spring-boot:test-run
```

### Run Tests & Verification

```shell
./mvnw clean verify
```

---

## 💡 Running Only This Service Locally (Tips)

1. Start dependencies with Docker Compose:
   ```shell
   docker compose -f docker/docker-compose.yml up postgresql kafka grafana-lgtm -d
   ```
2. In IntelliJ IDEA or your IDE, set the environment variable for `InventoryServiceApplication`:
   ```text
   SPRING_PROFILES_ACTIVE=local
   ```
3. Run or debug `com.example.inventoryservice.InventoryServiceApplication`.

---

## 🌐 Useful Links & Endpoints

- **Service Port**: `18181` (Context path: `/inventory-service`)
- **Swagger UI**: `http://localhost:18181/inventory-service/swagger-ui.html`
- **OpenAPI v3 Docs**: `http://localhost:18181/inventory-service/v3/api-docs`
- **Actuator Endpoint**: `http://localhost:18181/inventory-service/actuator`
- **Actuator Health**: `http://localhost:18181/inventory-service/actuator/health`

### REST API Endpoints

| HTTP Method | Endpoint | Description | Features |
| :--- | :--- | :--- | :--- |
| `GET` | `/inventory-service/api/inventory` | Get paginated inventory items | jOOQ pagination, sorting by ID or product code |
| `GET` | `/inventory-service/api/inventory/{productCode}` | Get inventory by product code | Direct stock lookup |
| `GET` | `/inventory-service/api/inventory/product?codes=` | Bulk inventory query | Batch stock lookup for multiple product codes |
| `POST` | `/inventory-service/api/inventory` | Create new inventory entry | Validation, entity mapping, and persistence |
| `PUT` | `/inventory-service/api/inventory/{id}` | Update inventory | Transactional update with optimistic locking |
| `DELETE` | `/inventory-service/api/inventory/{id}` | Soft delete inventory | Sets `deleted=true` |
| `POST` | `/inventory-service/api/inventory/generate` | Generate test inventory items | Development bulk inventory creation |
