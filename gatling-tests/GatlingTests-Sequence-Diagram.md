# Gatling Performance Tests - Sequence Diagram

```mermaid
%%{init: {'theme':'dark', 'themeVariables': { 'primaryColor': '#ff6b6b', 'primaryTextColor': '#fff', 'primaryBorderColor': '#ff6b6b', 'lineColor': '#fff', 'secondaryColor': '#384454', 'tertiaryColor': '#384454', 'background': '#0f0f23', 'mainBkg': '#1e1e3f', 'secondBkg': '#2d2d5a', 'tertiaryBkg': '#3c3c75'}}}%%

sequenceDiagram
    participant User as 👤 Test Engineer
    participant Script as 📜 run-tests Script
    participant Maven as 🔧 Maven
    participant Gatling as ⚡ Gatling Engine
    participant Gateway as 🌐 API Gateway
    participant Catalog as 📚 Catalog Service
    participant Inventory as 📦 Inventory Service
    participant Order as 🛍️ Order Service
    participant Payment as 💳 Payment Service
    participant Kafka as 📡 Kafka
    participant Reports as 📊 Report Generator

    Note over User, Reports: 🚀 Gatling Performance Testing Suite
    
    %% Test Initialization
    User->>Script: ./run-tests.sh --profile stress --users 100
    Script->>Script: Parse parameters & validate profile
    
    %% Health Check Phase (Now in Script)
    Note over Script, Payment: 🏥 Pre-Test Health Validation
    
    loop Service Health Validation
        Script->>Gateway: GET /actuator/health
        Gateway-->>Script: 200 OK (Gateway healthy)
        
        Script->>Gateway: GET /catalog-service/actuator/health
        Gateway->>Catalog: Forward health check
        Catalog-->>Gateway: 200 OK
        Gateway-->>Script: 200 OK (Catalog healthy)
        
        Script->>Gateway: GET /inventory-service/actuator/health
        Gateway->>Inventory: Forward health check
        Inventory-->>Gateway: 200 OK
        Gateway-->>Script: 200 OK (Inventory healthy)
        
        Script->>Gateway: GET /order-service/actuator/health
        Gateway->>Order: Forward health check
        Order-->>Gateway: 200 OK
        Gateway-->>Script: 200 OK (Order healthy)
        
        Script->>Gateway: GET /payment-service/actuator/health
        Gateway->>Payment: Forward health check
        Payment-->>Gateway: 200 OK
        Gateway-->>Script: 200 OK (Payment healthy)
    end
    
    alt All Services Healthy
        Script->>Script: ✅ All services available
    else Service Unavailable
        Script-->>User: ❌ Test failed - services not ready (Exit 1)
    end

    %% Test Data Generation
    Note over Script, Kafka: 📊 Test Data Preparation & Warmup
    Script->>Gateway: POST /api/v1/generate
    Gateway->>Catalog: Generate test products & inventory
    Catalog->>Kafka: Publish ProductCreated/InventoryCreated events
    Catalog-->>Gateway: 200 OK
    Gateway-->>Script: 200 OK (Data generated)

    %% Kafka Initialization Delay
    Note over Script, Kafka: ⏱️ Kafka Initialization
    Script->>Script: sleep 10 (Wait for topics to initialize)
    Kafka->>Kafka: Process events
    
    %% Start Gatling
    Script->>Maven: mvn clean gatling:test -Dgatling.simulationClass=...
    Maven->>Gatling: Initialize Gatling Engine

    %% Main Load Test Phase
    Note over Gatling, Reports: 🚀 Main Load Test (Multi-User Scenarios)
    
    rect rgb(60, 20, 20)
        
        par Casual Browsers (30% traffic)
            loop Browse Catalog Flow
                Gatling->>Gateway: GET /catalog-service/api/catalog?pageNo=0
                Gateway->>Catalog: Browse products page 1
                Catalog-->>Gateway: 200 OK + Products
                Gateway-->>Gatling: Page 1 loaded
                
                Gatling->>Gateway: GET /catalog-service/api/catalog?pageNo=1
                Gateway->>Catalog: Browse products page 2
                Catalog-->>Gateway: 200 OK + Products
                Gateway-->>Gatling: Page 2 loaded
            end
        and Active Searchers (20% traffic)
            loop Search Products Flow
                Gatling->>Gateway: GET /catalog-service/api/catalog/search?term=product
                Gateway->>Catalog: Search by term
                Catalog-->>Gateway: 200 OK + Search results
                Gateway-->>Gatling: Search completed
                
                Gatling->>Gateway: GET /catalog-service/api/catalog/search?minPrice=10&maxPrice=100
                Gateway->>Catalog: Search by price range
                Catalog-->>Gateway: 200 OK + Filtered results
                Gateway-->>Gatling: Price filter applied
            end
        and Product Viewers (30% traffic)
            loop Product Detail Flow
                Gatling->>Gateway: POST /catalog-service/api/catalog (Create Product)
                Gateway->>Catalog: Create new product
                Catalog->>Kafka: Publish ProductCreated event
                Catalog-->>Gateway: 201 Created
                Gateway-->>Gatling: Product created
                
                Gatling->>Gateway: POST /inventory-service/api/inventory
                Gateway->>Inventory: Initialize inventory
                Inventory->>Kafka: Publish InventoryCreated event
                Inventory-->>Gateway: 201 Created
                Gateway-->>Gatling: Inventory initialized
                
                Gatling->>Gateway: GET /catalog-service/api/catalog/product-code/{code}
                Gateway->>Catalog: Get product details
                Catalog-->>Gateway: 200 OK + Product data
                Gateway-->>Gatling: Product details loaded
                
                Gatling->>Gateway: GET /inventory-service/api/inventory/{code}
                Gateway->>Inventory: Check availability
                Inventory-->>Gateway: 200 OK + Stock info
                Gateway-->>Gatling: Stock information retrieved
            end
        and Power Shoppers (20% traffic)
            loop Complete Order Flow
                Gatling->>Gateway: POST /catalog-service/api/catalog (Create Product)
                Gateway->>Catalog: Create product for order
                Catalog->>Kafka: Publish ProductCreated event
                Catalog-->>Gateway: 201 Created
                Gateway-->>Gatling: Product ready
                
                Gatling->>Gateway: POST /inventory-service/api/inventory
                Gateway->>Inventory: Setup inventory
                Inventory->>Kafka: Publish InventoryCreated event
                Inventory-->>Gateway: 201 Created
                Gateway-->>Gatling: Inventory ready
                
                Gatling->>Gateway: GET /catalog-service/api/catalog/product-code/{code}
                Gateway->>Catalog: Verify product exists
                Catalog-->>Gateway: 200 OK + Product + Price
                Gateway-->>Gatling: Product verified
                
                Gatling->>Gateway: GET /inventory-service/api/inventory/{code}
                Gateway->>Inventory: Check stock availability
                Inventory-->>Gateway: 200 OK + Available quantity
                Gateway-->>Gatling: Stock confirmed
                
                Gatling->>Gateway: POST /order-service/api/orders
                Gateway->>Order: Place order with items
                Order->>Kafka: Publish OrderCreated event
                Order->>Inventory: Reserve inventory
                Order-->>Gateway: 201 Created + Order location
                Gateway-->>Gatling: Order placed successfully
            end
        end
    end
    
    %% Load Pattern Execution
    Note over Gatling, Reports: 📈 Load Pattern: Ramp-up → Plateau → Cool-down
    
    rect rgb(20, 60, 20)
        Note over Gatling: 🔄 Ramp-up Phase (5 minutes)
        Gatling->>Gatling: rampUsersPerSec(0 → 25)
        Gatling->>Gatling: rampUsersPerSec(25 → 50)
        Gatling->>Gatling: rampUsersPerSec(50 → 100)
    end
    
    rect rgb(60, 60, 20)
        Note over Gatling: 🏔️ Plateau Phase (10 minutes)
        Gatling->>Gatling: constantUsersPerSec(100)
    end
    
    rect rgb(20, 20, 60)
        Note over Gatling: 🔽 Cool-down Phase (2 minutes)
        Gatling->>Gatling: rampUsersPerSec(100 → 0)
    end

    %% Resilience Testing Scenarios
    alt Resilience Test Profile
        Note over Gatling, Reports: 🛡️ Resilience & Error Handling Tests
        
        par Valid Requests (70% traffic)
            Gatling->>Gateway: POST /catalog-service/api/catalog (Valid data)
            Gateway->>Catalog: Process valid product
            Catalog-->>Gateway: 201 Created
            Gateway-->>Gatling: Success response
        and Invalid Requests (20% traffic)
            Gatling->>Gateway: POST /catalog-service/api/catalog (Invalid data)
            Gateway->>Catalog: Process invalid product
            Catalog-->>Gateway: 400 Bad Request
            Gateway-->>Gatling: Validation error (Expected)
        and High Concurrency (10% traffic)
            loop Concurrent Access
                Gatling->>Gateway: GET /catalog-service/api/catalog/P000001
                Gateway->>Catalog: Concurrent product access
                Catalog-->>Gateway: 200 OK
                Gateway-->>Gatling: Concurrent access handled
            end
        end
        
        %% Circuit Breaker Testing
        Note over Gatling, Gateway: ⚡ Circuit Breaker Activation
        loop Rapid Error Requests
            Gatling->>Gateway: GET /catalog-service/api/catalog/error
            Gateway->>Catalog: Error endpoint
            Catalog-->>Gateway: 500 Internal Server Error
            Gateway->>Gateway: Increment error count
        end
        
        Gateway->>Gateway: Circuit breaker OPEN
        Gatling->>Gateway: GET /catalog-service/api/catalog
        Gateway-->>Gatling: 503 Service Unavailable (Circuit Open)
    end

    %% API Gateway Resilience Testing
    alt API Gateway Resilience Profile
        Note over Gatling, Gateway: 🌐 API Gateway Patterns Testing
        
        %% Rate Limiting Test
        rect rgb(80, 40, 40)
            Note over Gatling, Gateway: 🚦 Rate Limiting Test
            loop Rapid Fire Requests (10 requests)
                Gatling->>Gateway: GET /catalog-service/api/catalog
                alt Rate Limit Not Exceeded
                    Gateway->>Catalog: Forward request
                    Catalog-->>Gateway: 200 OK
                    Gateway-->>Gatling: 200 OK
                else Rate Limit Exceeded
                    Gateway-->>Gatling: 429 Too Many Requests
                    Gatling->>Gatling: rateLimitedCount++
                end
            end
        end
        
        %% Mixed Load Test
        rect rgb(40, 80, 40)
            Note over Gatling, Payment: 🔀 Mixed Service Load Test
            par Catalog Requests (25%)
                Gatling->>Gateway: GET /catalog-service/api/catalog/P000001
                Gateway->>Catalog: Get product
                Catalog-->>Gateway: 200 OK
                Gateway-->>Gatling: Product data
            and Inventory Requests (25%)
                Gatling->>Gateway: GET /inventory-service/api/inventory/P000001
                Gateway->>Inventory: Get inventory
                Inventory-->>Gateway: 200 OK
                Gateway-->>Gatling: Inventory data
            and Order Requests (25%)
                Gatling->>Gateway: GET /order-service/api/orders
                Gateway->>Order: List orders
                Order-->>Gateway: 200 OK
                Gateway-->>Gatling: Orders list
            and Payment Requests (25%)
                Gatling->>Gateway: GET /payment-service/api/payments
                Gateway->>Payment: List payments
                Payment-->>Gateway: 200 OK
                Gateway-->>Gatling: Payments list
            end
        end
    end

    %% Performance Monitoring & Metrics
    Note over Gatling, Reports: 📊 Performance Monitoring & Metrics Collection
    
    par Real-time Metrics
        Gatling->>Gatling: Collect response times
        Gatling->>Gatling: Track success/failure rates
        Gatling->>Gatling: Monitor active users
        Gatling->>Gatling: Calculate throughput (RPS)
    and SLA Validation
        Gatling->>Gatling: Assert mean response time < SLA
        Gatling->>Gatling: Assert 95th percentile < SLA
        Gatling->>Gatling: Assert error rate checks
    end

    %% Test Completion & Reporting
    Note over Gatling, Reports: 📈 Test Completion & Report Generation
    
    Gatling->>Reports: Generate HTML reports
    Reports->>Reports: Create performance dashboard
    
    Reports-->>Gatling: Reports generated
    Gatling-->>Maven: Test execution completed
    Maven-->>Script: Gatling tests finished
    
    Script->>Script: Run compare-baseline.sh (Regression check)
    Script->>Script: Open index.html in browser
    Script-->>User: 📊 Performance report ready
```

## Key Performance Testing Features

### 🏥 Health Check Validation
- **Pre-test verification** of all microservices via the orchestration scripts (`run-tests.sh` / `run-tests.ps1`)
- **Automated service discovery** through API Gateway
- **Fail-fast approach** if services are unavailable before Gatling starts
- **Automated warm-up** triggered by scripts to generate test data and initialize Kafka

### 🔥 Stress Testing Scenarios
- **Ramp-up Strategy**: Gradual load increase from zero to find breaking points
- **Mixed User Journeys**: Realistic traffic patterns (browsers, searchers, shoppers)
- **Plateau Testing**: Sustained load to test system stability

### 🛡️ Resilience Testing
- **Error Injection**: Invalid requests to test validation
- **Circuit Breaker Testing**: Trigger failure thresholds
- **Rate Limiting Validation**: API Gateway protection mechanisms
- **Concurrent Access**: High-concurrency scenarios

### 📊 Performance Monitoring
- **Real-time Metrics**: Response times, throughput, error rates
- **SLA Validation**: Automated assertion checking
- **Percentile Analysis**: P95, P99 response time tracking
- **Regression Detection**: Post-run comparison against baselines via `compare-baseline.sh`

### 🎯 Test Profiles
- **Default**: Basic functionality testing
- **Quick**: Minimal load for CI/CD pipelines
- **Heavy**: High-load testing for capacity planning
- **Resilience**: Error handling and recovery testing
- **Stress**: Breaking point identification
- **Gateway**: API Gateway pattern validation

### 📈 Reporting & Analysis
- **HTML Dashboards**: Interactive performance reports
- **Response Time Charts**: Visual performance analysis
- **Throughput Graphs**: Request rate visualization
- **Error Analysis**: Failure pattern identification
- **Trend Analysis**: Handled by CI/CD pipeline baseline comparisons