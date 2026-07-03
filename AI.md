# Agent Configuration & Skills

Welcome, Agent. This file serves as your primary entry point for understanding the architecture and development standards of the **Spring Boot Microservices Series V2** project.

## 🏗️ Project Architecture
This is a microservices-based system built with:
- **Core**: Java 25, Spring Boot 4.1.0, Spring Cloud 2025.
- **Service Mesh**: Eureka (Discovery), Config Server (Centralized Config), Spring Cloud Gateway (API Gateway).
- **Persistence**: PostgreSQL managed by Liquibase.
- **Messaging**: Apache Kafka for event-driven communication.
- **Observability**: Prometheus, Grafana, OpenTelemetry, and Micrometer.

## 📜 Development Skills & Playbooks
To ensure consistency and follow project-specific standards, specialized playbooks are stored in the `.ai/skills/` directory. **Always refer to these files before performing the following tasks:**

1.  **Native Hints**: [implement_native_hints.md](./.ai/skills/implement_native_hints.skill.md) – How to register reflection/proxy hints for GraalVM.
2.  **New Microservice**: [add_new_microservice.md](./.ai/skills/add_new_microservice.skill.md) – Step-by-step scaffolding of a new service.
3.  **Architecture**: [solve_modulith_violations.md](./.ai/skills/solve_modulith_violations.skill.md) – Resolving Spring Modulith violations.
4.  **Database**: [manage_database_migration.md](./.ai/skills/manage_database_migration.skill.md) – Using Liquibase across XML/YAML/JSON formats.

## 🛠️ Global Constraints
- **Formatting**: Use Google Java Format (AOSP style) enforced by Spotless.
- **Linting**: Checkstyle must pass on every build.
- **Testing**: Use Testcontainers for integration tests; verify with `./mvnw.cmd verify`.
- **Modularity**: Respect package-private visibility where required by Spring Modulith.

Please use these resources to provide accurate and idiomatic assistance.
