# Operations and Deployment Specification: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`

This document details runtime profiles, configuration sources, container limits, Kubernetes deployment manifests, and operational links for `inventory-service`.

---

## 1. Profiles and Centralized Configuration

### 1.1 Spring Profiles
- **`local`**: Used for developer workstations and local Docker Compose environments (`docker/docker-compose.yml`). Activates `Initializer.java` to seed 100 test items.
- **`test`**: Used during Maven test phase with Testcontainers. Disables external Eureka and Kafka infrastructure in favor of ephemeral containers.
- **`prod`**: Used in production Kubernetes deployments.

### 1.2 Config Server Integration
The service imports externalized configuration at startup:
```yaml
spring:
  config:
    import: optional:configserver:http://${CONFIG_SERVER_USR:dev-usr}:${CONFIG_SERVER_PWD:dev-pass}@${CONFIG_SERVER_HOST:localhost}:${CONFIG_SERVER_PORT:8888}/
```
- In development/local environments, credentials default to `dev-usr` / `dev-pass`.
- In Kubernetes, `CONFIG_SERVER_USR` and `CONFIG_SERVER_PWD` are injected from Kubernetes secret `config-server-credentials`.

### 1.3 Possibly Unused Resilience4j Properties
In `config-server/src/main/resources/config-repository/inventory-service.properties`, configuration properties are declared for Resilience4j:
```properties
resilience4j.retry.instances.inventory-api.maxAttempts=5
resilience4j.retry.instances.inventory-api.waitDuration=1s
resilience4j.ratelimiter.instances.default.limitForPeriod=2
resilience4j.ratelimiter.instances.default.limitRefreshPeriod=10s
resilience4j.bulkhead.instances.default.maxConcurrentCalls=10
resilience4j.circuitbreaker.configs.default.slidingWindowSize=10
```
> [!NOTE]
> As verified in `inventory-service/pom.xml`, `inventory-service` does **not** include the `io.github.resilience4j` starter dependency in its POM. These properties are currently inactive in the service runtime (flagged in `INV-Q-004`).

---

## 2. Docker & Kubernetes Resources

### 2.1 Container Resource Limits & JVM Settings
Configured in `deployment/k8s/base/inventory-service.yaml`:
```yaml
resources:
  requests:
    cpu: 200m
    memory: 512Mi
  limits:
    cpu: 1000m
    memory: 1400Mi
```
- Docker image: `dockertmt/mmv2-inventory-service:0.0.1-SNAPSHOT`
- Temporary storage: `emptyDir: {}` mounted at `/tmp`.

### 2.2 Probes and Health Checks
- **Startup Probe**:
  - `httpGet: { path: /inventory-service/actuator/health/liveness, port: 18181 }`
  - `failureThreshold: 30`, `periodSeconds: 10`
- **Liveness Probe**:
  - `httpGet: { path: /inventory-service/actuator/health/liveness, port: 18181 }`
  - `initialDelaySeconds: 5`, `periodSeconds: 10`
- **Readiness Probe**:
  - `httpGet: { path: /inventory-service/actuator/health/readiness, port: 18181 }`
  - `initialDelaySeconds: 5`, `periodSeconds: 10`

### 2.3 Init Container (`wait-for-dependencies`)
Ensures that all backend infrastructure services are accepting TCP connections or passing health checks before Tomcat starts:
```bash
until wget -q -O /dev/null http://config-server:8888/actuator/health/readiness 2>/dev/null; do echo waiting for config-server; sleep 3; done;
until nc -z service-registry 8761; do echo waiting for service-registry; sleep 2; done;
until nc -z postgresql 5432; do echo waiting for postgresql; sleep 2; done;
until nc -z kafka 9092; do echo waiting for kafka; sleep 2; done;
```

---

## 3. Observability & Telemetry

- **Metrics & Traces Exporter**:
  In `application-docker.yml` and Kubernetes `shared-config`, metrics and traces are exported via OTLP:
  - Metrics URL: `http://grafana-lgtm:4318/v1/metrics` (interval: `10s`)
  - Traces URL: `http://grafana-lgtm:4318/v1/traces`
  - Logs URL: `http://grafana-lgtm:4318/v1/logs`
- **Sampling Probability**: `1.0` (100% of traces captured).
- **Correlation ID Logging**: SLF4J MDC pattern `[%X{traceId:-},%X{spanId:-}]`.

---

## 4. Operational Playbooks & ADR Links

- [ADR-001: Kubernetes Deployment Strategy](../../../../docs/adr/ADR-001-Kubernetes-Deployment-Strategy.md)
- [ADR-002: Kafka Architecture and Clustering](../../../../docs/adr/ADR-002-Kafka-Architecture-And-Clustering.md)
- [ADR-003: PostgreSQL Persistence and CNPG](../../../../docs/adr/ADR-003-PostgreSQL-Persistence-And-CNPG.md)
- [ADR-009: Observability OpenTelemetry LGTM Stack](../../../../docs/adr/ADR-009-Observability-OpenTelemetry-LGTM-Stack.md)
- [ADR-010: Schema Governance Apicurio Registry](../../../../docs/adr/ADR-010-Schema-Governance-Apicurio-Registry.md)
- [Runbook: Kafka Lag Triage](../../../../docs/runbooks/kafka-lag.md)
- [Runbook: Dead Letter Queue (DLQ) Handling](../../../../docs/runbooks/DLQ-Handling.md)
- [Runbook: Database Failure](../../../../docs/runbooks/database-failure.md)
- [Runbook: Service Down](../../../../docs/runbooks/service-down.md)
