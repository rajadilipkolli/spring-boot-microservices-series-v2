# API Gateway (`api-gateway`)

**Spring Cloud Gateway** serves as the intelligent single entry point for all incoming external and client requests in the microservices platform. Built on the reactive Spring WebFlux stack, it provides dynamic routing, client-side load balancing, distributed rate limiting, circuit breaker protection with fallbacks, automated retry logic, and end-to-end request tracing.

---

## 🛠️ Tech Stack & Key Technologies

- **Java**: Java 25
- **Spring Boot**: 4.1.2
- **Spring Cloud**: 2025.1.4
- **Gateway Framework**: Spring Cloud Gateway (Reactive / WebFlux)
- **Service Discovery**: Spring Cloud Netflix Eureka Client (`lb://` protocol routing)
- **Load Balancer**: Spring Cloud LoadBalancer
- **Distributed Rate Limiting**: Redis Reactive (`RequestRateLimiter` filter backed by Redis)
- **Circuit Breaking & Resilience**: Resilience4j CircuitBreaker with forward fallback
- **Retry Mechanism**: Spring Cloud Gateway Retry filter with exponential backoff
- **Observability & Tracing**: Micrometer Tracing, OpenTelemetry, Prometheus metrics
- **Documentation**: SpringDoc OpenAPI (v3.2.0) aggregating Swagger endpoints across microservices

---

## 🛣️ Route & Resilience Configuration

| Route ID | Path Predicate | Target URI | Resilience & Filters |
| :--- | :--- | :--- | :--- |
| `catalog-service` | `/catalog-service/**` | `lb://catalog-service` | **Retry**: 3 retries on GET methods with exponential backoff (50ms to 500ms, factor 2) |
| `inventory-service` | `/inventory-service/**` | `lb://inventory-service` | **Circuit Breaker**: `exampleSlowCircuitBreaker` with fallback forwarding to `/fallback/api/inventory` |
| `order-service` | `/order-service/**` | `lb://order-service` | **RequestRateLimiter**: Redis-based rate limiting (replenishRate: 60/min, burstCapacity: 10) |
| `payment-service` | `/payment-service/**` | `lb://payment-service` | Dynamic load balancing via Eureka (`lb://`) |

---

## 🚀 Running the Service

### Prerequisites & Dependencies via Docker Compose

Start the supporting infrastructure (Redis, Config Server, Eureka Service Registry, Grafana LGTM):

```shell
# From the api-gateway directory
docker compose up -d
```

### Run Locally (Spring Boot)

```shell
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
*(On Windows Command Prompt or PowerShell, use `mvnw.cmd`)*

### Using Testcontainers at Development Time

Run `TestAPIGatewayApplication.java` directly from your IDE, or launch it with Maven:

```shell
./mvnw spotless:apply spring-boot:test-run
```

### Run Tests & Verification

```shell
./mvnw clean verify
```

---

## 🌐 Useful URLs & Endpoints

- **Gateway Base URL**: `http://localhost:8765`
- **Swagger UI (Aggregated APIs)**: `http://localhost:8765/swagger-ui.html`
- **OpenAPI v3 Docs**: `http://localhost:8765/v3/api-docs`
- **Actuator Health**: `http://localhost:8765/actuator/health`
- **Actuator Info**: `http://localhost:8765/actuator/info`
- **Gateway Routes**: `http://localhost:8765/actuator/gateway/routes`
- **Gateway Global Filters**: `http://localhost:8765/actuator/gateway/globalfilters`
- **Gateway Route Filters**: `http://localhost:8765/actuator/gateway/routefilters`
- **Route Detail (`catalog-service`)**: `http://localhost:8765/actuator/gateway/routes/catalog-service`
- **Orchestrated Data Generation Endpoint**: `POST http://localhost:8765/api/v1/generate` (Requires `Idempotency-Key` header)

### Calling Microservices Through Gateway

- **Catalog Service**: `http://localhost:8765/catalog-service/api/catalog`
- **Inventory Service**: `http://localhost:8765/inventory-service/api/inventory`
- **Order Service**: `http://localhost:8765/order-service/api/orders`
- **Payment Service**: `http://localhost:8765/payment-service/api/payments`
