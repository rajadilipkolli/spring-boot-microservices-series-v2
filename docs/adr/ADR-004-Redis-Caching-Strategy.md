# ADR-004: Redis Caching and Distributed Rate Limiting Strategy

## Status
Accepted

## Context
The platform requires distributed, low-latency in-memory data structures for two primary concerns:
1. **API Gateway Rate Limiting**: The Spring Cloud Gateway enforces distributed request rate limits per client IP or authenticated user identity using the token-bucket algorithm (`RedisRateLimiter`).
2. **Application-Level Caching**: `catalog-service` serves high-volume catalog search and product detail requests that are read-heavy, making in-memory caching essential to shield PostgreSQL from repeated reads.

Prior to standardization, cache failure behaviors were unspecified, risking total system outage if Redis became unreachable.

## Decision
We establish a standardized Redis architecture, caching topology, and failure-handling policy:

1. **Deployment Architecture**:
   - **Dev / CI**: Standalone Redis container with persistence disabled or limited to ephemeral RDB snapshots.
   - **Staging / Prod**: Redis Cluster or Redis Sentinel deployment providing automatic master failover with at least 1 replica per master node.
   - **Eviction Policy**: `maxmemory-policy: allkeys-lru` with memory limits strictly capped to prevent container OOM-kills.

2. **Distributed Rate Limiting (`api-gateway`)**:
   - Implemented via Spring Cloud Gateway's built-in `RequestRateLimiter` filter powered by Reactive Redis (`ReactiveRedisTemplate`).
   - Token-bucket algorithm with configurable `replenishRate` and `burstCapacity` per route.
   - **Failure Policy: Fail-Open**: If the Redis instance fails or network connectivity drops, the gateway circuit breaker or rate-limiter fallback allows traffic to pass rather than rejecting legitimate client traffic with 500/503 errors.

3. **Catalog Data Caching (`catalog-service`)**:
   - Implemented via Spring Cache abstraction with Redis as the cache manager (`RedisCacheManager`).
   - Standard cache keys: `products::byCode::{productCode}` and `products::paged::{pageNo}_{pageSize}_{sort}`.
   - **Time-to-Live (TTL)**: Configured with a default TTL of 10 minutes (`spring.cache.redis.time-to-live: 600000`) and cache null-values disabled to avoid memory pollution from non-existent lookups.
   - **Cache Eviction**: Automatic eviction triggered on product creation, update, or inventory adjustment events via `@CacheEvict`.

4. **Resilience & Timeout Policies**:
   - Connection timeout: 1000ms; Command read timeout: 500ms.
   - Lettuce client with connection pooling and automated reconnection enabled.

## Consequences

### Positive
- **Gateway Scalability**: Token bucket state is shared across all gateway replicas, preventing individual clients from overwhelming backend services.
- **Sub-Millisecond Read Latency**: Cached catalog reads bypass database round-trips, achieving P99 latencies under 20ms.
- **Graceful Degradation**: The fail-open rate limiting strategy ensures that an infrastructure failure in Redis does not cascade into a customer-facing outage.

### Negative / Trade-offs
- Cache Invalidation Complexity: Eventual consistency window exists between database updates and cache invalidation if an eviction event fails.
- Memory Pressure: Unbounded caching requires strict LRU eviction policies and alerts when Redis memory usage exceeds 80%.

## Verification & Compliance
- Rate limiting unit and integration tests (`GatewayRedisRateLimiterTest`) must verify token bucket enforcement and rate limit headers (`X-RateLimit-Remaining`, `X-RateLimit-Burst-Capacity`).
- Simulated Redis connection failure tests must confirm gateway requests continue to flow without returning HTTP 500.
