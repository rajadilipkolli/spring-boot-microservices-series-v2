# 🚀 Deployment Guide - Spring Boot Microservices

## 📋 Overview

This deployment folder contains everything needed to run the complete microservices stack with full observability using Docker Compose.

## 🗂️ Directory Structure

```
deployment/
├── config/                          # Configuration files for observability tools
│   ├── alert-manager/
│   │   └── config/
│   │       └── alertmanager.yml     # Email alert configuration
│   ├── grafana/
│   │   ├── provisioning/
│   │   │   ├── dashboards/          # Pre-built Grafana dashboards
│   │   │   │   ├── circuit-breaker.json
│   │   │   │   ├── JVM_Dashboard.json
│   │   │   │   ├── logs_traces_metrics.json
│   │   │   │   ├── spring-boot-statistics.json
│   │   │   │   └── spring-cloud-gateway.json
│   │   │   └── datasource.yml       # Auto-configured data sources
│   │   └── grafana.ini              # Grafana server configuration
│   ├── prometheus/
│   │   └── config/
│   │       ├── prometheus.yml       # Metrics scraping configuration
│   │       └── alert-rules.yml      # Alerting rules
│   ├── promtail/
│   │   └── promtail.yml            # Log collection configuration
│   └── tempo/
│       └── tempo.yml               # Distributed tracing configuration
├── realm-config/
│   └── retailstore-realm.json      # Keycloak realm configuration
├── docker-compose.yml              # Core services only
├── docker-compose-tools.yml        # Full stack with observability
└── manual-sample-data.sql          # Sample data for testing
```

## 🚀 Quick Start

### Option 1: Core Services Only
```bash
docker-compose -f docker-compose.yml up -d
```

### Option 2: Full Stack with Observability (Recommended)
```bash
docker-compose -f docker-compose-tools.yml up -d
```

## 🎯 Service Endpoints

### 🏢 Core Application Services
| Service                 | Port  | URL                    | Description                     |
|-------------------------|-------|------------------------|---------------------------------|
| **Retail Store WebApp** | 8080  | http://localhost:8080  | Customer-facing application     |
| **API Gateway**         | 8765  | http://localhost:8765  | Single entry point for all APIs |
| **Service Registry**    | 8761  | http://localhost:8761  | Eureka service discovery        |
| **Config Server**       | 8888  | http://localhost:8888  | Centralized configuration       |
| **Catalog Service**     | 18080 | http://localhost:18080 | Product catalog management      |
| **Inventory Service**   | 18181 | http://localhost:18181 | Stock level management          |
| **Order Service**       | 18282 | http://localhost:18282 | Order processing                |
| **Payment Service**     | 18085 | http://localhost:18085 | Payment processing              |

### 📊 Observability Stack
| Tool             | Port | URL                   | Credentials   | Purpose                    |
|------------------|------|-----------------------|---------------|----------------------------|
| **Grafana**      | 3000 | http://localhost:3000 | user/password | Dashboards & visualization |
| **Prometheus**   | 9090 | http://localhost:9090 | -             | Metrics collection         |
| **AlertManager** | 9093 | http://localhost:9093 | -             | Alert routing              |
| **Loki**         | 3100 | http://localhost:3100 | -             | Log aggregation            |
| **Tempo**        | 3110 | http://localhost:3110 | -             | Distributed tracing        |

### 🗄️ Infrastructure Services
| Service        | Port | URL                   | Credentials           |
|----------------|------|-----------------------|-----------------------|
| **PostgreSQL** | 5432 | localhost:5432        | retailuser/retailpass |
| **Keycloak**   | 9191 | http://localhost:9191 | admin/admin1234       |
| **Kafka**      | 9092 | localhost:9092        | -                     |
| **Redis**      | 6379 | localhost:6379        | -                     |

## 📊 Grafana Dashboards

### 🎛️ Pre-configured Dashboards
All dashboards are **automatically imported** when Grafana starts:

1. **🔄 Circuit Breaker Dashboard**
    - Monitor circuit breaker states
    - Track failure rates and recovery times
    - Visualize resilience patterns

2. **☕ JVM Dashboard**
    - Memory usage (heap, non-heap, metaspace)
    - Garbage collection metrics
    - Thread pool monitoring
    - CPU usage per service

3. **📊 Logs, Traces & Metrics**
    - Unified observability view
    - Correlate logs with traces
    - Service dependency mapping
    - Error rate tracking

4. **📈 Spring Boot Statistics**
    - HTTP request metrics
    - Response time percentiles
    - Throughput analysis
    - Database connection pools

5. **🌐 Spring Cloud Gateway**
    - Route-specific metrics
    - Load balancing statistics
    - Gateway performance
    - Circuit breaker integration

### 🔍 Accessing Dashboards
1. Open Grafana: http://localhost:3000
2. Login: `user` / `password`
3. Navigate: **Dashboards** → **Browse** → **Applications** folder

## 🔧 Configuration Details

### 📡 Prometheus Configuration
**File**: `config/prometheus/config/prometheus.yml`

- **Scrape Interval**: 2 seconds for real-time monitoring
- **Targets**: All microservices via API Gateway
- **Metrics Path**: `/actuator/prometheus` for Spring Boot services
- **Alert Rules**: Configured for service health and CPU usage

### 🚨 Alert Rules
**File**: `config/prometheus/config/alert-rules.yml`

1. **ServerDown Alert**
    - Triggers when service is down > 1 minute
    - Severity: Critical

2. **HighCpuUsage Alert**
    - Triggers when CPU usage > 80% for 5 minutes
    - Severity: Critical

### 📧 AlertManager Configuration
**File**: `config/alert-manager/config/alertmanager.yml`

- **Email Notifications**: Configured for Gmail SMTP
- **Repeat Interval**: 1 minute
- **Target Email**: dockertmt@gmail.com

> ⚠️ **Note**: Update email credentials before production use

#### Update AlertManager email credentials (Gmail)

When using Gmail as the SMTP provider for AlertManager, follow these steps and security considerations.

- **Prerequisite**: The Gmail account used for sending must have **2-Step Verification** enabled. App passwords require 2-Step Verification and will not be available otherwise.
- **Why App Passwords**: Google no longer supports "less secure apps" that use plain username/password for SMTP without 2FA. Create an App Password which is a 16-character string that grants SMTP access for a specific app (AlertManager).

Step-by-step: Generate a Gmail App Password

1. Open your Google Account: https://myaccount.google.com
2. Go to **Security** → **Signing in to Google** → **App passwords**.
3. Under **Select app** choose "Other (Custom name)" and enter `alertmanager`.
4. Click **Generate** and copy the 16-character password shown.
5. In `deployment/config/alert-manager/config/alertmanager.yml`, update the `auth_username` and `auth_password` fields:

```yaml
email_configs:
  - to: '<recipient@example.com>'   # keep configurable
    from: '<sender@gmail.com>'       # full Gmail address; keep configurable
    smarthost: 'smtp.gmail.com:587'  # verified Gmail SMTP host:port
    auth_username: 'sender@gmail.com' # must be the full Gmail address
    auth_password: '<16-character-app-password>'
    require_tls: true
```

Important notes:
- Use the full Gmail address for `auth_username` (for example `alerts@example.gmail.com@gmail.com`).
- The `auth_password` must be the 16-character App Password generated in Google — NOT your regular account password.
- `require_tls: true` enforces STARTTLS which Gmail requires on port 587.

Troubleshooting
- "Invalid credentials" — verify `auth_username` is the full Gmail address and `auth_password` is the exact 16-character app password (no spaces).
- "Blocked sign-in attempt" emails from Google — visit https://myaccount.google.com/security-checkup and review security alerts; sometimes sign-ins from unknown locations/devices are blocked until you confirm them.
- Ensure the account has 2-Step Verification enabled and the App Password was generated after enabling it.
- Check AlertManager logs and SMTP connectivity from the host to `smtp.gmail.com:587` (firewall or network restrictions can block outbound SMTP).

Security considerations and best practices
- Do NOT commit `auth_password` or any credentials to version control. Use environment variables or a secret manager (Docker secrets, Vault, Kubernetes Secrets) to inject credentials at runtime.
- Prefer a dedicated Gmail account for sending alerts rather than a personal account.
- Rotate App Passwords periodically (e.g., every 90 days) and revoke any unused App Passwords from the Google Account security settings.
- App Password vs OAuth: App Passwords are simpler to configure for server-to-server SMTP but provide a static secret. OAuth provides better long-term security and fine-grained scopes but is more complex to configure with AlertManager (requires token refresh flow and additional tooling). For production, evaluate OAuth for stricter requirements.
- IP restrictions & Google security: Google may flag sign-in attempts from unfamiliar IPs. If you run AlertManager in a production environment with stable IP ranges, add those to your Google account's security alerts monitoring and use a dedicated account.
- Store credentials in a secrets management system and grant minimal access. Avoid embedding them in YAML files in source control.

Credential management checklist
- [ ] Enable 2-Step Verification on the Gmail account
- [ ] Create an App Password named `alertmanager`
- [ ] Inject credentials via secrets (do not commit to repo)
- [ ] Use a dedicated Gmail account for alerts
- [ ] Schedule regular password/secret rotation


### 📋 Log Collection (Promtail)
**File**: `config/promtail/promtail.yml`

- **Auto-discovery**: Monitors Docker containers with `logging=promtail` label
- **Log Streaming**: Real-time log collection to Loki
- **Container Metadata**: Automatic labeling with container names

### 🔍 Distributed Tracing (Tempo)
**File**: `config/tempo/tempo.yml`

- **Multiple Protocols**: Supports OTLP, Jaeger, Zipkin
- **Metrics Generation**: Automatic service graphs and span metrics
- **Storage**: Local file system (suitable for development)

## 🎯 Data Sources (Auto-configured)

### 📊 Prometheus
- **URL**: http://host.docker.internal:9090
- **Purpose**: Metrics collection and alerting
- **Integration**: Linked to Tempo for trace-to-metrics correlation

### 🔍 Tempo
- **URL**: http://tempo:3100
- **Purpose**: Distributed tracing
- **Features**:
    - Trace-to-metrics correlation
    - Trace-to-logs correlation
    - Service dependency mapping

### 📋 Loki
- **URL**: http://loki:3100
- **Purpose**: Centralized logging
- **Features**:
    - Log-to-trace correlation
    - Real-time log streaming
    - Container log aggregation

## 🔄 Service Dependencies

### 🏗️ Startup Order
The docker-compose file ensures proper startup sequence:

1. **Infrastructure**: PostgreSQL, Redis, Kafka, Keycloak
2. **Core Services**: Config Server, Service Registry
3. **Observability**: Loki, Tempo, Prometheus
4. **Microservices**: Catalog, Inventory, Order, Payment
5. **Gateway & UI**: API Gateway, Retail Store WebApp
6. **Monitoring**: Grafana, AlertManager, Promtail

### 🔗 Health Checks
All services include health checks with:
- **Interval**: 15-30 seconds
- **Timeout**: 5-15 seconds
- **Retries**: 3-5 attempts
- **Start Period**: 30-90 seconds

## 💾 Resource Allocation

### 🖥️ Memory Limits
| Service         | Memory Limit | Purpose                          |
|-----------------|--------------|----------------------------------|
| Microservices   | 1050MB       | Standard Spring Boot apps        |
| Payment Service | 1400MB       | Higher due to complex processing |
| Retail WebApp   | 700MB        | Lightweight UI application       |

## 🛠️ Development Tips

### 📊 Generating Metrics
1. **Use the application**: http://localhost:8080
2. **Make API calls** through the retail store
3. **Check Prometheus targets**: http://localhost:9090/targets
4. **View service registry**: http://localhost:8761

### 🔍 Troubleshooting

#### Check Service Health
```bash
# View all container status
docker-compose -f docker-compose-tools.yml ps

# Check specific service logs
docker-compose -f docker-compose-tools.yml logs -f catalog-service

# Monitor all logs
docker-compose -f docker-compose-tools.yml logs -f
```

#### Verify Prometheus Targets
1. Open http://localhost:9090/targets
2. All targets should show "UP" status
3. If services show "DOWN", check service logs

#### Grafana Dashboard Issues
1. Verify data sources: **Configuration** → **Data Sources**
2. Check Prometheus connectivity
3. Ensure services are generating metrics

### 🧹 Cleanup Commands
```bash
# Stop all services
docker-compose -f docker-compose-tools.yml down

# Remove volumes (data loss!)
docker-compose -f docker-compose-tools.yml down -v

# Complete cleanup
docker system prune -a -f --volumes
```

## 🔐 Security Considerations

### 🛡️ Production Checklist
- [ ] Update AlertManager email credentials
- [ ] Change Grafana admin password
- [ ] Configure proper Keycloak realm
- [ ] Set up TLS/SSL certificates
- [ ] Review network security groups
- [ ] Enable authentication for Prometheus/Grafana

### 🔑 Default Credentials
| Service    | Username   | Password   |
|------------|------------|------------|
| Grafana    | user       | password   |
| Keycloak   | admin      | admin1234  |
| PostgreSQL | retailuser | retailpass |

> ⚠️ **Warning**: Change all default credentials before production deployment

## 📈 Monitoring Best Practices

### 🎯 Key Metrics to Monitor
1. **Service Health**: Up/Down status
2. **Response Times**: P50, P95, P99 percentiles
3. **Error Rates**: 4xx/5xx HTTP responses
4. **Resource Usage**: CPU, Memory, Disk
5. **Business Metrics**: Orders, Payments, Inventory

### 🚨 Alert Configuration
- **Critical**: Service down, high error rates
- **Warning**: High response times, resource usage
- **Info**: Deployment events, configuration changes

### 📊 Dashboard Organization
- **Overview**: High-level system health
- **Service-specific**: Detailed metrics per service
- **Infrastructure**: Database, messaging, caching
- **Business**: KPIs and business metrics

## 🚀 Future Improvements & Enhancements

### 📊 Additional Grafana Dashboards

#### 🏢 Business Intelligence Dashboards
- **📈 Business KPI Dashboard**
    - Order conversion rates
    - Revenue per service
    - Customer acquisition metrics
    - Product performance analytics

- **💰 Financial Metrics Dashboard**
    - Payment success/failure rates
    - Transaction volume trends
    - Revenue by payment method
    - Refund and chargeback tracking

- **👥 User Behavior Dashboard**
    - User journey analytics
    - Cart abandonment rates
    - Session duration metrics
    - Feature usage statistics

#### 🔧 Technical Enhancement Dashboards
- **🗄️ Database Performance Dashboard**
    - Connection pool utilization
    - Query execution times
    - Slow query analysis
    - Database lock monitoring

- **📡 Kafka Monitoring Dashboard**
    - Topic throughput and lag
    - Consumer group performance
    - Message processing rates
    - Partition distribution

- **🔄 API Performance Dashboard**
    - Endpoint-specific metrics
    - Rate limiting statistics
    - API versioning usage
    - Client application breakdown

### 🚨 Enhanced Alerting

#### 📈 Business Alerts
```yaml
# Additional alert rules to add
- alert: HighOrderFailureRate
  expr: rate(orders_failed_total[5m]) > 0.1
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "High order failure rate detected"

- alert: PaymentProcessingDelay
  expr: histogram_quantile(0.95, payment_processing_duration_seconds) > 30
  for: 5m
  labels:
    severity: critical
```

#### 🔧 Infrastructure Alerts
- **Database Connection Pool Exhaustion**
- **Kafka Consumer Lag Threshold**
- **Memory Leak Detection**
- **Disk Space Monitoring**
- **Network Latency Spikes**

### 📊 Advanced Monitoring Features

#### 🔍 Distributed Tracing Enhancements
- **Service Dependency Mapping**: Automatic topology discovery
- **Error Correlation**: Link errors across service boundaries
- **Performance Bottleneck Detection**: Identify slow spans
- **Custom Span Annotations**: Business context in traces

#### 📋 Log Analysis Improvements
- **Structured Logging**: JSON format with consistent fields
- **Log Correlation IDs**: Track requests across services
- **Error Pattern Detection**: Automated anomaly detection
- **Log-based Metrics**: Extract metrics from log patterns

#### 🎯 Custom Metrics
```java
// Example custom metrics to implement
@Component
public class BusinessMetrics {
    private final Counter ordersProcessed = Counter.builder("orders_processed_total")
        .description("Total orders processed")
        .tag("status", "success")
        .register(Metrics.globalRegistry);
    
    private final Timer paymentProcessingTime = Timer.builder("payment_processing_duration")
        .description("Payment processing time")
        .register(Metrics.globalRegistry);
}
```

### 🛡️ Security Monitoring

#### 🔐 Security Dashboards
- **Authentication Failures**: Failed login attempts
- **Authorization Violations**: Access denied events
- **Suspicious Activity**: Unusual request patterns
- **API Abuse Detection**: Rate limit violations

#### 🚨 Security Alerts
- **Brute Force Attack Detection**
- **Unusual Traffic Patterns**
- **Failed Authentication Spikes**
- **Privilege Escalation Attempts**

### 📱 Mobile & Real-time Features

#### 📲 Mobile Dashboard
- **Responsive Grafana Themes**
- **Mobile-optimized Panels**
- **Push Notifications Integration**
- **Offline Dashboard Sync**

#### ⚡ Real-time Monitoring
- **WebSocket-based Updates**
- **Live Event Streaming**
- **Real-time Anomaly Detection**
- **Dynamic Threshold Adjustment**

### 🔧 Infrastructure Improvements

#### 🐳 Container Monitoring
```yaml
# Additional container metrics
- job_name: 'docker-containers'
  docker_sd_configs:
    - host: unix:///var/run/docker.sock
  relabel_configs:
    - source_labels: [__meta_docker_container_name]
      target_label: container_name
```

#### ☁️ Cloud-Native Enhancements
- **Kubernetes Integration**: Pod and node monitoring
- **Service Mesh Observability**: Istio/Linkerd metrics
- **Multi-cluster Monitoring**: Federated Prometheus
- **Cost Optimization Tracking**: Resource usage vs. cost

### 📊 Data Retention & Storage

#### 🗄️ Long-term Storage
- **Prometheus Remote Storage**: InfluxDB or Cortex integration
- **Log Archival**: S3/MinIO for long-term log storage
- **Trace Sampling**: Intelligent sampling strategies
- **Data Lifecycle Management**: Automated cleanup policies

### 🎯 Performance Optimization

#### ⚡ Query Optimization
- **Recording Rules**: Pre-compute expensive queries
- **Dashboard Caching**: Reduce load on data sources
- **Metric Cardinality Control**: Prevent high-cardinality issues
- **Query Result Caching**: Improve dashboard load times

### 🔄 CI/CD Integration

#### 🚀 Deployment Monitoring
- **Deployment Success Rate**
- **Rollback Frequency**
- **Feature Flag Usage**
- **A/B Test Performance**

#### 📈 Release Quality Metrics
- **Error Rate After Deployment**
- **Performance Regression Detection**
- **User Experience Impact**
- **Canary Deployment Monitoring**

### 🎨 Visualization Enhancements

#### 📊 Advanced Panel Types
- **Heatmaps**: Response time distribution
- **Geo Maps**: User location analytics
- **Flow Diagrams**: Service interaction flows
- **Sankey Diagrams**: Data flow visualization

#### 🎭 Custom Themes
- **Dark/Light Mode Toggle**
- **Brand-specific Themes**
- **Accessibility Improvements**
- **Mobile-responsive Layouts**

## 🤝 Contributing

When modifying configurations:
1. Test changes locally first
2. Update this documentation
3. Verify all health checks pass
4. Test alert rules functionality
5. Ensure dashboards display correctly

---

**📝 Last Updated**: 25-DEC-2025
**🔧 Maintained By**: Spring Boot Microservices Team
