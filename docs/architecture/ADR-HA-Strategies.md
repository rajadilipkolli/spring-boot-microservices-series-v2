# Architecture Decision Record: Redis High Availability

## Status
Accepted

## Context
Our architecture relies on Redis for caching and session management. A single point of failure in Redis could lead to degraded performance or failed authentication flows if session tokens are dropped.

## Decision
We will deploy **Redis Sentinel** for High Availability in production.
- **Topology**: 3 Redis nodes (1 Master, 2 Replicas) with 3 Sentinel processes.
- **Failover**: Sentinel monitors the master and automatically promotes a replica if the master fails.
- **Client Configuration**: Spring Boot services will be configured with spring.data.redis.sentinel.master and spring.data.redis.sentinel.nodes to dynamically discover the current master.

## Consequences
- **Positive**: Resilient caching layer; seamless failover without manual intervention.
- **Negative**: Increased infrastructure footprint (minimum 3 nodes); slightly more complex client configuration.

---

# Architecture Decision Record: Keycloak High Availability

## Status
Accepted

## Context
Keycloak handles identity and access management (IAM). Downtime in Keycloak prevents users from logging in and inter-service authentication.

## Decision
We will deploy **Keycloak in HA mode** using a shared external PostgreSQL database and Infinispan.
- **Topology**: 3 Keycloak replicas.
- **State Management**: Keycloak instances use embedded Infinispan with the Keycloak `kubernetes` cache stack, which selects the JGroups `DNS_PING` discovery protocol. Do not configure a second discovery protocol.
- **Discovery**: `jgroups.dns.query` resolves `keycloak-discovery.retailstore.svc.cluster.local`, a headless Service with `publishNotReadyAddresses: true` that selects every Keycloak pod.
- **Network**: Cluster DNS must be reachable on UDP/TCP 53, and Keycloak pods must be able to connect to one another on TCP 7800. Any NetworkPolicy introduced for this namespace must explicitly preserve both paths.
- **Database**: The instances will point to the CloudNativePG HA cluster to ensure the persistence layer is robust.

### Production validation
After applying `deployment/k8s/overlays/prod`, verify that the rendered and live configuration remain aligned with this decision:

```bash
kubectl kustomize deployment/k8s/overlays/prod | grep -E 'replicas: 3|KC_CACHE_STACK|keycloak-discovery'
kubectl -n retailstore rollout status deployment/keycloak --timeout=5m
kubectl -n retailstore get deployment/keycloak -o jsonpath='{.spec.replicas}{" replicas; stack="}{.spec.template.spec.containers[0].env[?(@.name=="KC_CACHE_STACK")].value}{"\n"}'
kubectl -n retailstore get endpoints keycloak-discovery
kubectl -n retailstore logs -l app=keycloak --prefix --since=10m | grep -E 'ISPN000078|ISPN000094'
```

The gate passes only when the Deployment reports 3 available replicas, the discovery Service exposes 3 pod addresses, every pod logs the `kubernetes` stack, and the latest `ISPN000094` cluster view contains all 3 members. A one-member view on each pod is a failed deployment, even if all readiness probes pass.

## Consequences
- **Positive**: Identity provider can survive node failures; scales horizontally to handle login spikes.
- **Negative**: Requires the headless-Service DNS record and pod-to-pod JGroups traffic to remain available; relies heavily on the database's HA capabilities. Keycloak deprecates the built-in `kubernetes` cache stack, so a future ADR must select and test a supported replacement before upgrading to a release that removes it.
