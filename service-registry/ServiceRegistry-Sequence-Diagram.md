# Service Registry (Eureka Server) - Sequence Diagram

```mermaid
%%{init: {'theme':'dark', 'themeVariables': { 'primaryColor': '#ff6b6b', 'primaryTextColor': '#fff', 'primaryBorderColor': '#ff6b6b', 'lineColor': '#fff', 'secondaryColor': '#384454', 'tertiaryColor': '#384454', 'background': '#0f0f23', 'mainBkg': '#1e1e3f', 'secondBkg': '#2d2d5a', 'tertiaryBkg': '#3c3c75'}}}%%

sequenceDiagram
    participant Admin as 👤 Administrator
    participant Docker as 🐳 Docker
    participant ConfigServer as 📁 Config Server
    participant EurekaServer as 🏢 Eureka Server
    participant CatalogService as 📚 Catalog Service
    participant InventoryService as 📦 Inventory Service
    participant OrderService as 🛍️ Order Service
    participant PaymentService as 💳 Payment Service
    participant APIGateway as 🌐 API Gateway
    participant WebApp as 🛒 Web Application
    participant Dashboard as 📊 Eureka Dashboard
    participant HealthCheck as 🏥 Health Monitor

    Note over Admin, HealthCheck: 🏢 Eureka Service Registry - Service Discovery & Registration

    %% Service Registry Startup
    rect rgb(40, 40, 80)
        Note over Admin, ConfigServer: 🚀 Service Registry Initialization
        
        Admin->>Docker: docker-compose up service-registry
        Docker->>EurekaServer: Start Eureka Server (Port 8761)
        
        EurekaServer->>EurekaServer: Load application.properties
        Note over EurekaServer: eureka.client.registerWithEureka=false<br/>eureka.client.fetchRegistry=false
        
        EurekaServer->>ConfigServer: GET /naming-server/default
        Note over EurekaServer, ConfigServer: spring.config.import=configserver:http://dev-usr:dev-pass@localhost:8888/
        
        alt Config Server Available
            ConfigServer-->>EurekaServer: 200 OK + Configuration properties
            EurekaServer->>EurekaServer: Merge external configuration
        else Config Server Unavailable
            EurekaServer->>EurekaServer: Use local application.properties
            Note over EurekaServer: optional:configserver - graceful fallback
        end
        
        EurekaServer->>EurekaServer: Initialize Eureka registry
        EurekaServer->>EurekaServer: Start embedded Tomcat (Port 8761)
        EurekaServer->>EurekaServer: Enable virtual threads
        Note over EurekaServer: spring.threads.virtual.enabled=true
        
        EurekaServer-->>Docker: ✅ Eureka Server started successfully
        Docker-->>Admin: Service registry ready at http://localhost:8761
    end

    %% Service Registration Phase
    rect rgb(20, 60, 20)
        Note over CatalogService, PaymentService: 📋 Microservice Registration
        
        par Catalog Service Registration
            CatalogService->>EurekaServer: POST /eureka/apps/CATALOG-SERVICE
            Note over CatalogService, EurekaServer: Registration payload:<br/>- instanceId: catalog-service:18080<br/>- hostName: localhost<br/>- port: 18080<br/>- healthCheckUrl: /actuator/health
            EurekaServer->>EurekaServer: Validate registration data
            EurekaServer->>EurekaServer: Store service instance
            EurekaServer-->>CatalogService: 204 No Content (Registration successful)
            
            loop Heartbeat (every 30s)
                CatalogService->>EurekaServer: PUT /eureka/apps/CATALOG-SERVICE/{instanceId}
                EurekaServer->>EurekaServer: Update last heartbeat timestamp
                EurekaServer-->>CatalogService: 200 OK (Heartbeat acknowledged)
            end
            
        and Inventory Service Registration
            InventoryService->>EurekaServer: POST /eureka/apps/INVENTORY-SERVICE
            Note over InventoryService, EurekaServer: Registration payload:<br/>- instanceId: inventory-service:18181<br/>- hostName: localhost<br/>- port: 18181<br/>- healthCheckUrl: /actuator/health
            EurekaServer->>EurekaServer: Register inventory service
            EurekaServer-->>InventoryService: 204 No Content
            
            loop Heartbeat (every 30s)
                InventoryService->>EurekaServer: PUT /eureka/apps/INVENTORY-SERVICE/{instanceId}
                EurekaServer-->>InventoryService: 200 OK
            end
            
        and Order Service Registration
            OrderService->>EurekaServer: POST /eureka/apps/ORDER-SERVICE
            Note over OrderService, EurekaServer: Registration payload:<br/>- instanceId: order-service:18282<br/>- hostName: localhost<br/>- port: 18282<br/>- healthCheckUrl: /actuator/health
            EurekaServer->>EurekaServer: Register order service
            EurekaServer-->>OrderService: 204 No Content
            
            loop Heartbeat (every 30s)
                OrderService->>EurekaServer: PUT /eureka/apps/ORDER-SERVICE/{instanceId}
                EurekaServer-->>OrderService: 200 OK
            end
            
        and Payment Service Registration
            PaymentService->>EurekaServer: POST /eureka/apps/PAYMENT-SERVICE
            Note over PaymentService, EurekaServer: Registration payload:<br/>- instanceId: payment-service:18085<br/>- hostName: localhost<br/>- port: 18085<br/>- healthCheckUrl: /actuator/health
            EurekaServer->>EurekaServer: Register payment service
            EurekaServer-->>PaymentService: 204 No Content
            
            loop Heartbeat (every 30s)
                PaymentService->>EurekaServer: PUT /eureka/apps/PAYMENT-SERVICE/{instanceId}
                EurekaServer-->>PaymentService: 200 OK
            end
        end
    end

    %% API Gateway Registration & Service Discovery
    rect rgb(60, 20, 60)
        Note over APIGateway, EurekaServer: 🌐 API Gateway Integration
        
        APIGateway->>EurekaServer: POST /eureka/apps/API-GATEWAY
        Note over APIGateway, EurekaServer: Registration payload:<br/>- instanceId: api-gateway:8765<br/>- hostName: localhost<br/>- port: 8765
        EurekaServer-->>APIGateway: 204 No Content (Gateway registered)
        
        APIGateway->>EurekaServer: GET /eureka/apps
        Note over APIGateway, EurekaServer: Fetch all registered services for routing
        EurekaServer-->>APIGateway: 200 OK + Service registry (XML/JSON)
        
        APIGateway->>APIGateway: Build dynamic routing table
        Note over APIGateway: Route mapping:<br/>- /CATALOG-SERVICE/** → catalog-service:18080<br/>- /INVENTORY-SERVICE/** → inventory-service:18181<br/>- /ORDER-SERVICE/** → order-service:18282<br/>- /PAYMENT-SERVICE/** → payment-service:18085
        
        loop Registry Refresh (every 30s)
            APIGateway->>EurekaServer: GET /eureka/apps/delta
            EurekaServer-->>APIGateway: 200 OK + Registry changes
            APIGateway->>APIGateway: Update routing table with changes
        end
    end

    %% Web Application Service Discovery
    rect rgb(20, 20, 60)
        Note over WebApp, EurekaServer: 🛒 Web Application Integration
        
        WebApp->>EurekaServer: POST /eureka/apps/RETAIL-STORE-WEB
        EurekaServer-->>WebApp: 204 No Content (Web app registered)
        
        WebApp->>EurekaServer: GET /eureka/apps/API-GATEWAY
        EurekaServer-->>WebApp: 200 OK + API Gateway instances
        
        WebApp->>WebApp: Configure API Gateway endpoint
        Note over WebApp: Discovered gateway: http://localhost:8765
        
        loop Service Health Monitoring
            WebApp->>EurekaServer: GET /eureka/apps/API-GATEWAY
            EurekaServer-->>WebApp: 200 OK + Gateway health status
        end
    end

    %% Service Discovery Operations
    rect rgb(80, 40, 20)
        Note over CatalogService, PaymentService: 🔍 Inter-Service Communication
        
        OrderService->>EurekaServer: GET /eureka/apps/CATALOG-SERVICE
        EurekaServer-->>OrderService: 200 OK + Catalog service instances
        
        OrderService->>OrderService: Select healthy instance (load balancing)
        OrderService->>CatalogService: GET /api/catalog/productCode/{code}
        CatalogService-->>OrderService: 200 OK + Product details
        
        OrderService->>EurekaServer: GET /eureka/apps/INVENTORY-SERVICE
        EurekaServer-->>OrderService: 200 OK + Inventory service instances
        
        OrderService->>InventoryService: GET /api/inventory/{code}
        InventoryService-->>OrderService: 200 OK + Stock information
        
        OrderService->>EurekaServer: GET /eureka/apps/PAYMENT-SERVICE
        EurekaServer-->>OrderService: 200 OK + Payment service instances
        
        OrderService->>PaymentService: POST /api/payments
        PaymentService-->>OrderService: 201 Created + Payment confirmation
    end

    %% Health Monitoring & Self-Preservation
    rect rgb(60, 60, 20)
        Note over EurekaServer, HealthCheck: 🏥 Health Monitoring & Self-Preservation
        
        EurekaServer->>EurekaServer: Monitor heartbeat intervals
        Note over EurekaServer: Expected heartbeat every 30s<br/>Eviction threshold: 90s
        
        alt Service Healthy
            CatalogService->>EurekaServer: PUT /eureka/apps/CATALOG-SERVICE/{instanceId}
            EurekaServer->>EurekaServer: Update heartbeat timestamp
            EurekaServer-->>CatalogService: 200 OK
            
        else Missed Heartbeats
            EurekaServer->>EurekaServer: Detect missed heartbeats (>90s)
            EurekaServer->>EurekaServer: Mark instance as DOWN
            
            alt Self-Preservation Mode
                EurekaServer->>EurekaServer: Calculate renewal threshold (85%)
                EurekaServer->>EurekaServer: Enable self-preservation mode
                Note over EurekaServer: Protect against network partitions<br/>Keep instances in registry
                
            else Normal Operation
                EurekaServer->>EurekaServer: Evict unhealthy instances
                EurekaServer->>EurekaServer: Notify other services of changes
                
                APIGateway->>EurekaServer: GET /eureka/apps/delta
                EurekaServer-->>APIGateway: 200 OK + Instance removal
                APIGateway->>APIGateway: Remove unhealthy routes
            end
        end
        
        HealthCheck->>EurekaServer: GET /actuator/health
        EurekaServer-->>HealthCheck: 200 OK + Registry health status
        
        HealthCheck->>EurekaServer: GET /actuator/metrics
        EurekaServer-->>HealthCheck: 200 OK + Registry metrics
        Note over HealthCheck: Metrics:<br/>- eureka.server.registry.size<br/>- eureka.server.renewals<br/>- eureka.server.evictions
    end

    %% Dashboard & Administration
    rect rgb(40, 80, 40)
        Note over Admin, Dashboard: 📊 Eureka Dashboard & Administration
        
        Admin->>Dashboard: Open http://localhost:8761
        Dashboard->>EurekaServer: GET /
        EurekaServer-->>Dashboard: 200 OK + Eureka dashboard HTML
        
        Dashboard->>EurekaServer: GET /eureka/apps
        EurekaServer-->>Dashboard: 200 OK + All registered services
        
        Dashboard->>Dashboard: Render service registry view
        Note over Dashboard: Display:<br/>- Registered services count<br/>- Instance status (UP/DOWN)<br/>- Last heartbeat times<br/>- Self-preservation status
        
        Admin->>Dashboard: View service details
        Dashboard->>EurekaServer: GET /eureka/apps/{serviceName}
        EurekaServer-->>Dashboard: 200 OK + Service instances
        
        Dashboard->>Dashboard: Show instance details
        Note over Dashboard: Instance info:<br/>- Host and port<br/>- Health check URL<br/>- Registration timestamp<br/>- Metadata
        
        Admin->>Dashboard: Monitor registry health
        Dashboard->>EurekaServer: GET /actuator/health
        EurekaServer-->>Dashboard: 200 OK + Health indicators
        
        Dashboard->>Dashboard: Display health status
        Note over Dashboard: Health indicators:<br/>- Eureka server status<br/>- Registry size<br/>- Renewal rate<br/>- Self-preservation mode
    end

    %% Service Deregistration
    rect rgb(80, 20, 20)
        Note over CatalogService, EurekaServer: 🔄 Graceful Service Shutdown
        
        Admin->>CatalogService: SIGTERM (Graceful shutdown)
        CatalogService->>CatalogService: Initiate shutdown hooks
        
        CatalogService->>EurekaServer: DELETE /eureka/apps/CATALOG-SERVICE/{instanceId}
        EurekaServer->>EurekaServer: Remove service instance
        EurekaServer-->>CatalogService: 200 OK (Deregistration successful)
        
        EurekaServer->>EurekaServer: Notify registry change
        
        APIGateway->>EurekaServer: GET /eureka/apps/delta
        EurekaServer-->>APIGateway: 200 OK + Service removal
        APIGateway->>APIGateway: Remove routes to deregistered service
        
        CatalogService->>CatalogService: Complete shutdown
        CatalogService-->>Admin: Service stopped gracefully
    end

    %% Error Handling & Recovery
    rect rgb(80, 40, 40)
        Note over EurekaServer, APIGateway: ⚠️ Error Handling & Recovery Scenarios
        
        alt Network Partition
            CatalogService->>EurekaServer: PUT /eureka/apps/CATALOG-SERVICE/{instanceId}
            Note over CatalogService, EurekaServer: Network timeout/failure
            EurekaServer->>EurekaServer: Missed heartbeat detected
            
            EurekaServer->>EurekaServer: Check renewal threshold
            alt Below Threshold (Self-Preservation)
                EurekaServer->>EurekaServer: Enter self-preservation mode
                Note over EurekaServer: Keep all instances in registry<br/>Prevent mass evictions
                
                Dashboard->>EurekaServer: GET /
                EurekaServer-->>Dashboard: Show self-preservation warning
                
            else Above Threshold (Normal)
                EurekaServer->>EurekaServer: Evict unhealthy instances
                EurekaServer->>APIGateway: Notify service removal
                APIGateway->>APIGateway: Update routing table
            end
            
        else Service Crash
            CatalogService->>CatalogService: Unexpected shutdown (no deregistration)
            EurekaServer->>EurekaServer: Detect missed heartbeats
            EurekaServer->>EurekaServer: Wait for eviction timeout (90s)
            EurekaServer->>EurekaServer: Remove crashed instance
            
            APIGateway->>CatalogService: Route request to crashed service
            CatalogService-->>APIGateway: Connection refused
            APIGateway->>EurekaServer: GET /eureka/apps/CATALOG-SERVICE
            EurekaServer-->>APIGateway: 200 OK + Updated instance list
            APIGateway->>APIGateway: Retry with healthy instance
            
        else Registry Overload
            EurekaServer->>EurekaServer: High CPU/Memory usage
            EurekaServer->>EurekaServer: Enable rate limiting
            
            CatalogService->>EurekaServer: PUT /eureka/apps/CATALOG-SERVICE/{instanceId}
            EurekaServer-->>CatalogService: 503 Service Unavailable
            CatalogService->>CatalogService: Retry with exponential backoff
        end
    end

    %% Performance Optimization
    Note over Admin, HealthCheck: ⚡ Performance Optimization Features
    
    EurekaServer->>EurekaServer: Enable Caffeine cache
    Note over EurekaServer: com.github.ben-manes.caffeine<br/>Improved registry performance
    
    EurekaServer->>EurekaServer: Configure peer node timeout
    Note over EurekaServer: eureka.server.peer-node-read-timeout-ms=300
    
    EurekaServer->>EurekaServer: Use IP addresses for registration
    Note over EurekaServer: eureka.instance.preferIpAddress=true
    
    %% Final Status
    Note over Admin, HealthCheck: ✅ Service Registry Operational
    Admin->>Admin: 📊 Monitor service health via dashboard
    Admin->>Admin: 🔍 Track service registrations/deregistrations
    Admin->>Admin: ⚡ Ensure high availability and performance
```

## Key Service Registry Features

### 🏢 Eureka Server Configuration
- **Standalone Mode**: `registerWithEureka=false`, `fetchRegistry=false`
- **Config Server Integration**: External configuration with fallback
- **Virtual Threads**: Enhanced performance with `spring.threads.virtual.enabled=true`
- **Caffeine Cache**: Improved registry performance and memory usage

### 📋 Service Registration & Discovery
- **Automatic Registration**: Services register on startup with instance metadata
- **Heartbeat Mechanism**: 30-second intervals to maintain service health
- **Dynamic Discovery**: Real-time service lookup for inter-service communication
- **Load Balancing**: Multiple instance support with client-side load balancing

### 🏥 Health Monitoring & Self-Preservation
- **Health Checks**: Continuous monitoring of service availability
- **Self-Preservation Mode**: Protection against network partitions (85% threshold)
- **Graceful Eviction**: 90-second timeout before removing unhealthy instances
- **Registry Metrics**: Comprehensive monitoring via Actuator endpoints

### 🌐 API Gateway Integration
- **Dynamic Routing**: Automatic route discovery and configuration
- **Registry Refresh**: Delta updates every 30 seconds for efficiency
- **Failover Support**: Automatic routing to healthy service instances
- **Service Mesh**: Central point for service-to-service communication

### 📊 Dashboard & Administration
- **Web Interface**: Real-time view of registered services at http://localhost:8761
- **Service Details**: Instance status, health, and metadata visualization
- **Registry Health**: Self-preservation status and renewal rate monitoring
- **Administrative Actions**: Manual service management and troubleshooting

### ⚠️ Resilience Patterns
- **Network Partition Tolerance**: Self-preservation mode during network issues
- **Graceful Degradation**: Fallback to cached registry during outages
- **Circuit Breaker Integration**: Coordinated failure handling with API Gateway
- **Retry Logic**: Exponential backoff for registration and heartbeat failures