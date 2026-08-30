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
- **State Management**: Keycloak instances will cluster using Infinispan (JGroups) over Kubernetes DNS (ping).
- **Database**: The instances will point to the CloudNativePG HA cluster to ensure the persistence layer is robust.

## Consequences
- **Positive**: Identity provider can survive node failures; scales horizontally to handle login spikes.
- **Negative**: Requires careful configuration of JGroups (e.g., JDBC_PING or DNS_PING) to ensure cluster nodes discover each other; relies heavily on the database's HA capabilities.
