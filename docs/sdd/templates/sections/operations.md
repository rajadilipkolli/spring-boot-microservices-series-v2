# Operations and Deployment Specification

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`

This document details operational configurations, deployment topologies, Kubernetes configurations, and observability hooks for `<service-name>`.

---

## 1. Profiles and Runtime Configuration *(Mandatory)*

- **Spring Profiles**:
  - `local`: Developer setup with Docker Compose.
  - `test`: Automated integration testing environment with Testcontainers.
  - `prod`: Production Kubernetes deployment.
- **Config Server Integration**:
  - Optional import URI: `optional:configserver:http://${CONFIG_SERVER_USR}:${CONFIG_SERVER_PWD}@${CONFIG_SERVER_HOST:localhost}:${CONFIG_SERVER_PORT:8888}/`
  - Centralized properties loaded from `config-server/src/main/resources/config-repository/`.

---

## 2. Kubernetes Deployment Manifests *(Mandatory)*

- **Base Manifest**: `deployment/k8s/base/<service-name>.yaml`
- **Replicas**: 1 (base deployment).
- **Service Account**: `<service-name>`
- **Security Context**:
  - `runAsNonRoot: true`
  - `runAsUser: 1000`
  - `readOnlyRootFilesystem: true`
  - `drop: [ALL]` capabilities
- **Init Container**: `wait-for-dependencies` polling PostgreSQL, Kafka, and Config Server before starting.

### Resource Limits
```yaml
resources:
  requests:
    cpu: 200m
    memory: 512Mi
  limits:
    cpu: 1000m
    memory: 1400Mi
```

### Probes
- **Startup Probe**: `/actuator/health/liveness` (failureThreshold: 30, periodSeconds: 10).
- **Liveness Probe**: `/actuator/health/liveness` (initialDelaySeconds: 5, periodSeconds: 10).
- **Readiness Probe**: `/actuator/health/readiness` (initialDelaySeconds: 5, periodSeconds: 10).

---

## 3. Observability and Monitoring *(Mandatory)*

- **Metrics**: Exported to OpenTelemetry collector / Prometheus every 10 seconds.
- **Tracing**: 100% trace sampling (`management.tracing.sampling.probability=1.0`).
- **Health Indicators**: Probes enabled; CircuitBreaker and RateLimiter health indicators exposed.

---

## 4. Runbook Cross-References *(Mandatory)*

- [Kafka Lag Troubleshooting](../../../../docs/runbooks/kafka-lag.md)
- [Database Failure Runbook](../../../../docs/runbooks/database-failure.md)
- [Service Down Recovery](../../../../docs/runbooks/service-down.md)
- [SLO Definitions](../../../../docs/runbooks/SLO-Definitions.md)
