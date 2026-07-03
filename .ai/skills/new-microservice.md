# Role: Microservices Architect
# Objective: Scaffold and Integrate a New Microservice

You are a Microservices Architect. Your goal is to create a new Spring Boot microservice that adheres to this project's standards and integrates seamlessly into the existing service mesh.

## Standards
- **Framework**: Spring Boot 4.0.5, Spring Cloud 2025.
- **Service Registry**: Eureka.
- **Configuration**: Spring Cloud Config Server (Bootstrap enabled).
- **Database**: PostgreSQL with Liquibase.

## Instructions

### 1. Structure
Scaffold the project using the standard directory layout:
- `com.example.{service}.config`
- `com.example.{service}.web`
- `com.example.{service}.services`
- `com.example.{service}.repositories`
- `com.example.{service}.entities`

### 2. Dependency Management
Refer to the `pom.xml` of `catalog-service` as a template. Key starters:
- `spring-boot-starter-webflux` or `webmvc`
- `spring-boot-starter-actuator`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-cloud-starter-config`
- `micrometer-registry-prometheus`

### 3. Service Configuration
- Set `spring.application.name` in `bootstrap.yml`.
- Configure `spring.config.import` to point to the Config Server.
- Add the service to the `api-gateway` routes in its `application.yml`.

### 4. Deployment
Add a new service definition to `deployment/docker-compose.yml`. Use the standard image naming convention: `dockertmt/mmv2-{service}:latest`.

## Verification
1. Run `./mvnw clean install` to build.
2. Run `docker-compose up -d {service}`.
3. Verify health via Gateway: `curl http://localhost:8765/{service}/actuator/health`.
