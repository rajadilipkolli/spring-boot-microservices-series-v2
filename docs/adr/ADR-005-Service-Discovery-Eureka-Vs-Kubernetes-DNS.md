# ADR-005: Service Discovery: Spring Cloud Netflix Eureka vs. Native Kubernetes DNS

## Status
Accepted

## Context
Microservices need to discover and communicate with downstream collaborators dynamically (e.g., `api-gateway` routing to microservices, `order-service` calling `catalog-service`, `payment-service` calling `order-service`).

In standard Spring Cloud ecosystems, Netflix Eureka (`service-registry`) has historically served as the dynamic service registry. However, when deployed on Kubernetes, Kubernetes provides its own battle-tested, native service discovery mechanism via CoreDNS and ClusterIP `Services`. Running Eureka on Kubernetes introduces duplicate registries, discovery latency (Eureka heartbeat and cache refresh intervals of 30s), and unnecessary memory overhead (~500MB RAM for the Eureka server).

## Decision
We adopt a **Dual-Mode Discovery Architecture** with a clear roadmap toward **Native Kubernetes DNS Discovery**:

1. **Local Development & Docker Compose (Eureka Mode)**:
   - Microservices continue to register with `service-registry` (`http://naming-server:8761/eureka/`) when running under the `docker` or `local` profile.
   - Spring Cloud LoadBalancer handles client-side load balancing via Eureka instance registries.

2. **Kubernetes Deployments (Hybrid / Transition Mode)**:
   - Microservices deploy with standard Kubernetes `Service` definitions providing stable DNS endpoints (`http://catalog-service:18080/catalog-service`, `http://order-service:18282/order-service`).
   - `api-gateway` supports both `lb://CATALOG-SERVICE` (Eureka-based client-side routing) and direct URL routing `http://catalog-service:18080` via environment variable toggles (`APPLICATION_CATALOG_SERVICE_URL`).
   - Downstream inter-service calls (e.g., `payment-service` -> `order-service`) use direct Kubernetes DNS URLs configured via `shared-config` ConfigMap or Spring Boot properties.

3. **Strategic Sunset Plan for Eureka on Kubernetes**:
   - For pure Kubernetes clusters, `service-registry` is phased out in favor of Spring Cloud Kubernetes (`spring-cloud-starter-kubernetes-client-loadbalancer`) or native Kubernetes DNS resolution with standard HTTP connection pooling.
   - Eliminates Eureka registration delay (warm-up interval) and removes the `service-registry` pod requirement in production overlays.

## Consequences

### Positive
- **Parity with Local Tooling**: Developers retain fast local iteration with Docker Compose and Eureka without needing a local Kubernetes cluster (Minikube / Kind).
- **Reduced Footprint on Kubernetes**: Eliminates 1 unnecessary JVM process and cuts cluster startup time by removing the 30-60s Eureka cache warm-up window.
- **Native Infrastructure Alignment**: Standard Kubernetes health probes and endpoints controllers manage traffic routing directly at the networking layer via kube-proxy and iptables/IPVS.

### Negative / Trade-offs
- Configuration divergence between profiles: Docker profile uses `eureka.client.serviceUrl.defaultZone`, while Kubernetes profile overrides target URLs to Kubernetes DNS names.
- Client-side metrics: Transitioning away from Eureka requires ensuring Micrometer metrics track outbound HTTP client calls via standard `RestClient` / `WebClient` observation filters.

## Verification & Compliance
- E2E tests and integration tests must succeed both when routing through Eureka (`lb://`) and when using explicit DNS URLs.
- In Kubernetes staging and production manifests, all downstream service URLs must point to ClusterIP service names within the `retailstore` namespace.
