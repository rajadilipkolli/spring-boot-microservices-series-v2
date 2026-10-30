# Operational Runbook: High Latency and CPU Saturation

## 1. Symptoms
- **Alert Triggered**: `HighLatency` (`P99 > 500ms` for > 5m) or `HighCpuUsage` (`process_cpu_usage > 0.8` for > 5m).
- **Client Impact**: Slow web page load times on `retail-store-webapp`; delayed API Gateway responses; potential client-side timeouts.
- **Metrics**: `http_server_requests_seconds` histogram quantiles show elevated P95/P99 across one or more services.

## 2. Dashboards
- **Service Latency Overview**: `http://grafana:3000/d/http-performance` (P50, P90, P99 response times per route).
- **Tempo Distributed Tracing**: `http://grafana:3000/explore` (Trace spans with duration > 500ms).
- **JVM & Garbage Collection Dashboard**: `http://grafana:3000/d/jvm-performance` (GC pause frequency, heap utilization, thread states).
- **Infrastructure CPU/Memory Dashboard**: CPU throttling and node load averages.

## 3. Diagnosis
1. **Identify Slow Endpoints via Prometheus**:
   Query Prometheus for the highest latency endpoints:
   ```promql
   topk(5, histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri, job)))
   ```
2. **Trace the Bottleneck with Grafana Tempo**:
   - Locate slow traces for the offending URI.
   - Inspect the span waterfall:
     - Is time spent in **PostgreSQL** query execution (missing index, full table scan)?
     - Is time spent in **Redis** cache misses or network round-trips?
     - Is time spent waiting on **Downstream HTTP Calls** (e.g., `api-gateway` -> `catalog-service`)?
3. **Inspect Thread Dumps & CPU Utilization**:
   If CPU is pegged at > 80%:
   - Check if the JVM is thrashing in Garbage Collection:
     ```promql
     sum(rate(jvm_gc_pause_seconds_sum[5m])) by (job)
     ```
   - Capture a live thread dump:
     ```bash
     kubectl exec -it <pod-name> -n retailstore -- jcmd 1 Thread.print > /tmp/threaddump.txt
     ```
     *Search for threads in `BLOCKED` or `WAITING` states on synchronization monitors.*
4. **Inspect Slow Database Queries**:
   - Check PostgreSQL `pg_stat_activity` for locks or sequential scans.

## 4. Mitigation
1. **Horizontal Pod Autoscaling / Scale Out**:
   If CPU saturation is due to legitimate traffic volume:
   ```bash
   kubectl scale deployment/<service-name> -n retailstore --replicas=5
   ```
2. **Redis Cache Warm-Up & Refresh**:
   If latency is caused by a cold cache or mass cache invalidation:
   - Verify Redis cluster health and query response times.
   - Warm up frequently requested catalog pages.
3. **Tune Rate Limiting in API Gateway**:
   If excessive synthetic or scraper traffic is driving CPU load:
   - Temporarily lower the Redis rate limiter replenish rate on the offending route:
     ```yaml
     redis-rate-limiter.replenishRate: 10
     redis-rate-limiter.burstCapacity: 20
     ```
4. **Restart JVM Pods (Transient Deadlock / GC Leak)**:
   If thread dump indicates unrecoverable deadlock or thread pool exhaustion:
   ```bash
   kubectl rollout restart deployment/<service-name> -n retailstore
   ```

## 5. Escalation
- **Level 1 (On-Call SRE)**: Identify bottleneck service, execute horizontal scaling, check GC logs.
- **Level 2 (Database Administrator / Application Engineers)**: Escalate if slow queries require emergency index creation or code optimization.
- **Level 3 (Platform Architect)**: Escalate if cluster node resource limits or underlying virtual machine noisy-neighbor issues cause throttling.

## 6. Recovery Verification
1. Verify P99 latency returns within SLO (< 500ms):
   ```promql
   histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, job))
   ```
2. Confirm container CPU usage stabilizes below 70%:
   ```promql
   sum(rate(process_cpu_usage[5m])) by (job)
   ```
3. Verify Prometheus alerts `HighLatency` and `HighCpuUsage` resolve.
