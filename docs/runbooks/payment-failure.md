# Operational Runbook: Payment Failure and Elevated Order Rejection Rate

## 1. Symptoms
- **Alert Triggered**: `OrderFailureRate` (`sum(rate(orders_failed_total[5m])) / sum(rate(orders_created_total[5m])) > 0.05` for > 5m).
- **Client Impact**: Customers complete checkout but receive order rejection notifications; orders transition to `REJECTED` or `ROLLBACK` status instead of `CONFIRMED`.
- **Application Metrics**: Spike in `orders_failed_total` counter in `order-service` and `payment-service`.

## 2. Dashboards
- **Business Health Dashboard**: `http://grafana:3000/d/business-metrics` (Order success vs. failure rates, order creation volume, average order value).
- **Payment Service Metrics**: `http://grafana:3000/d/payment-service` (Card authorization latency, error codes, database transaction times).
- **Distributed Traces (Tempo)**: Search for traces with tags `http.status_code=500` or `order.status=REJECTED`.

## 3. Diagnosis
1. **Identify the Failure Cause (Payment vs. Inventory)**:
   Query `order-service` database or inspect reconciliation logs:
   ```bash
   kubectl logs -n retailstore deployment/order-service --tail=200 | grep -i "reconcil"
   ```
   *Determine if orders are failing due to:*
   - **Payment Rejection**: `orderPayment.status == "REJECT"` (insufficient funds, payment gateway error).
   - **Inventory Exhaustion**: `orderStock.status == "REJECT"` (stock depleted).
   - **Technical Exception**: Network timeout, unhandled serialization error, or database failure.
2. **Inspect Payment Service Logs**:
   ```bash
   kubectl logs -n retailstore deployment/payment-service --tail=200 | grep -E "ERROR|WARN"
   ```
   *Look for external gateway connection timeouts, database constraint violations, or missing customer accounts.*
3. **Inspect Customer & Payment Records**:
   Verify customer account balance and record integrity:
   ```bash
   curl -i http://api-gateway:8765/payment-service/api/customers/1
   ```
4. **Check Dead Letter Queues**:
   Check if failed payment events landed on `orders-dlt-payment`:
   ```bash
   kubectl exec -it <kafka-pod> -n retailstore -- /opt/kafka/bin/kafka-run-class.sh \
     kafka.tools.GetOffsetShell --bootstrap-server localhost:9092 --topic orders-dlt-payment --time -1
   ```

## 4. Mitigation
1. **Circuit Breaker & Fallback Triage**:
   - If payment failures stem from external gateway timeouts, verify Resilience4j circuit breaker tripped and fallback logic executed without exhausting service threads.
2. **Service Restart / Pod Recycling**:
   - If payment service connection pools are saturated or memory is leaking:
     ```bash
     kubectl rollout restart deployment/payment-service -n retailstore
     ```
3. **Address Stock Depletion (if Inventory-Induced)**:
   - If rejections are caused by false inventory stock shortages, update catalog stock levels via administrative inventory APIs.
4. **DLQ Replay Procedure**:
   - If transient infrastructure failure caused legitimate transactions to land in `orders-dlt-payment`, follow [DLQ-Handling.md](DLQ-Handling.md) to replay messages once payment service is stabilized.

## 5. Escalation
- **Level 1 (On-Call SRE)**: Distinguish between business rejections (legitimate card declines/stockouts) vs system errors.
- **Level 2 (Payment Service Domain Engineers)**: Escalate if payment transaction logic, database schema, or third-party PSP integration is defective.
- **Level 3 (VP Engineering / Business Operations)**: Escalate if a widespread merchant processing outage impacts customer orders for > 30 minutes.

## 6. Recovery Verification
1. Verify `OrderFailureRate` metric drops below 5%:
   - PromQL: `sum(rate(orders_failed_total[5m])) / sum(rate(orders_created_total[5m])) <= 0.05`.
2. Execute end-to-end checkout test via API Gateway:
   - Place a test order for available product (e.g., `P1001`).
   - Confirm status transitions to `CONFIRMED` within 3 seconds.
3. Confirm Prometheus alert `OrderFailureRate` transitions to resolved.
