# Service Level Objectives (SLOs) & Error Budgets

This document defines the core Service Level Objectives (SLOs) and corresponding Service Level Indicators (SLIs) for the Retail Store application.

## 1. Availability SLO
- **Target**: 99.9% successful eligible requests over a 30-day rolling window.
- **SLI**: Successful eligible requests divided by all eligible application requests. Count 2xx and 3xx responses as successful. Count only the expected client/business outcomes 400, 401, 403, 404, 409, and 422 as successful, and only when they are emitted after the application completed validation or authorization. Exclude health checks and administration traffic from both numerator and denominator. Treat all other 4xx responses as failures unless this document explicitly adds them; in particular, 408, 425, and service-generated 429 responses are not successes. Gateway/overload responses such as 502, 503, and 504 are also failures.
- **Rate-limit SLI**: Measure service-generated 429 responses separately as `429 responses / eligible requests`; load-test exceptions must be filtered by an explicit synthetic-traffic label, not by treating every 4xx response as successful.
- **Error Budget**: 0.1% of eligible requests in the rolling window may be unsuccessful. No fixed downtime allowance is inferred from this request-based SLI.

## 2. Latency SLO
- **Target**: 99th percentile (P99) of HTTP requests must complete in < 500ms.
- **SLI**: http_server_requests_seconds bucket metrics.
- **Error Budget**: 1% of requests per month may exceed 500ms.

## 3. Order Success Rate SLO
- **Target**: 95% of initiated orders complete successfully.
- **SLI**: Ratio of orders_completed_total to orders_created_total.
- **Error Budget**: 5% of orders can fail due to legitimate inventory or payment rejections, or system errors.

## 4. Kafka Consumer Lag SLO
- **Target**: Kafka consumer lag for critical processors (order, payment, inventory) must remain < 1000 messages.
- **SLI**: kafka_consumer_group_lag metrics.
- **Action**: Alerting on this condition triggers investigations into consumer health or KEDA autoscaling limits.
