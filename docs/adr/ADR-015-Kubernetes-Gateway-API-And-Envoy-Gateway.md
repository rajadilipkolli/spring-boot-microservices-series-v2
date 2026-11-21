# ADR-015: Kubernetes Gateway API and Envoy Gateway Architecture

## Status
Accepted

## Context
Prior iterations of the platform used NGINX Ingress Controller for North-South edge routing into the Kubernetes cluster. The standard Kubernetes `Ingress` resource has significant limitations:
- Monolithic and vendor-specific: Advanced capabilities (rate limiting, timeouts, header routing, authentication) depend on vendor-specific annotations (`nginx.ingress.kubernetes.io/*`).
- Role coupling: Ingress tightly couples cluster operators, security teams, and application developers into a single resource file.
- Lack of multi-tenant policy inheritance: Policies cannot be declared at gateway scope and cleanly inherited or patched by environment overlays.

Furthermore, Kubernetes Gateway API (`gateway.networking.k8s.io`) has graduated to GA as the official next-generation standard for L4/L7 ingress and mesh traffic routing.

## Decision
We adopt **Kubernetes Gateway API** using **Envoy Gateway v1.3** as the North-South perimeter routing engine:

1. **North-South Edge Perimeter Architecture**:
   - Envoy Gateway (`GatewayClass: eg`) manages the perimeter `Gateway` (`retailstore-gateway`) listening on port 80 (NodePort 30080 in Kind).
   - Host-based routing is decoupled across distinct `HTTPRoute` resources:
     - `retailstore.local`: Directs web traffic to `retail-store-webapp:8080`.
     - `keycloak.local`: Directs authentication traffic to `keycloak:8080`.
     - `jobrunr.local`: Directs background processing dashboard traffic to `order-service:28282`.
     - `api.retailstore.local`: Directs API traffic to `api-gateway:8765` (Spring Cloud Gateway) across path prefixes `/catalog-service`, `/inventory-service`, `/order-service`, `/payment-service`, and the `/` catch-all for actuator, OpenAPI docs, and data generation endpoints. Forwarding preserves the path unchanged and configures a 120s `request` and `backendRequest` timeout.

2. **Perimeter vs. Application Gateway Division**:
   - **Envoy Gateway (Perimeter / Edge)**: Provides cluster-edge host and path routing, perimeter outlier detection, and edge rate limiting and authentication policies.
   - **Spring Cloud Gateway (Application Routing)**: Retains responsibility for internal application-level routing, Eureka service discovery integration, distributed circuit breaking (Resilience4j), and backend token relay.

3. **Traffic and Security Policy Governance**:
   - **Assumption 2 Policy-Merging Constraint**: Per Envoy Gateway specification, multiple policies of the same kind targeting the same hierarchy cannot simply be stacked independently without explicit merge rules. Therefore, `BackendTrafficPolicy` (`retailstore-gateway`) is defined once in `base` with TCP connect timeouts and passive health checks (`healthCheck.passive`), and overlay environments (`staging`, `prod`) customize HTTP request timeouts and local rate limiting by applying Kustomize patches directly targeting the base policy.
   - **`SecurityPolicy`**: HTTP Basic Authentication is enforced at the Envoy perimeter for `jobrunr.local` targeting the `internal-jobrunr-route`.

4. **HTTP-Only Scope**:
   - The Gateway API listener is currently configured for HTTP-only traffic. External TLS termination is identified as future work.

## Consequences

### Positive
- **Standardized Specification**: Uses portable, expressive Kubernetes Gateway API resources (`GatewayClass`, `Gateway`, `HTTPRoute`) without vendor annotation lock-in.
- **Granular Role Separation**: Network infrastructure and route attachments are managed independently.
- **Unified Policy Engine**: Envoy Gateway extension policies (`BackendTrafficPolicy`, `SecurityPolicy`) provide declarative outlier ejection, rate limiting, and edge authentication.

### Negative / Trade-offs
- Requires installing Gateway API CRDs and the Envoy Gateway controller (`envoy-gateway-system`).
- Single-policy attachment constraints require environment overlays to patch base policies rather than declare conflicting standalone policy resources.

## Verification & Compliance
- Deployment scripts (`deploy.sh`) and CI workflows (`k8s-e2e.yml`) must enforce fatal reconciliation checks for `GatewayClass`, `Gateway`, `HTTPRoute`, `BackendTrafficPolicy`, and `SecurityPolicy`.
