# Spring Boot Microservices Series V2: A Deep Dive into Microservice Architectures

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/rajadilipkolli/spring-boot-microservices-series-v2)

A comprehensive example project demonstrating a microservices architecture using Spring Boot, Spring Cloud, Docker, Kafka, and various other modern technologies. This project serves as a practical guide for building resilient, scalable, and observable distributed systems.

## Key Features

*   **Microservice Architecture:** Decoupled services for different business domains.
*   **Spring Boot & Spring Cloud:** Robust framework for building and managing microservices (Service Registry, API Gateway, Config Server).
*   **Containerization:** Dockerized services for consistent environments and easy deployment using Docker Compose.
*   **Asynchronous Communication:** Apache Kafka for event-driven communication between services.
*   **Observability:** Integrated monitoring and tracing with Prometheus, Grafana, and Micrometer (leveraging the capabilities formerly provided by Spring Cloud Sleuth & Zipkin).
*   **Database Diversity:** Demonstrates various database technologies (PostgreSQL, MongoDB) and schema management with Liquibase (XML, YAML, JSON formats).
*   **Centralized Configuration:** Using Spring Cloud Config Server.
*   **API Gateway:** Single entry point for all microservices using Spring Cloud Gateway.
*   **Service Discovery:** Eureka for dynamic service registration and discovery.

## Architecture Overview

This project implements a microservices pattern, where different functionalities are broken down into independent services. These services communicate with each other, often asynchronously via message brokers like Kafka, and are managed and discovered through tools like Eureka and Spring Cloud Gateway.
![](images/microservicesArchitecture.png)

## Modules

This project consists of the following microservices and supporting modules:

*   `api-gateway`: Provides a single entry point and routes requests to appropriate backend services.
*   `catalog-service`: Manages product catalog information (e.g., using PostgreSQL, Liquibase with YAML).
*   `config-server`: Centralized configuration management for all microservices.
*   `inventory-service`: Manages stock levels for products (e.g., using MongoDB, Liquibase with JSON).
*   `order-service`: Handles customer orders and orchestrates interactions with other services like payment and inventory (e.g., using PostgreSQL, Liquibase with XML).
*   `payment-service`: Processes payments for orders (e.g., using PostgreSQL, Liquibase with XML).
*   `service-registry`: Eureka server enabling service discovery within the microservices ecosystem.
*   `retail-store-webapp`: A sample web application that interacts with the backend microservices.

## Tech Stack

* <img width='25' height='25' src='https://img.stackshare.io/service/995/K85ZWV2F.png' alt='Java'/> [Java](https://www.java.com) – Languages
* <img width='25' height='25' src='https://img.stackshare.io/service/5807/default_cbd8ab670309059d7e315252d307d409aa40d793.png' alt='Project Reactor'/> [Project Reactor](https://projectreactor.io/) – Java Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/5494/default_b403ef08976083aea6d4caf5a4f19f3325c751e5.png' alt='Spring Cloud'/> [Spring Cloud](https://spring.io/projects/spring-cloud) – Frameworks (Full Stack)
* <img width='25' height='25' src='https://img.stackshare.io/service/2006/spring-framework-project-logo.png' alt='Spring Framework'/> [Spring Framework](https://spring.io/projects/spring-framework) – Frameworks (Full Stack)
* <img width='25' height='25' src='https://img.stackshare.io/service/1063/kazUJooF_400x400.jpg' alt='Kafka'/> [Kafka](http://kafka.apache.org/) – Message Queue
* <img width='25' height='25' src='https://img.stackshare.io/service/8938/default_952e19080b823dcfc14ef0508ae6a783d35224f6.png' alt='Kafka REST'/> [Kafka REST](https://github.com/confluentinc/kafka-rest) – Kafka Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/9190/kazUJooF_400x400.jpg' alt='Kafka Streams'/> [Kafka Streams](https://kafka.apache.org/documentation/streams/) – Stream Processing
* <img width='25' height='25' src='https://img.stackshare.io/service/1398/y1As8_s5_400x400.jpg' alt='Liquibase'/> [Liquibase](https://www.liquibase.com) – Database Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/1028/ASOhU5xJ.png' alt='PostgreSQL'/> [PostgreSQL](http://www.postgresql.org/) – Databases
* <img width='25' height='25' src='https://img.stackshare.io/service/1031/default_cbce472cd134adc6688572f999e9122b9657d4ba.png' alt='Redis'/> [Redis](http://redis.io/) – In-Memory Databases
* <img width='25' height='25' src='https://img.stackshare.io/service/7624/IG6D4Ro2_400x400.png' alt='Spring Data'/> [Spring Data](https://spring.io/projects/spring-data) – Database Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/2279/jooq-logo-white-750x750-padded.png' alt='jOOQ'/> [jOOQ](http://www.jooq.org) – Database Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/586/n4u37v9t_400x400.png' alt='Docker'/> [Docker](https://www.docker.com/) – Virtual Machine Platforms & Containers
* <img width='25' height='25' src='https://img.stackshare.io/service/3136/docker-compose.png' alt='Docker Compose'/> [Docker Compose](https://github.com/docker/compose) – Container Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/11563/actions.png' alt='GitHub Actions'/> [GitHub Actions](https://github.com/features/actions) – Continuous Integration
* <img width='25' height='25' src='https://img.stackshare.io/service/2645/default_8f9d552b144493679449b16c79647da5787e808b.jpg' alt='Grafana'/> [Grafana](http://grafana.org/) – Monitoring Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/2020/874086.png' alt='JUnit'/> [JUnit](http://junit.org/) – Testing Frameworks
* <img width='25' height='25' src='https://img.stackshare.io/service/2923/05518ecaa42841e834421e9d6987b04f_400x400.png' alt='Logback'/> [Logback](https://logback.qos.ch/) – Log Management
* <img width='25' height='25' src='https://img.stackshare.io/service/2501/default_3cf1b307194b26782be5cb209d30360580ae5b3c.png' alt='Prometheus'/> [Prometheus](http://prometheus.io/) – Monitoring Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/2494/8leuukuhknbc8fj0eg42_400x400.png' alt='Zipkin'/> [Zipkin](https://zipkin.io/) – Monitoring Tools
* <img width='25' height='25' src='https://img.stackshare.io/service/6429/alpine_linux.png' alt='Alpine Linux'/> [Alpine Linux](https://www.alpinelinux.org/) – Operating Systems
* <img width='25' height='25' src='https://img.stackshare.io/service/4631/default_c2062d40130562bdc836c13dbca02d318205a962.png' alt='Shell'/> [Shell](https://en.wikipedia.org/wiki/Shell_script) – Shells

For more details, see [techstack.md](./techstack.md).

## Getting Started

### Prerequisites

*   Java 21 or higher (ensure `JAVA_HOME` is set)
*   Maven 3.9.x or higher (for building)
*   Docker & Docker Compose (for running the services)
*   Git (for cloning the repository)

### Build

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/rajadilipkolli/spring-boot-microservices-series-v2.git
    cd spring-boot-microservices-series-v2
    ```

2.  **Build all modules using Maven Wrapper:**
    *   For Linux/macOS:
        ```bash
        ./mvnw clean install
        ```
    *   For Windows:
        ```cmd
        .\mvnw.cmd clean install
        ```

### Running the Application

The entire application stack can be run using Docker Compose.

1.  **Core Services:**
    To run the main application services (defined in `deployment/docker-compose.yml`):
    ```bash
    docker-compose -f deployment/docker-compose.yml up -d --remove-orphans
    ```

2.  **Services with Monitoring Tools:**
    To run the application services along with monitoring tools like Prometheus, Grafana, etc. (defined in `deployment/docker-compose-tools.yml`):
    ```bash
    docker-compose -f deployment/docker-compose-tools.yml up -d --remove-orphans
    ```

3.  **Helper Scripts:**
    Convenience scripts are provided to start the core services:
    *   Linux/macOS: `bash run.sh` (executes `docker-compose up -d --remove-orphans` from the `deployment` directory using `deployment/docker-compose.yml`)
    *   Windows: `.\start-services.ps1` (similarly executes `docker-compose up -d --remove-orphans` from the `deployment` directory)

To stop the services:
```bash
docker-compose -f deployment/docker-compose.yml down # Or docker-compose-tools.yml
```

## Service Discovery & API Access

*   **Service Registry (Eureka):** View registered services at [http://localhost:8761/](http://localhost:8761/)
*   **API Gateway Swagger UI:** Access all service APIs through the gateway's aggregated Swagger UI at [http://localhost:8765/swagger-ui.html](http://localhost:8765/swagger-ui.html). You can select individual service APIs from the dropdown menu.
    ![](images/swagger.jpg)

## Observability: Monitoring and Tracing

This project integrates several tools for comprehensive observability.

### Metrics, Alerting with Prometheus & Grafana

*   **Prometheus:** Scrapes metrics exposed by microservices. Access the Prometheus UI at [http://localhost:9090/](http://localhost:9090/).
*   **Alertmanager:** Handles alerts triggered by Prometheus based on rules defined in `deployment/config/prometheus/alert-rules.yml` (path may vary based on exact volume mount in `docker-compose-tools.yml`). Access Alertmanager UI at [http://localhost:9093/](http://localhost:9093/).
*   **Grafana:** Visualize metrics with pre-built or custom dashboards. Access Grafana at [http://localhost:3000/](http://localhost:3000/) (default credentials are often admin/admin).

**Alerting Flow:**
```mermaid
---
title: Alerting Flow
---
flowchart TD
    subgraph Microservices
        direction TB
        ms1[Service A Metrics]
        ms2[Service B Metrics]
        ms3[Service C Metrics]
    end

    Prometheus[Prometheus Server] -- Scrapes --> ms1
    Prometheus -- Scrapes --> ms2
    Prometheus -- Scrapes --> ms3

    Prometheus -- Evaluates Rules --> AlertRules[alert-rules.yml]
    AlertRules -- If Condition Met --> PrometheusTriggersAlert[Prometheus Triggers Alert]
    PrometheusTriggersAlert -- Sends Alert --> AlertManager[AlertManager]
    AlertManager -- Manages & Routes --> Notifications[Notifications (Email, Slack, etc.)]

    style Prometheus fill:#f9f,stroke:#333,stroke-width:2px
    style AlertManager fill:#ccf,stroke:#333,stroke-width:2px
```

### Distributed Tracing with Micrometer and Zipkin

*   **Micrometer Tracing:** Integrated into Spring Boot applications to generate and propagate trace context.
*   **Zipkin:** Collects and visualizes distributed traces, helping to understand request flows and latencies across services. Access the Zipkin UI at [http://localhost:9411/zipkin/](http://localhost:9411/zipkin/).
    ![](images/zipkin.jpg)

## Database Schema Management with Liquibase

This project demonstrates flexible database schema management using Liquibase with various changelog formats across different services:

| Liquibase Format | Service Example(s)                                                         |
|------------------|----------------------------------------------------------------------------|
| XML              | `order-service`, `payment-service`                                         |
| YAML             | `catalog-service`                                                          |
| JSON             | `inventory-service`                                                        |
| SQL              | (Can be used, not explicitly shown as primary in current service examples) |

Changelogs are typically located in `src/main/resources/db/changelog` within each service.

## Useful Docker Commands

*   **Clean up Docker System:** Remove all unused containers, networks, images (both dangling and unreferenced), and optionally, volumes.
    ```bash
    docker system prune -a -f --volumes
    ```
*   **Prune Unused Volumes:**
    ```bash
    docker volume prune -f
    ```
*   **List Running Containers:**
    ```bash
    docker ps
    ```
*   **View Logs for All Services in a Compose File:** (e.g., from `docker-compose-tools.yml`)
    ```bash
    docker-compose -f deployment/docker-compose-tools.yml logs -f
    ```
*   **View Logs for a Specific Service:** (e.g., `order-service` from `docker-compose.yml`)
    ```bash
    docker-compose -f deployment/docker-compose.yml logs -f order-service
    ```
*   **Follow Logs from Last N Lines:**
    ```bash
    docker-compose -f deployment/docker-compose-tools.yml logs --tail=100 -f
    ```

## Development Utility Tips

### Kill Application Running on a Port

*   **Windows (Command Prompt/PowerShell):**
    1.  Find process ID (PID) using the port (e.g., 18080):
        ```shell
        netstat -ano | findstr :18080
        ```
    2.  Kill the process using its PID:
        ```shell
        taskkill /PID <PID_FROM_ABOVE> /F
        ```
*   **Linux/macOS (Terminal):**
    1.  Find process ID (PID) using the port (e.g., 18080):
        ```bash
        sudo lsof -i :18080
        ```
    2.  Kill the process using its PID:
        ```bash
        kill -9 <PID_FROM_ABOVE>
        ```

## Important Notes

### Breaking Changes & Project Evolution (Spring Boot 3.x Context)
*   **Jakarta EE Namespace:** Migration from `javax.*` to `jakarta.*` is a key change in Spring Boot 3.x.
*   **Observability with Micrometer:** Spring Cloud Sleuth has been superseded by Micrometer Tracing for distributed tracing capabilities.
*   **Asynchronous Communication & Observability:** While RabbitMQ is a valid choice, this project emphasizes Kafka. Ensure compatibility of chosen message brokers with the Micrometer-based observability stack.
*   **Log Forwarding (Promtail/Fluent-bit):** Promtail is highlighted due to broader architecture support (e.g., ARM64) compared to Fluent-bit's common AMD64 focus. If using Fluent-bit, ensure your log aggregation backend (like Grafana Loki) is operational first.
*   **jOOQ and `@Transactional`:** jOOQ often requires `@Transactional` annotations directly on repository methods for its transaction management to integrate seamlessly, even if service layers are also annotated.

### Spring MVC Test Tip for `BigDecimal` Assertions
When using `jsonPath` in Spring MVC tests to assert `BigDecimal` values, use Hamcrest's `closeTo` matcher for handling potential precision issues:
```java
import static org.hamcrest.Matchers.closeTo;
// ...

// Instead of:
// .andExpect(jsonPath("$.totalPrice").value(100.00))
// Use:
.andExpect(jsonPath("$.totalPrice").value(closeTo(new BigDecimal("100.00"), new BigDecimal("0.01"))))
```

### Native Image (GraalVM) Considerations
Certain modules, particularly those relying heavily on Netty (like `config-server` and `api-gateway`), may present challenges for straightforward out-of-the-box GraalVM native image compilation and might require additional configuration.

## Contributing

We welcome contributions! If you'd like to contribute, please fork the repository and submit a pull request. We encourage you to first open an issue to discuss any significant changes.

Please read our [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) to understand our community standards.

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for full details.

## Acknowledgements & References

*   This project draws inspiration from common microservice patterns and best practices in the Spring ecosystem.
*   The article on [Distributed Transactions in Microservices with Kafka Streams and Spring Boot](https://piotrminkowski.com/2022/01/24/distributed-transactions-in-microservices-with-kafka-streams-and-spring-boot/) provides valuable insights relevant to some concepts explored here.
