# Configuration Server (`config-server`)

The **Configuration Server** provides centralized, externalized runtime configuration management across all microservices in the platform. Backed by Spring Cloud Config Server and secured with Spring Security, it serves versioned, profile-specific configurations from a local classpath repository or version-controlled backend.

---

## 🛠️ Tech Stack & Architecture

- **Java**: Java 25
- **Spring Boot**: 4.1.2
- **Spring Cloud**: 2025.1.4 (`spring-cloud-config-server`)
- **Security**: Spring Security with HTTP Basic Authentication (`dev-usr` / `dev-pass` default credentials)
- **Concurrency**: Virtual Threads (`spring.threads.virtual.enabled=true`)
- **Repository Backend**: Native repository profile scanning `classpath:/config-repository`
- **Observability**: OpenTelemetry Logback appender and OTLP tracing export to OpenTelemetry Collector / Grafana LGTM

---

## 🔐 Authentication & Access Credentials

The configuration server requires HTTP Basic Authentication. Credentials can be customized via environment variables:

| Environment Variable | Description | Default Value |
| :--- | :--- | :--- |
| `CONFIG_SERVER_USR` | Basic Auth Username | `dev-usr` |
| `CONFIG_SERVER_PWD` | Basic Auth Password | `dev-pass` |

When microservices connect to Config Server, they import configuration via `spring.config.import`:
```properties
spring.config.import=optional:configserver:http://${CONFIG_SERVER_USR:dev-usr}:${CONFIG_SERVER_PWD:dev-pass}@${CONFIG_SERVER_HOST:localhost}:${CONFIG_SERVER_PORT:8888}/
```

---

## 🌐 Endpoints & Configuration Retrieval

### Accessing Service Properties

Configurations can be fetched using REST endpoints:
```text
http://localhost:8888/{application}/{profile}[/{label}]
```

Examples using `curl` with HTTP Basic Authentication:

```shell
# Catalog Service default profile
curl -u dev-usr:dev-pass http://localhost:8888/catalog-service/default

# Inventory Service default profile
curl -u dev-usr:dev-pass http://localhost:8888/inventory-service/default

# Order Service docker profile
curl -u dev-usr:dev-pass http://localhost:8888/order-service/docker

# Payment Service default profile
curl -u dev-usr:dev-pass http://localhost:8888/payment-service/default
```

### Actuator & Health Endpoints

- **Health Check**: `curl -u dev-usr:dev-pass http://localhost:8888/actuator/health`
- **Info Endpoint**: `curl -u dev-usr:dev-pass http://localhost:8888/actuator/info`
- **Environment Properties**: `curl -u dev-usr:dev-pass http://localhost:8888/actuator/env`
- **Config Server Health Component**: `curl -u dev-usr:dev-pass http://localhost:8888/actuator/health/configServer`

---

## 📁 Repository Structure (`classpath:/config-repository`)

```text
config-repository/
├── application.yml                    # Global baseline configuration for all services
├── application-docker.yml             # Global overrides for Docker Compose environments
├── application-kubernetes.yml         # Global overrides for Kubernetes deployments
├── application-native.yml             # Native build configurations
├── api-gateway.properties            # API Gateway specific routing & management settings
├── catalog-service.yml               # Catalog Service circuit breaker & retry settings
├── catalog-service-docker.properties # Catalog Service Docker environment overrides
├── inventory-service.properties      # Inventory Service resilience & datasource settings
├── naming-server-docker.yml          # Eureka Service Registry Docker configuration
├── order-service.properties          # Order Service Kafka & persistence settings
├── payment-service.properties        # Payment Service resilience & settings
└── payment-service-docker.properties # Payment Service Docker overrides
```

---

## 🚀 Running the Service

### Run Locally (Spring Boot)

```shell
./mvnw spring-boot:run
```
*(On Windows Command Prompt or PowerShell, use `mvnw.cmd`)*

### Run Tests & Verification

```shell
./mvnw clean verify
```
