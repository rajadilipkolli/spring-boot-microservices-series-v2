# ADR-014: Service Mesh and Mutual TLS (mTLS) Zero-Trust Network Evaluation

## Status
Accepted

## Context
As the microservices platform scales, zero-trust network security mandates that:
1. All inter-service East-West traffic within the Kubernetes cluster must be encrypted in transit via mutual TLS (mTLS).
2. Service-to-service authentication and authorization must be cryptographically verified, preventing unauthorized pods from spoofing caller identity.
3. Fine-grained traffic shifting (e.g., canary splitting at 1% increments) and layer-7 observability must operate transparently.

Two primary approaches exist:
- **Approach A: Application-Level mTLS & Spring Security**: Configuring SSL contexts, keystores, and truststores within each JVM container.
- **Approach B: Service Mesh (Istio / Linkerd / Cilium eBPF Mesh)**: Offloading TLS termination, proxying, and identity management to a transparent infrastructure layer.

## Decision
We adopt **Approach B (Service Mesh)** with a phased evaluation and adoption path:

1. **Current Baseline: Ingress TLS + Kubernetes NetworkPolicies**:
   - Ingress controller terminates external North-South TLS via cert-manager.
   - East-West container communication is strictly isolated using declarative Kubernetes `NetworkPolicies` (`default-deny-all`, `allow-apps-to-apps`, `allow-apps-to-infrastructure`).
   - Plaintext inter-service HTTP is permitted only within the isolated private overlay network bounded by network policies.

2. **Target Service Mesh Selection: Cilium Service Mesh (eBPF) or Linkerd**:
   - We reject heavy sidecar-based meshes (e.g., legacy Istio with Envoy sidecars on every pod) because adding an Envoy container to all 8 JVM microservices incurs substantial memory overhead (~50-100MB per pod) and introduces sidecar lifecycle race conditions with Spring Boot startup.
   - We select **Cilium Service Mesh with eBPF** or **Linkerd with lightweight Rust proxies**:
     - **eBPF-Powered mTLS (Cilium)**: Provides transparent kernel-level mutual authentication and encryption (via WireGuard/IPsec) without injecting sidecar containers into application pods.
     - Zero code modifications required in Spring Boot services.
     - Negligible CPU/memory overhead and zero impact on JVM container boot times.

3. **Zero-Trust Identity & Authorization Policies**:
   - SPIFFE/SPIRE-compatible workload identities assigned to Kubernetes ServiceAccounts.
   - `CiliumNetworkPolicy` or `AuthorizationPolicy` enforcing that only `api-gateway` and `retail-store-webapp` can initiate HTTP connections to backend domain services.

## Consequences

### Positive
- **Transparent Zero-Trust Encryption**: East-West traffic is encrypted without touching Spring Boot code, Java keystores, or certificates.
- **No Sidecar Bloat**: Node-level eBPF avoids injecting additional containers into application pods.
- **Cryptographic Service Identity**: Replaces IP-based security with cryptographically verifiable service account identities.

### Negative / Trade-offs
- Cluster Infrastructure Requirement: Requires Kubernetes CNI replacement (Cilium) or mesh control plane installation.
- Debugging Complexity: Troubleshooting network drops requires eBPF tooling (`cilium monitor`) rather than simple `tcpdump`.

## Verification & Compliance
- Security audits must verify that East-West packets captured inside the cluster network fabric are fully encrypted.
- Network policy penetration tests must confirm that unauthorized pods in the same cluster cannot establish connections to database or microservice ports.
