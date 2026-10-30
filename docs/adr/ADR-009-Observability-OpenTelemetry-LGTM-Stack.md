# ADR-009: Observability Architecture: OpenTelemetry, LGTM Stack, and Trace-Context Propagation

## Status
Accepted

## Context
A distributed microservices architecture handling asynchronous event processing across HTTP and Kafka boundaries requires end-to-end observability across the three telemetry pillars:
1. **Metrics**: Real-time throughput, latency distributions, error rates, and JVM runtime statistics.
2. **Logs**: Structured application and audit logs searchable across all services and pods.
3. **Traces**: Distributed execution paths tracking request lifecycle across `api-gateway`, domain microservices, and asynchronous Kafka topics.

Prior approaches utilized fragmented logging, ad-hoc Zipkin tracing, and unstandardized metric labels, making cross-system root cause analysis difficult and error-prone.

## Decision
We standardize on the **OpenTelemetry (OTel)** standard and the **Grafana LGTM Stack** (Loki, Grafana, Tempo, Mimir/Prometheus):

1. **Telemetry Collector & Pipeline Architecture**:
   - Microservices export telemetry via OpenTelemetry Java Agent or Micrometer Observation to an OpenTelemetry Collector (`otel-collector:4318` via OTLP/HTTP or OTLP/gRPC).
   - The OpenTelemetry Collector batches, enriches, and fans out data:
     - **Traces** -> Grafana Tempo
     - **Logs** -> Grafana Loki
     - **Metrics** -> Prometheus / Grafana Mimir

2. **Standardized Resource Attributes**:
   - Every service container injects four standard OpenTelemetry resource attributes via `OTEL_RESOURCE_ATTRIBUTES`:
     - `service.name`: Canonical service identifier (e.g., `order-service`, `catalog-service`).
     - `service.version`: Build version / image tag (e.g., `0.0.1-SNAPSHOT`).
     - `service.namespace`: Application namespace (`retail-store` / `retailstore`).
     - `deployment.environment`: Target environment (`local`, `dev`, `staging`, `prod`, `kubernetes`).

3. **Context Propagation and Distributed Tracing**:
   - W3C Trace Context (`traceparent`, `tracestate`) is standard for all inter-service HTTP headers.
   - `ObservabilityWebFilter` in `api-gateway` ensures every incoming request receives an `X-Trace-Id` and `X-Correlation-Id`, injected into the response and downstream request headers.
   - For Kafka messaging, Spring Kafka automatically propagates `traceparent` within Kafka record headers, connecting HTTP producers with asynchronous consumers in the same distributed trace graph.

4. **Log-Trace Correlation**:
   - Logback configuration utilizes MDC (Mapped Diagnostic Context) to include `trace_id` and `span_id` in every structured JSON log line.
   - In Grafana, developers can click directly from a log line into the corresponding Tempo trace or view Prometheus metrics associated with that specific execution.

5. **Business Metrics Integration**:
   - Micrometer counters (`orders_created`, `orders_completed`, `orders_failed`) and observation timers (`orderService`, `inventoryService`) are automatically translated into Prometheus metrics (`orders_created_total`, etc.) enabling SLO alerting and business health dashboards.

## Consequences

### Positive
- **Vendor-Neutral Telemetry**: OpenTelemetry API decouples application code from any specific observability vendor backend.
- **Single-Pane-of-Glass Visibility**: Grafana unifies metrics, logs, and distributed traces with seamless drill-down.
- **Asynchronous Trace Continuity**: Distributed tracing follows transactions across Kafka queues into background workers and database transactions.

### Negative / Trade-offs
- Collector Resource Overhead: Running the LGTM stack requires additional memory and CPU in staging and production clusters.
- Trace Sampling: Full 100% trace capture at high volumes generates substantial storage load; tail-based or probabilistic sampling (e.g., 10% standard, 100% errors) is required under heavy load.

## Verification & Compliance
- The `GatewayObservabilityE2ETest` validates that every request to the gateway returns non-empty `X-Trace-Id` and `X-Correlation-Id` headers.
- Prometheus scrape targets must report 100% healthy up status across all microservice `/actuator/prometheus` endpoints.
