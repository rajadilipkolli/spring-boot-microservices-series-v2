---
name: Add New Spring Boot Microservice
description: Scaffold and Integrate a New Microservice using established patterns
---

# Skill: Add New Spring Boot Microservice

This skill describes how to scaffold and integrate a new microservice using the established patterns in this repository.

## Standards
- **Framework**: Spring Boot 4.0.5, Spring Cloud 2025.
- **Service Registry**: Eureka.
- **Configuration**: Spring Cloud Config Server (Bootstrap enabled).
- **Database**: PostgreSQL with Liquibase.

## Steps

### 1. Project Scaffolding
Create a new directory for the service (e.g., `new-service`).
Scaffold the project using the standard directory layout:
- `com.example.{service}.config`
- `com.example.{service}.web`
- `com.example.{service}.services`
- `com.example.{service}.repositories`
- `com.example.{service}.entities`

Copy the following from an existing service (like `catalog-service`):
- `mvnw`, `mvnw.cmd`, `.mvn/`
- `pom.xml` (Update `artifactId`, `name`, `description`, `mainClass`)
- `src/main/resources/application.yml` and `bootstrap.yml`

Key starters for `pom.xml`:
- `spring-boot-starter-webflux` or `webmvc`
- `spring-boot-starter-actuator`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-cloud-starter-config`
- `micrometer-registry-prometheus`

### 2. Configuration
Update `src/main/resources/bootstrap.yml` with the correct `spring.application.name`.
Ensure the service is configured to use:
- **Config Server**: `spring.config.import: optional:configserver:http://config-server:8888`
- **Eureka**: `eureka.client.serviceUrl.defaultZone: http://service-registry:8761/eureka/`
- **Observability**: Prometheus endpoint enabled.

### 3. Database & Liquibase
- Add PostgreSQL and Liquibase dependencies.
- Create `src/main/resources/db/changelog/db.changelog-master.xml` (or YAML/JSON).

### 4. Integration & Deployment
Include the new service in the root `pom.xml` modules list (if applicable).
Update the API Gateway for routing in its `application.yml`:
```yaml
- id: new-service
  uri: lb://new-service
  predicates:
    - Path=/new-service/**
```
Add the service to `deployment/docker-compose.yml`. Use the standard image naming convention: `dockertmt/mmv2-{service}:latest`.

### 5. Verification
1. Build the service: `.\mvnw.cmd clean install`
2. Run with Docker: `docker-compose up -d new-service`
3. Check Eureka Dashboard: `http://localhost:8761`
4. Verify health via Gateway: `curl http://localhost:8765/{service}/actuator/health`
