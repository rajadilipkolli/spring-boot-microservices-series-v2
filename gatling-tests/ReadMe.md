# Gatling Performance Tests Suite for Microservices

This module contains a modernized Gatling performance testing suite designed for high-concurrency validation of the Spring Boot microservices architecture.

## Overview

The test suite has been refactored to use a **three-phase injection profile** (Ramp-up, Steady-state, Ramp-down) and centralized lifecycle management. All simulations now perform pre-flight health checks and data initialization in the `before()` hook, ensuring a stable environment for performance measurements.

## Prerequisites

1. **Microservices Environment**: Ensure all services (Catalog, Inventory, Order, Payment) and the API Gateway are running.
2. **API Gateway**: Must be accessible at `http://localhost:8765` (default).
3. **Java 25**: Required for Gatling and the microservices.
4. **Maven**: Used for test execution and dependency management.

## Available Simulations

| Simulation | Description | Use Case |
|------------|-------------|----------|
| `CreateProductSimulation` | Single-user end-to-end workflow validation. | Verifies core business logic: Create Product -> Get Product -> Update Inventory -> Place Order. |
| `StressTestSimulation` | High-load multi-path user journey testing. | Simulates realistic user behavior including browsing, searching, and purchasing under heavy load. |
| `ResilienceTestSimulation` | Service resilience and error handling. | Tests how the system handles invalid data and high concurrency on shared resources. |
| `ApiGatewayResilienceSimulation` | Gateway-level resilience patterns. | Specifically targets rate limiting and circuit breaker behavior at the API Gateway level. |

## Load Profiles

All simulations follow a standard three-phase load pattern to provide reliable and repeatable metrics:

1. **Ramp-up Phase**: Gradually increases user injection from 0 to the target rate over a specified duration (default: 30-60s). This allows the JVM and connection pools to warm up.
2. **Steady-state Phase**: Maintains a constant load for a fixed duration (default: 2-10m) to measure sustained performance and stability.
3. **Ramp-down Phase**: Gradually reduces the load to 0, allowing for clean termination and final metric collection.

## Configuration Properties

Tests can be customized using Maven system properties:

| Property | Description | Default |
|----------|-------------|---------|
| `baseUrl` | Base URL for the API Gateway | `http://localhost:8765` |
| `rampUsers` | Total users injected during ramp-up | `20` |
| `constantUsers` | Target users per second during steady-state | `50` |
| `rampDuration` | Duration of ramp-up phase (seconds) | `30` |
| `testDuration` | Duration of steady-state phase (seconds) | `180` |
| `maxUsers` | Maximum users (used in Stress/Resilience) | `50` |
| `targetRate` | Target injection rate for resilience tests | `10` |

## Running the Tests

### Using the Shell Scripts (Recommended)

The provided scripts perform automatic pre-flight health checks before starting Gatling.

#### Windows (PowerShell):
```powershell
# Standard test run (3-5 minutes)
.\run-tests.ps1

# Quick smoke test (1-2 minutes)
.\run-tests.ps1 -TestProfile quick

# Sustained stability test (10+ minutes)
.\run-tests.ps1 -TestProfile extended -Users 100
```

#### Linux/macOS (Bash):
```bash
chmod +x run-tests.sh

# Run standard profile
./run-tests.sh -p standard

# Run with custom users and duration
./run-tests.sh -n 100 -d 600
```

### Using Maven Directly

```bash
mvn clean gatling:test -Dgatling.simulationClass=simulation.StressTestSimulation -DconstantUsers=20
```

## Troubleshooting

### Pre-flight Health Checks Failed
If the runner scripts abort, check if the services are healthy at the actuator endpoints:
- Gateway: `http://localhost:8765/actuator/health`
- Catalog: `http://localhost:8765/catalog-service/actuator/health`

Common causes:
- Service not registered with Discovery (wait 30-60s after startup).
- Database connection issues (check microservice logs).

### Kafka Initialization Issues
The `ResilienceTestSimulation` and `CreateProductSimulation` perform a warm-up request in the `before()` hook to initialize Kafka topics. If you see timeouts in the first few requests of a run:
- Ensure Kafka/Zookeeper containers are healthy.
- Increase the sleep duration in the `warmUpKafka()` method in `BaseSimulation.java`.

### High Error Rate (503/504)
During `StressTestSimulation`, 503 (Service Unavailable) or 504 (Gateway Timeout) errors are often intentional indicators of system capacity limits. Check the Gatling reports for the specific bottleneck.

---
*Note: While Apache JMeter plans are provided in the `jmeter/` directory, **Gatling is the primary load testing tool** for this project and provides the most comprehensive resilience metrics.*
