# Operational Runbook: Service Down (Microservice Unavailability)

## 1. Symptoms
- **Alert Triggered**: `ServerDown` (`up < 1` for > 1m) or `HighErrorRate` (5xx error rate > 1% for > 5m).
- **Client Impact**: Clients receive HTTP 502 Bad Gateway, 503 Service Unavailable, or 504 Gateway Timeout from `api-gateway`.
- **Kubernetes State**: Pods in `CrashLoopBackOff`, `OOMKilled`, `Error`, or `Terminating` states; readiness probes failing.

## 2. Dashboards
- **Grafana Platform Overview**: `http://grafana:3000/d/microservices-overview` (Status, Restarts, Request Rate).
- **Kubernetes Cluster Monitor**: `http://grafana:3000/d/k8s-pod-overview` (CPU/Memory utilization per pod).
- **API Gateway Dashboard**: `http://grafana:3000/d/gateway-traffic` (Route success rate, circuit breaker states).
- **Loki Logs Explorer**: `{namespace="retailstore", app="<affected-service>"}`.

## 3. Diagnosis
1. **Identify the affected service and pods**:
   ```bash
   kubectl get pods -n retailstore -l app=<service-name>
   ```
2. **Check recent pod events and termination reasons**:
   ```bash
   kubectl describe pod <pod-name> -n retailstore
   ```
   *Look for `Last State: Terminated` with reason `OOMKilled` (exit code 137) or application exit.*
3. **Inspect application logs for exceptions**:
   ```bash
   kubectl logs <pod-name> -n retailstore --previous --tail=100
   ```
   *Check for fatal startup errors: `ApplicationContext` failure, database connection timeout, Kafka bootstrap broker unreachable, or out of memory.*
4. **Check node health**:
   ```bash
   kubectl get nodes
   kubectl describe node <node-hosting-pod>
   ```

## 4. Mitigation
1. **Immediate Restart / Pod Recycling**:
   If the pod is locked in a deadlock or transient state:
   ```bash
   kubectl rollout restart deployment/<service-name> -n retailstore
   ```
2. **Memory or CPU Exhaustion (OOMKilled)**:
   If the container exceeded memory limits, temporarily patch the deployment memory limit:
   ```bash
   kubectl set resources deployment/<service-name> -n retailstore --limits=memory=2048Mi --requests=memory=1024Mi
   ```
3. **Upstream Infrastructure Blockage**:
   - If PostgreSQL is down, follow [database-failure.md](database-failure.md).
   - If Kafka is unreachable, follow [kafka-outage.md](kafka-outage.md).
   - If Config Server is unreachable, verify `config-server` status and connectivity.
4. **Rollback if Caused by Recent Release**:
   If the crash started immediately following a deployment, execute an immediate rollback:
   ```bash
   kubectl rollout undo deployment/<service-name> -n retailstore
   ```

## 5. Escalation
- **Level 1 (On-Call SRE)**: Acknowledge alert within 5 minutes. Execute triage and mitigation steps above.
- **Level 2 (Service Team Lead)**: Escalate if the service cannot be restored within 15 minutes or root cause is an application bug.
- **Level 3 (Platform / Cloud Architect)**: Escalate if node-level hardware failures, storage volume detachment, or cluster-wide networking issues are present.

## 6. Recovery Verification
1. Verify all pods are in `Running` state and passing readiness probes:
   ```bash
   kubectl get pods -n retailstore -l app=<service-name>
   ```
2. Check the actuator health endpoint via API Gateway:
   ```bash
   curl -i http://api-gateway:8765/actuator/health
   curl -i http://api-gateway:8765/<service-name>/actuator/health/readiness
   ```
3. Confirm Prometheus alert `ServerDown` has auto-resolved:
   - Alert status in Alertmanager transitions to `resolved`.
   - Error rate on Grafana dashboard drops below 0.1%.
