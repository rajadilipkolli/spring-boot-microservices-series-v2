# order-service

The **Order Service** manages order lifecycles in the microservices architecture, implementing the Saga pattern for distributed transactions using Kafka Streams, Spring Modulith outbox event externalization, and JobRunr for background job execution.

## 🛠️ Technology Stack
- **Java**: 25 (with Virtual Threads enabled)
- **Spring Boot**: 4.1.2
- **Spring Cloud**: 2025.1.4
- **Spring Modulith**: 2.1.2 (Kafka outbox event externalization)
- **Messaging & Streaming**: Apache Kafka 4.x / Kafka Streams (JoinWindows 30s + 30s grace, EXACTLY_ONCE_V2)
- **Background Processing**: JobRunr 9.0.2
- **Persistence**: PostgreSQL 18 managed with Liquibase, Hypersistence TSID
- **Resilience**: Resilience4j 2.4.0 (CircuitBreaker, RateLimiter, Bulkhead)
- **Observability**: OpenTelemetry, Micrometer, Prometheus, Grafana

### Run tests

```shell
./mvnw clean verify
```

### Run locally

```shell
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
### Using Testcontainers at Development Time
You can run `TestOrderServiceApplication.java` from your IDE directly.
You can also run the application using Maven as follows:

```shell
./mvnw spotless:apply spring-boot:test-run
```


### Useful Links
* Swagger UI: http://localhost:18282/order-service/swagger-ui.html
* OpenAPI Docs: http://localhost:18282/order-service/v3/api-docs
* Actuator Endpoint: http://localhost:18282/order-service/actuator
* JobRunr Dashboard: http://localhost:28282/dashboard/overview

## Running only this Service Locally - Tips

To run only the Order Service locally with clean logs, you can follow these steps:

* Start the Kafka and Postgres servers:
```shell
docker compose -f docker/docker-compose.yml up -d kafka postgresql
```

* In IntelliJ IDEA, open Run Configuration for main class:
  `com.example.orderservice.OrderServiceApplication`
Set the Environment variable value to:
```text
SPRING_PROFILES_ACTIVE=local
```

* In case the local profile cannot connect to PostgreSQL:
Modify the Environment Variable Value to bring up the application using an embedded H2 database:
```text
SPRING_PROFILES_ACTIVE=h2
```

### Notes
* KafkaStream DeadLetter recovery is configured in `KafkaStreamsConfig.java` routing deserialization errors to `recovererDLQ`.
* Order event publication uses Spring Modulith outbox event publishing (`eventPublisher.publishEvent(...)`).
