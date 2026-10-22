# 🧪 End-to-End (E2E) Test Suite

This module provides end-to-end integration tests for the **Spring Boot Microservices Series V2** platform. It validates the deployed system as a black box through the **API Gateway** and **Keycloak**.

---

## 📋 Test Coverage

1. **Service Health Checks (`ActuatorHealthE2ETest`)**:
   - Validates `/actuator/health` on `api-gateway`, `catalog-service`, `inventory-service`, `order-service`, and `payment-service`.
2. **Catalog & Inventory Flow (`CatalogAndInventoryE2ETest`)**:
   - Product creation, inventory updates, and cross-service inventory queries via catalog service (`?fetchInStock=true`).
3. **Order Fulfillment Saga (`OrderSagaFulfillmentE2ETest`)**:
   - **Happy Path**: Creates order $\to$ awaits Kafka Streams Saga transition to `CONFIRMED` $\to$ verifies customer fund deduction in payment service.
   - **Rollback (Insufficient Stock)**: Submits order exceeding stock $\to$ awaits Saga transition to `ROLLBACK`/`REJECTED`.
   - **Rollback (Insufficient Funds)**: Submits order exceeding balance $\to$ awaits Saga transition to `ROLLBACK`/`REJECTED`.
4. **Security & Identity (`KeycloakSecurityE2ETest`)**:
   - Validates OAuth2 token acquisition, realm OpenID configuration, and token exchange.
5. **Resilience & Fallbacks (`ResilienceE2ETest`)**:
   - Validates Gateway fallback routing and circuit breaker behavior.

---

## 🚀 Running the Tests

### 1. Prerequisites
Ensure the microservices stack is running (via Docker Compose or Kubernetes Kind cluster):
```bash
docker compose -f deployment/docker-compose.yml up -d
```

### 2. Execute Tests Locally
From the project root:
```bash
# Run all E2E tests
./mvnw test -pl e2e-tests -DskipE2E=false

# Or from within the e2e-tests directory
cd e2e-tests
../mvnw test
```

### 3. Execute Against Custom Host / Kubernetes Ingress
Override target URLs using system properties:
```bash
./mvnw test -pl e2e-tests -DskipE2E=false \
  -Dbase.url=http://api.retailstore.local \
  -Dkeycloak.url=http://keycloak.local
```

### 4. Configuration Properties & Environment Variables

| Property | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `base.url` | `BASE_URL` | `http://localhost:8765` | Target URL of API Gateway / Ingress |
| `keycloak.url` | `KEYCLOAK_URL` | `http://localhost:9191` | Keycloak server URL |
| `webapp.url` | `WEBAPP_URL` | `http://localhost:8080` | Retail Store WebApp URL |
| `keycloak.client.secret` | `TF_VAR_webapp_client_secret` | `demo-throwaway-oauth-secret` | Keycloak OAuth2 client secret |
| `keycloak.password` | `KEYCLOAK_RETAIL_PASSWORD` | `retail1234` | Keycloak retail user password |
| `skipE2E` | `SKIP_E2E` | `false` (in module) | Set to `true` to skip test execution |

The Bash Kubernetes runners load credentials from Kubernetes Secrets when no
environment override is set, then export `TF_VAR_webapp_client_secret` and
`KEYCLOAK_RETAIL_PASSWORD` for the test JVM. Keep credentials out of Maven command
arguments.

`deployment/k8s/run-k8s-e2e-local.sh` uses the prod overlay's local CA issuer to
issue an ingress certificate for the `.local` hosts. It creates a temporary PKCS12
trust store and passes `e2e.trustStore`, `e2e.trustStoreType`,
and `e2e.trustStorePassword` to Maven. `E2EConfig` applies that trust to
both request specifications and direct REST Assured requests, with certificate
and hostname validation enabled. Curl uses the same CA via `--cacert`; the
temporary trust files are removed on exit. Maven retains its normal repository
TLS trust. This does not install the CA into the operating system or browser
trust store.
