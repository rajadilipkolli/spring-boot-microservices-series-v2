# Catalog Service (`catalog-service`)

The **Catalog Service** manages product information, inventory-enriched product details, search, and catalog persistence in a non-blocking, reactive architecture. It asynchronously publishes lifecycle events to Apache Kafka and caches product listings and details in Redis.

---

## 🛠️ Tech Stack & Architecture

- **Java**: Java 25
- **Spring Boot**: 4.1.2
- **Spring Cloud**: 2025.1.4
- **Reactive Framework**: Spring WebFlux (Reactor Netty)
- **Reactive Persistence**: Spring Data R2DBC with PostgreSQL driver (`r2dbc-postgresql`)
- **Distributed Caching**: Redis Reactive (`ReactiveRedisOperations<String, Object>`), cache generation invalidation, and ShedLock (`RedisLockProvider`)
- **Messaging & Event Streaming**: Apache Kafka via Spring Cloud Stream (`StreamBridge`, topic: `productTopic`)
- **Resilience**: Resilience4j Reactive (CircuitBreaker, RateLimiter, Retry, TimeLimiter) protecting calls to Inventory Service
- **Service Discovery**: Spring Cloud Netflix Eureka Client (WebClient-backed)
- **Database Migrations**: Liquibase (YAML format)
- **ID Generation**: TSID (`hypersistence-tsid`)
- **Observability**: OpenTelemetry, Micrometer Tracing, Prometheus metrics

---

## 🚀 Running the Service

### Prerequisites & Dependencies via Docker Compose

Start the required infrastructure services (PostgreSQL, Redis, Kafka, Grafana LGTM):

```shell
# From the catalog-service directory
docker compose -f docker/docker-compose.yml up -d
```

To start only the core dependencies (PostgreSQL, Kafka, Redis) without monitoring:

```shell
docker compose -f docker/docker-compose.yml up postgresql kafka redis -d
```

### Run Locally (Spring Boot)

```shell
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
*(On Windows Command Prompt or PowerShell, use `mvnw.cmd`)*

### Using Testcontainers at Development Time

You can run `TestCatalogServiceApplication.java` directly from your IDE, or launch it with Maven:

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
   docker compose -f docker/docker-compose.yml up postgresql kafka redis -d
   ```
2. In IntelliJ IDEA or your IDE, set the environment variable for `CatalogServiceApplication`:
   ```text
   SPRING_PROFILES_ACTIVE=local
   ```
3. Run or debug `com.example.catalogservice.CatalogServiceApplication`.

---

## 🌐 Useful Links & Endpoints

- **Service Port**: `18080` (Base path: `/catalog-service`)
- **Swagger UI**: `http://localhost:18080/catalog-service/swagger-ui.html`
- **OpenAPI v3 Docs**: `http://localhost:18080/catalog-service/v3/api-docs`
- **Actuator Endpoint**: `http://localhost:18080/catalog-service/actuator`
- **Actuator Health**: `http://localhost:18080/catalog-service/actuator/health`

### REST API Endpoints

| HTTP Method | Endpoint | Description | Features |
| :--- | :--- | :--- | :--- |
| `GET` | `/catalog-service/api/catalog` | Get paginated products | Reactive pagination, Redis caching, stock enrichment |
| `GET` | `/catalog-service/api/catalog/id/{id}` | Get product by ID | 5-minute Redis cache, stock lookup |
| `GET` | `/catalog-service/api/catalog/product-code/{code}` | Get product by product code | Case-insensitive lookup, optional stock info |
| `GET` | `/catalog-service/api/catalog/exists?productCodes=` | Check product existence | Bulk product validation |
| `GET` | `/catalog-service/api/catalog/search` | Advanced search | Multi-criteria filtering (query, price range) |
| `POST` | `/catalog-service/api/catalog` | Create product | TSID assigned, Kafka event published, cache invalidated |
| `PUT` | `/catalog-service/api/catalog/{id}` | Update product | Reactive update, cache eviction |
| `DELETE` | `/catalog-service/api/catalog/{id}` | Soft delete product | Sets `deleted=true`, cache eviction |
| `POST` | `/catalog-service/api/catalog/generate` | Generate test products | Development bulk test utility (requires `Idempotency-Key`) |
