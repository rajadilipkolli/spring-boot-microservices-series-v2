# Operational Runbook: Disaster Recovery and Regional Failover

## 1. Symptoms
- **Context**: Catastrophic regional cloud provider outage, total loss of primary Kubernetes cluster, or sustained catastrophic network failure affecting the primary region.
- **Criteria for DR Invocation**:
  - Primary region completely unreachable for > 15 minutes with no estimated time of restoration.
  - Incident Commander and Executive Leadership officially declare Disaster Recovery.
- **Targets**: RPO < 15 minutes, RTO < 30 minutes.

## 2. Dashboards
- **Global Traffic Manager (GSLB) Console**: Route53 / Cloudflare DNS routing status, health check pass/fail states.
- **Secondary Region Grafana Console**: `http://dr-grafana:3000` (Secondary cluster status, CNPG replica sync state).
- **Object Storage Replication Monitor**: S3 / GCS cross-region replication latency and WAL archive progression.

## 3. Diagnosis & Pre-Failover Checklist
1. **Confirm Primary Region Unavailability**:
   - Verify health checks fail from multiple external synthetic monitoring vantage points.
   - Confirm cloud provider regional status dashboard reports widespread infrastructure outage.
2. **Verify Secondary Cluster Infrastructure State**:
   ```bash
   kubectl get nodes --context secondary-region
   kubectl get namespaces --context secondary-region
   ```
3. **Check CloudNativePG Standby Replication Lag**:
   In the secondary region:
   ```bash
   kubectl cnpg status postgresql --context secondary-region -n retailstore
   ```
   *Verify replication lag is within the 15-minute RPO threshold.*
4. **Check Kafka MirrorMaker 2 State**:
   - Verify topics in secondary Kafka cluster contain recent mirrored records.

## 4. Mitigation & Failover Execution

### Step 1: Promote Secondary Database Cluster to Primary
Promote the CloudNativePG replica cluster in the secondary region to become the active read-write primary:
```bash
kubectl cnpg promote postgresql --context secondary-region -n retailstore
```
*Wait for cluster status to transition to `Primary` and all instances to accept read-write connections.*

### Step 2: Scale Stateless Microservices in Secondary Region
Scale all application microservices from standby (0 or 1 replica) to full production scale (3 replicas each):
```bash
kubectl scale deployment/api-gateway --replicas=3 --context secondary-region -n retailstore
kubectl scale deployment/catalog-service --replicas=3 --context secondary-region -n retailstore
kubectl scale deployment/inventory-service --replicas=3 --context secondary-region -n retailstore
kubectl scale deployment/order-service --replicas=3 --context secondary-region -n retailstore
kubectl scale deployment/payment-service --replicas=3 --context secondary-region -n retailstore
kubectl scale deployment/retail-store-webapp --replicas=3 --context secondary-region -n retailstore
kubectl scale deployment/keycloak --replicas=3 --context secondary-region -n retailstore
```
*Monitor rollout until all pods are `Running` and `Ready`:*
```bash
kubectl rollout status deployment/api-gateway --context secondary-region -n retailstore
```

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:716be880f111ea088b71aa0642e68e61316fb0f8037ce9ba0bea6200edccddff -->

### Step 3: Switch Global DNS Traffic to Secondary Region

1. From the repository root, verify the secondary Gateway is programmed, all four application HTTPRoutes are accepted with resolved references, and both policies (including JobRunr's SecurityPolicy) are accepted. This runs the same checks as `deploy.sh` without modifying the cluster. Stop if any check fails.

   ```bash
   KUBE_CONTEXT=secondary-region ./deployment/k8s/verify-gateway.sh prod
   ```

2. Ensure the secondary webapp resolves the Keycloak issuer to the secondary edge, using internal DNS if needed. Before updating public DNS, configure the secondary load balancer or forwarding rule to map TCP port 80 to NodePort 30080 and TCP port 443 to NodePort 30443.

   From a client network that trusts the Gateway's CA, set `SECONDARY_LB_IP` to the secondary load balancer IP. Check that port 80 returns an HTTPS redirect, then verify that the HTTPS health response is JSON with a top-level Spring Boot status of `UP`. Stop the cutover if either check fails.

   ```bash
   curl -q -fsSI --noproxy '*' --resolve "api.retailstore.local:80:${SECONDARY_LB_IP}" http://api.retailstore.local/actuator/health
   (
     set -o pipefail
     curl -q -fsS --noproxy '*' --resolve "api.retailstore.local:443:${SECONDARY_LB_IP}" \
       https://api.retailstore.local/actuator/health |
       jq -es 'length == 1 and .[0].status == "UP"'
   )
   ```

3. Verify that the secondary JobRunr dashboard rejects an unauthenticated request with HTTP 401. Use `--resolve` as shown to reach the secondary address while keeping `jobrunr.local` in the request and TLS handshake. You can omit it if that hostname already resolves to the secondary Gateway. Stop the cutover if the request succeeds or fails to return HTTP 401.

   ```bash
   (
     set -e
     status=$(curl -q -sS --noproxy '*' -o /dev/null -w '%{http_code}' \
       --resolve "jobrunr.local:443:${SECONDARY_LB_IP}" https://jobrunr.local/dashboard)
     test "$status" = 401 || { echo "Expected JobRunr HTTP 401, got $status" >&2; exit 1; }
   )
   ```

4. Only after all checks pass, lower the DNS TTL to 60 seconds ahead of cutover if possible. Point `retailstore.local`, `api.retailstore.local`, `keycloak.local`, and `jobrunr.local` to the verified secondary load balancer address.

<!-- /coderabbit-ai-generated:v1 -->

### Step 4: Validate Application Endpoints

Run smoke tests against the secondary region Gateway after DNS cutover:

```bash
curl -fsS https://api.retailstore.local/actuator/health
curl -fsS https://api.retailstore.local/catalog-service/api/catalog/products
curl -fsS https://retailstore.local/
```

## 5. Escalation
- **Incident Commander (Lead SRE / VP Engineering)**: Declares DR event, oversees regional cutover, coordinates customer communication.
- **Database Team Lead**: Validates database promotion integrity and signs off on data consistency.
- **Network / Security Architect**: Executes Global DNS switch and validates Gateway configuration and route reconciliation on the secondary cluster.

## 6. Recovery Verification
1. Verify global user traffic is arriving at the secondary region Gateway:
   - Envoy Gateway proxy access logs show incoming 200 OK responses.
2. Confirm active order processing works end-to-end:
   - Place a synthetic test order through the webapp and verify payment and inventory reservations succeed.
3. Verify Prometheus and LGTM stack in secondary region are actively scraping all pods.
4. Document the actual RTO (time elapsed from DR declaration to DNS cutover) and RPO (gap between last committed transaction in primary and secondary DB).
