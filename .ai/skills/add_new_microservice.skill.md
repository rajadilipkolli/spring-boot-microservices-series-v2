# Skill: Add New Spring Boot Microservice

This skill describes how to scaffold and integrate a new microservice using the established patterns in this repository.

## Steps

### 1. Project Scaffolding
Create a new directory for the service (e.g., `new-service`).
Copy the following from an existing service (like `catalog-service`):
- `mvnw`, `mvnw.cmd`, `.mvn/`
- `pom.xml` (Update `artifactId`, `name`, `description`, `mainClass`)
- `src/main/resources/application.yml` and `bootstrap.yml`

### 2. Configuration
Update `src/main/resources/bootstrap.yml` with the correct `spring.application.name`.
Ensure the service is configured to use:
- **Config Server**: `spring.config.import: optional:configserver:http://config-server:8888`
- **Eureka**: `eureka.client.serviceUrl.defaultZone: http://service-registry:8761/eureka/`
- **Observability**: Prometheus endpoint enabled.

### 3. Database & Liquibase
- Add PostgreSQL and Liquibase dependencies.
- Create `src/main/resources/db/changelog/db.changelog-master.xml` (or YAML/JSON).
- Add the service to `deployment/docker-compose.yml`.

### 4. Integration
Include the new service in the root `pom.xml` modules list (if applicable).
Update the API Gateway for routing:
```yaml
# api-gateway application.yml
- id: new-service
  uri: lb://new-service
  predicates:
    - Path=/new-service/**
```

### 5. Verification
1. Build the service: `.\mvnw.cmd clean install`
2. Run with Docker: `docker-compose up -d new-service`
3. Check Eureka Dashboard: `http://localhost:8761`
