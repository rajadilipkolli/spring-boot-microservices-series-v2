# Service Registry (`service-registry`)

The **Service Registry** provides dynamic service discovery and registration for the microservices platform using **Spring Cloud Netflix Eureka Server**. It enables decentralized client-side load balancing, service instance health tracking, and zero-hardcoded-URL routing through the API Gateway.

---

## 🛠️ Tech Stack & Architecture

- **Java**: Java 25
- **Spring Boot**: 4.1.2
- **Spring Cloud**: 2025.1.4 (`spring-cloud-starter-netflix-eureka-server`)
- **Application Name**: `naming-server`
- **Concurrency**: Virtual Threads (`spring.threads.virtual.enabled=true`)
- **Performance Caching**: Caffeine Cache (`com.github.ben-manes.caffeine:caffeine`) for high-throughput registry access
- **Configuration Management**: Centralized configuration via Spring Cloud Config Server (`optional:configserver:http://${CONFIG_SERVER_USR:dev-usr}:${CONFIG_SERVER_PWD:dev-pass}@${CONFIG_SERVER_HOST:localhost}:${CONFIG_SERVER_PORT:8888}/`)
- **Packaging**: Standard JVM container image (GraalVM native compilation is deferred due to Eureka's reliance on `RefreshScope`; see [NATIVE_SUPPORT_DEFERRED.md](NATIVE_SUPPORT_DEFERRED.md))

---

## ⚙️ Service Registry Configuration

The Eureka Server runs in standalone mode:

- **Server Port**: `8761`
- **Self-Registration**: Disabled (`eureka.client.registerWithEureka=false`)
- **Registry Fetch**: Disabled (`eureka.client.fetchRegistry=false`)
- **Default Zone**: `http://localhost:8761/eureka/`
- **Peer Node Read Timeout**: `300ms`
- **IP Address Preference**: Enabled (`eureka.instance.preferIpAddress=true`)

---

## 🚀 Running the Service

### Prerequisites & Dependencies via Docker Compose

Start supporting infrastructure (Config Server):

```shell
# From the service-registry directory
docker compose -f docker-compose.yml up -d
```

### Run Locally (Spring Boot)

```shell
./mvnw spring-boot:run
```
*(On Windows Command Prompt or PowerShell, use `mvnw.cmd`)*

### Run Tests & Verification

```shell
./mvnw clean verify
```

---

## 🌐 Useful Links & Endpoints

- **Eureka Web Dashboard**: `http://localhost:8761/`
- **Eureka Applications API (XML/JSON)**: `http://localhost:8761/eureka/apps`
- **Actuator Health**: `http://localhost:8761/actuator/health`
- **Actuator Info**: `http://localhost:8761/actuator/info`

---

## 📋 Microservices Registration Summary

| Service Name | Port | Health Check URL | Description |
| :--- | :--- | :--- | :--- |
| `API-GATEWAY` | 8765 | `/actuator/health` | Edge routing & resilience gateway |
| `CATALOG-SERVICE` | 18080 | `/catalog-service/actuator/health` | Product catalog & reactive Redis caching |
| `INVENTORY-SERVICE` | 18181 | `/inventory-service/actuator/health` | Stock management & Kafka order saga |
| `ORDER-SERVICE` | 18282 | `/order-service/actuator/health` | Order orchestration & Kafka Streams saga |
| `PAYMENT-SERVICE` | 18085 | `/payment-service/actuator/health` | Customer balance & payment processing |
| `RETAIL-STORE-WEBAPP` | 8080 | `/actuator/health` | Customer-facing storefront web UI |
