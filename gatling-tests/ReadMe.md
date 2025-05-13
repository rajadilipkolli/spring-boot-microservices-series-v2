# Gatling Performance Tests Suite for Microservices

This module contains comprehensive Gatling performance tests for the Spring Boot microservices architecture. These tests validate various aspects of the system including functionality, performance, and resilience.

## Prerequisites

1. All microservices must be running (catalog-service, inventory-service, order-service, etc.)
2. API Gateway must be accessible at http://localhost:8765 (configurable)
3. JDK 21 or later

## Available Test Suites

This test suite contains several specialized simulations:

| Simulation                     | Description                 | Use Case                                   |
|--------------------------------|-----------------------------|--------------------------------------------|
| ServiceHealthCheckSimulation   | Health check validation     | Verifies all services are up and running   |
| CreateProductSimulation        | End-to-end workflow testing | Validates core business functionality      |
| ResilienceTestSimulation       | Error handling & validation | Tests system's response to invalid inputs  |
| StressTestSimulation           | High load testing           | Tests system under realistic user patterns |
| ApiGatewayResilienceSimulation | API Gateway patterns        | Tests rate limiting and circuit breakers   |

## Test Configuration

The tests can be configured using system properties:

| Property               | Description                       | Default               | Applicable Tests                                  |
|------------------------|-----------------------------------|-----------------------|---------------------------------------------------|
| baseUrl                | Base URL for the API Gateway      | http://localhost:8765 | All                                               |
| rampUsers              | Users to ramp up                  | 5                     | CreateProductSimulation                           |
| constantUsers          | Users per second                  | 30                    | CreateProductSimulation                           |
| rampDuration           | Ramp-up time (seconds)            | 30                    | CreateProductSimulation                           |
| testDuration           | Test duration (seconds)           | 60                    | CreateProductSimulation, ResilienceTestSimulation |
| maxUsers               | Maximum concurrent users          | 100                   | StressTestSimulation                              |
| rampDurationMinutes    | Ramp time (minutes)               | 5                     | StressTestSimulation                              |
| plateauDurationMinutes | Steady state time (minutes)       | 10                    | StressTestSimulation                              |
| burstUsers             | Burst users for API Gateway tests | 50                    | ApiGatewayResilienceSimulation                    |
| sustainSeconds         | Sustained test duration           | 30                    | ApiGatewayResilienceSimulation                    |

## Running the Tests

### JMeter Alternative

For users who prefer Apache JMeter, a sample test plan is provided in the `jmeter` directory. To use it:

1. Open JMeter and load `jmeter/product-workflow-test-plan.jmx`
2. Update variables as needed (BASE_URL, PORT)
3. Run the test plan

### Using the Provided Scripts

#### Windows (PowerShell):

```powershell
# Run default tests
.\run-tests.ps1

# Run quick tests (minimal load)
.\run-tests.ps1 -TestProfile quick

# Run high load tests
.\run-tests.ps1 -TestProfile heavy -Users 50 -Duration 300

# Run API Gateway resilience tests
.\run-tests.ps1 -TestProfile gateway -Users 30 -Duration 60

# Run stress tests
.\run-tests.ps1 -TestProfile stress -Users 100 -Duration 300
```

#### Linux/macOS (Bash):

```bash
# Make the script executable
chmod +x run-tests.sh

# Run default tests
./run-tests.sh

# Run quick tests
./run-tests.sh -p quick

# Run high load tests
./run-tests.sh -p heavy -n 50 -d 300
```

### Using Maven Directly

```bash
# Run with default profile
mvn gatling:test

# Run resilience tests
mvn gatling:test -P resilience -DbaseUrl=http://localhost:8765 -Dusers=20 -DtestDuration=60

# Run stress tests
mvn gatling:test -P stress -DmaxUsers=50 -DrampDurationMinutes=2 -DplateauDurationMinutes=5

# Run all tests
mvn gatling:test -P all
```

### From Your IDE

Run any of the simulation classes as a Java application:
- `CreateProductSimulation`
- `ResilienceTestSimulation`
- `StressTestSimulation`
- `ApiGatewayResilienceSimulation`

## Test Reports

After running the tests, Gatling generates comprehensive HTML reports in the `target/gatling` directory:

- **Dashboard:** Overall test metrics and summary statistics
- **Requests:** Detailed breakdown of each API request performance
- **Charts:** Response time distribution and percentiles
- **Global Information:** Cumulative statistics for the entire test

## Key Performance Indicators

- **Response Time:** Mean, median, p95, p99, min, max
- **Throughput:** Requests per second
- **Success Rate:** Percentage of successful requests
- **Error Rate:** Percentage of failed requests
- **Active Users:** Concurrent user count during test

## Best Practices for Performance Testing

1. **Dedicated Environment:** Run tests in an isolated environment
2. **Realistic Data:** Use data that mirrors production patterns
3. **Warm-up Period:** Allow JVM services to warm up before measuring
4. **Multiple Test Runs:** Perform several runs to account for variance
5. **Resource Monitoring:** Monitor CPU, memory, and network during tests
6. **Incremental Load:** Gradually increase load to find breaking points
7. **Regular Baselines:** Establish performance baselines regularly

## Test Flows

### Create Product Workflow
1. Create a product in the catalog service
2. Get the created product
3. Get the product's inventory
4. Update the product's inventory
5. Create an order using the product

### Stress Test User Journeys
1. **Casual Browsers:** Browse catalog and view products
2. **Active Searchers:** Search for products and occasionally place orders
3. **Power Shoppers:** Browse, view and purchase products frequently

### Resilience Patterns
1. Validation error handling
2. Rate limiting behavior
3. Circuit breaker activation
4. Service degradation under load
