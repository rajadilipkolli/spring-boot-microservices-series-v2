# Service Level Objectives (SLOs) & Error Budgets

This document defines the core Service Level Objectives (SLOs) and corresponding Service Level Indicators (SLIs) for the Retail Store application.

## 1. Availability SLO
- **Target**: 99.9% uptime over a 30-day rolling window.
- **SLI**: Ratio of successful HTTP requests (2xx, 3xx, 4xx) to total HTTP requests.
- **Error Budget**: 43 minutes of downtime per month.

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
