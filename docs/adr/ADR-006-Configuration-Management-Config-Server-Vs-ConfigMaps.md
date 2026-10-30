# ADR-006: Configuration Management: Spring Cloud Config Server vs. Kubernetes ConfigMaps

## Status
Accepted

## Context
Microservices require centralized, environment-specific externalized configuration. Two competing patterns exist:
1. **Spring Cloud Config Server**: Centralized configuration service backed by a Git repository or local filesystem profile, exposing configuration over HTTP with optional encryption and dynamic property refresh via Spring Cloud Bus / Actuator `/refresh`.
2. **Kubernetes Native ConfigMaps & Secrets**: Declarative key-value stores mounted as environment variables or filesystem volumes directly into pod containers by the kubelet.

Running Spring Cloud Config Server creates a hard bootstrap dependency: all microservices fail to start if Config Server is unavailable or experiencing latency during container initialization.

## Decision
We implement a **Complementary Hybrid Configuration Strategy**:

1. **Bootstrap & Infrastructure Properties via Kubernetes ConfigMaps**:
   - Fundamental cluster addresses (`CONFIG_SERVER_HOST`, `CONFIG_SERVER_PORT`, `SPRING_CONFIG_IMPORT`, `SPRING_PROFILES_ACTIVE`, database host, Kafka bootstrap servers) are injected directly via `ConfigMap` (`shared-config`) and Kubernetes environment variables.
   - Microservices can locate core infrastructure without prior network round-trips.

2. **Application Domain & Feature Configuration via Config Server**:
   - Fine-grained business logic toggles, retry thresholds, circuit breaker parameters, and route mappings remain managed centrally in `config-server`.
   - In production, Config Server runs with authenticated Git backend or native Kubernetes volume mounts.

3. **Fallback & Graceful Startup Behavior**:
   - `spring.cloud.config.fail-fast: false` is configured on non-critical microservices, or appropriate retry loops (`spring.cloud.config.retry.*`) are applied with exponential backoff so that temporary network jitter during pod startup does not cause CrashLoopBackOff.
   - For environments adopting full GitOps, properties are progressively mirrored to Kubernetes `ConfigMap` objects generated via Kustomize `configMapGenerator`.

4. **Secrets Separation**:
   - No sensitive credentials, tokens, or private keys are stored in Spring Cloud Config git repositories. All secrets are managed via External Secrets Operator and Kubernetes Secrets (see ADR-007).

## Consequences

### Positive
- **Centralized Versioning**: Git-backed Config Server provides commit audit trails for property modifications.
- **Dynamic Configuration Refresh**: Services can consume configuration updates at runtime via `/actuator/refresh` without pod restarts.
- **Cluster Portability**: Injecting bootstrap URLs via `ConfigMapRef` decouples container images from specific namespace DNS topologies.

### Negative / Trade-offs
- Config Server availability: Config Server remains a critical platform service that must start first and scale independently.
- Two configuration planes: Developers must be aware of whether a given property resides in `shared-config` (ConfigMap) or application-specific properties in `config-server`.

## Verification & Compliance
- Health checks for `config-server` must pass before dependant service pods begin initialization (`depends_on` in compose; readiness probes in Kubernetes).
- Configuration repositories must be scanned by Gitleaks / TruffleHog to ensure zero secrets are committed.
