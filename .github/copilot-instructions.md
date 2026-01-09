## Repository Overview

This repository implements a Spring Boot microservices reference architecture (API Gateway, Config Server, Eureka service registry, multiple domain services) wired together with Docker Compose for local development. Services communicate primarily via HTTP through the API Gateway and asynchronously via Kafka. Databases are PostgreSQL, and Liquibase is used for schema management.

Keep guidance short and actionable — why an agent is here: to edit, extend, or debug services while preserving cross-service contracts and Docker-based local workflows.

### High-level architecture (what to know)
- **API Gateway**: `api-gateway/` — single entry point (Spring Cloud Gateway). Look at `api-gateway/ReadMe.md` and `api-gateway/src/main` for route/filter patterns.
- **Service Registry**: `service-registry/` (Eureka) — services register dynamically; many services expect discovery at `localhost:8761`.
- **Config Server**: `config-server/` — centralized configuration; services load properties from it at startup (default port `8888`).
- **Domain services**: `catalog-service/`, `inventory-service/`, `order-service/`, `payment-service/` — each is a standalone Spring Boot app using PostgreSQL and Liquibase changelogs located under `src/main/resources/db/changelog` (check each service for format: YAML/JSON/XML).
- **UI**: `retail-store-webapp/` — Thymeleaf frontend that calls the gateway.
- **Messaging**: Kafka is used for event-driven communication; check `deployment/docker-compose*.yml` for topics and broker settings.

### Important files and places to look
- Root README: `ReadMe.md` — architecture and quick-start commands (Docker Compose scripts).
- Docker compose orchestration: `deployment/docker-compose.yml` and `deployment/docker-compose-tools.yml`.
- Service run helpers: `run.sh` (Unix) and `start-services.ps1` (Windows). Use these for a developer-run multi-service environment.
- Maven wrapper: use `./mvnw` (Unix) or `.
mvnw.cmd` (Windows) to keep builds reproducible.
- Service README examples: `api-gateway/ReadMe.md`, `catalog-service/README.md` — follow their `spring-boot:run -Dspring-boot.run.profiles=local` patterns when running a single service.

### Local developer workflows (commands agents may emit or run)
- Build all modules (Windows): `.
mvnw.cmd clean install`
- Build and create images (used by `run.sh`): `./mvnw spotless:apply spring-boot:build-image -DskipTests`
- Start full system (recommended):
  - Linux/macOS: `bash run.sh` or `docker compose -f deployment/docker-compose.yml up -d --remove-orphans`
  - Windows: `.
start-services.ps1` or `docker compose -f deployment/docker-compose.yml up -d`
- Start infra only (Kafka, Postgres, Grafana, Redis): `docker compose -f deployment/docker-compose.yml up -d grafana-lgtm postgresql kafka redis`
- Stop everything: `docker compose -f deployment/docker-compose.yml down`

When running a single service locally, prefer the service's README instructions and start dependent infra (Kafka/Postgres) using the docker compose targets shown above.

### Codebase conventions and patterns (repo-specific)
- Project layout: each service is a Maven module with its own `pom.xml` under the service directory. Root `pom.xml` aggregates modules.
- Profiles: a `local` Spring profile is commonly used (`-Dspring-boot.run.profiles=local`). Use it for running services against Dockerized infra.
- Liquibase: changelogs appear in different formats per service (YAML for `catalog-service`, JSON for `inventory-service`, XML for others). Modify the relevant changelog directory when adjusting schemas.
- Testcontainers: many integration tests use Testcontainers; running `spring-boot:test-run` in Maven is used by some services for a test-driven development loop (see service READMEs).
- Logging and observability: actuator endpoints are exposed under `/actuator` and Swagger UIs are mounted by many services — useful endpoints often follow the pattern `http://localhost:<service-port>/<service-name>/actuator` and `.../swagger-ui.html`.

### Cross-service contracts and where to check
- API contracts: Open the Swagger UI for aggregated API docs at `http://localhost:8765/swagger-ui.html` once the gateway is running.
- Event contracts: look for topic names and message DTOs in each service's `src/main/java` (search for `@KafkaListener`, `@KafkaTemplate`, or `topic` constants). Also check `deployment` compose files for topics/broker config.

### Editing guidance for agents
- When changing a service API, update its controller and add/adjust DTOs in the same module. Then:
  1. Run unit tests for that module: `./mvnw -pl <module> test` (or `.
mvnw.cmd -pl <module> test` on Windows).
  2. If it affects DB schema, update Liquibase changelogs under `src/main/resources/db/changelog` for that service.
  3. If it changes event payloads, update consumers/producers in other services and add a migration plan (backwards-compatible changes preferred).
  4. Always prefer AssertJ for assertions in tests.
  5. Always use imports rather than fully qualified class names in code.

### CI / Build notes
- Builds use Maven and the wrapper; maintainers rely on `spotless` and `checkstyle` in some modules. Avoid changing formatting rules without running `spotless:apply`.
- The `run.sh` script calls `spring-boot:build-image` during `build_api` — CI may expect images to be buildable.

### Debugging tips (quick wins)
- Check service registration at `http://localhost:8761` (Eureka) when services fail to discover.
- Use `docker compose -f deployment/docker-compose.yml logs -f <service>` to tail logs for a service.
- When database migrations fail, inspect the service's `target/` directory for generated changelogs and the Liquibase tables in Postgres (`DATABASECHANGELOG`).

### Examples (copy-paste friendly)
- Start infra (Kafka + Postgres) only:
  docker compose -f deployment/docker-compose.yml up -d kafka postgresql grafana-lgtm redis
- Start gateway + UI + backend services:
  docker compose -f deployment/docker-compose.yml up -d api-gateway retail-store-webapp catalog-service inventory-service order-service payment-service
- Run Catalog Service locally (Windows PowerShell):
  cd catalog-service; .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

### Safety and expectations
- Only make cross-service changes when you can run integration smoke tests locally or via CI. Small, focused PRs are preferred.
- Preserve backward compatibility for event payloads and REST endpoints where possible; document incompatible changes in PR descriptions.

If anything in these instructions is unclear or you want more detail (example PR checklist, common DTO locations, or typical test commands), tell me which area to expand.
