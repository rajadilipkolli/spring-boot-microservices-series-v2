# 🔄 Hybrid Development Setup Guide

This guide documents the hybrid development approach for running the Spring Boot Microservices project, where infrastructure services run in Docker while the retail webapp runs locally for optimal development experience.

## 🎯 Overview

**Hybrid Mode** combines the best of both worlds:
- **Infrastructure in Docker**: Databases, message brokers, and supporting services
- **Application locally**: Retail webapp for faster development cycles

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    HYBRID SETUP                            │
├─────────────────────────────────────────────────────────────┤
│  🖥️  LOCAL DEVELOPMENT                                      │
│  ├── 🛒 Retail Store WebApp (localhost:8080)               │
│  └── 🔧 IDE/Development Tools                              │
├─────────────────────────────────────────────────────────────┤
│  🐳 DOCKER INFRASTRUCTURE                                  │
│  ├── 🗄️ PostgreSQL (localhost:5432)                        │
│  ├── 🔴 Redis (localhost:6379)                             │
│  ├── 📡 Kafka (localhost:9092)                             │
│  ├── 🔐 Keycloak (localhost:9191)                          │
│  ├── 🔍 Zipkin (localhost:9411)                            │
│  ├── 📁 Config Server (localhost:8888)                     │
│  ├── 🏢 Service Registry (localhost:8761)                  │
│  ├── 🌐 API Gateway (localhost:8765)                       │
│  └── 🏪 Business Services (18080, 18181, 18282, 18085)     │
└─────────────────────────────────────────────────────────────┘
```

## 🚀 Setup Steps

### 1. Start Infrastructure Services

```bash
# Navigate to deployment directory
cd deployment

# Start all infrastructure services
docker-compose -f docker-compose.yml up -d --remove-orphans
```

### 2. Verify Services are Running

```bash
# Check all containers are healthy
docker ps

# Key services to verify:
# - keycloak (localhost:9191)
# - api-gateway (localhost:8765)
# - service-registry (localhost:8761)
```

### 3. Stop Retail WebApp Container

```bash
# Stop only the retail webapp container (keep infrastructure running)
docker-compose -f deployment/docker-compose.yml stop retailstore-webapp
```

### 4. Build Retail WebApp Locally

```bash
# Navigate to retail webapp directory
cd retail-store-webapp

# Build the application
./mvnw clean compile
```

### 5. Configure Local Development

The retail webapp is already configured for hybrid mode in `application.properties`:

```properties
# API Gateway connection
app.services.api-gateway-url=http://localhost:8765

# OAuth2 configuration for local development
spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:9191/realms/retailstore
spring.security.oauth2.client.registration.keycloak.redirect-uri=http://localhost:8080/login/oauth2/code/keycloak
```

### 6. Run Retail WebApp Locally

```bash
# Navigate to retail webapp directory
cd retail-store-webapp

# Run the application
./mvnw spring-boot:run

# Running it needs below environment variables as well
cmd /c "set KEYCLOAK_ADMINUSERNAME=admin && set KEYCLOAK_ADMINPASSWORD=admin1234 && mvnw.cmd spring-boot:run"

# Or run from IDE
```

## 🔐 Authentication Setup

### Keycloak Configuration

The project includes pre-configured Keycloak settings that work seamlessly in hybrid mode:

**Admin Access:**
- URL: http://localhost:9191/admin
- Username: `admin`
- Password: `admin1234`

**Application Users:**
- Username: `retail` / Password: `retail1234`
- Username: `raja` / Password: *[Reset via admin console]*

### OAuth2 Flow Resolution

**Problem Solved:** Hostname mismatch between Docker internal networking and browser access.

**Solution:** Keycloak configured with proper hostname settings in `docker-compose.yml`:

```yaml
keycloak:
  environment:
    KC_HOSTNAME: localhost
    KC_HOSTNAME_PORT: 9191
    KC_HOSTNAME_STRICT: false
    KC_HOSTNAME_STRICT_HTTPS: false
```

## 🔧 Key Configuration Changes

### Docker Compose Modifications

```yaml
# Keycloak hostname configuration for hybrid mode
keycloak:
  environment:
    KC_HOSTNAME: localhost  # Critical for OAuth2 redirects
    KC_HOSTNAME_PORT: 9191
    KC_HOSTNAME_STRICT: false
```

### Application Properties (Already Configured)

```properties
# Ensures webapp connects to Docker services via localhost
app.services.api-gateway-url=http://localhost:8765
spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:9191/realms/retailstore
```

## 🎯 Benefits of Hybrid Mode

| Aspect | Benefit |
|--------|---------|
| **Development Speed** | Instant code changes without container rebuilds |
| **Debugging** | Full IDE debugging capabilities |
| **Resource Usage** | Lower memory footprint for development |
| **OAuth2 Flow** | Proper browser-based authentication |
| **Hot Reload** | Spring Boot DevTools support |

## 🔍 Troubleshooting

### Common Issues

**OAuth2 Redirect Errors:**
- Ensure Keycloak hostname is set to `localhost`
- Verify redirect URIs match exactly

**Service Discovery Issues:**
- Check API Gateway is accessible at localhost:8765
- Verify Service Registry shows all services

**Database Connection:**
- Ensure PostgreSQL is running in Docker
- Check port 5432 is not blocked

### Verification Commands

```bash
# Check Keycloak is accessible
curl http://localhost:9191/realms/retailstore/.well-known/openid_configuration

# Verify API Gateway
curl http://localhost:8765/actuator/health

# Check Service Registry
curl http://localhost:8761/eureka/apps
```

## 🚦 Service Status Endpoints

| Service | Health Check | Admin Interface |
|---------|-------------|-----------------|
| **Keycloak** | http://localhost:9191/health | http://localhost:9191/admin |
| **API Gateway** | http://localhost:8765/actuator/health | - |
| **Service Registry** | http://localhost:8761/actuator/health | http://localhost:8761 |
| **Config Server** | http://localhost:8888/actuator/health | - |

## 📝 Development Workflow

1. **Start Infrastructure**: `docker-compose up -d`
2. **Stop Retail Container**: `docker-compose stop retail-store-webapp`
3. **Build WebApp**: `cd retail-store-webapp && ./mvnw clean compile`
4. **Verify Services**: Check health endpoints
5. **Run WebApp Locally**: `./mvnw spring-boot:run`
6. **Develop**: Make changes with hot reload
7. **Test**: Access http://localhost:8080
8. **Debug**: Use IDE debugging tools

## 🛑 Stopping Services

```bash
# Stop only Docker infrastructure (keep local webapp running)
docker-compose -f deployment/docker-compose.yml down

# Stop local webapp
# Ctrl+C in terminal or stop from IDE
```

## 💡 Pro Tips

- **Port Conflicts**: Ensure ports 8080, 8765, 9191 are available
- **User Management**: Reset passwords via Keycloak admin console
- **Service Dependencies**: Wait for all Docker services to be healthy before starting webapp
- **Configuration Changes**: Restart webapp after changing OAuth2 settings

---

*This hybrid setup provides the optimal development experience while maintaining production-like infrastructure services.*