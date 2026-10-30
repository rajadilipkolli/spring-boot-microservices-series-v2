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

### Step 3: Switch Global DNS Traffic to Secondary Region
1. Update DNS routing records (Route53 / Cloudflare) to route traffic to the secondary region Ingress IP:
   - `retailstore.local` -> `<Secondary-Ingress-IP>`
   - `api.retailstore.local` -> `<Secondary-Ingress-IP>`
   - `keycloak.local` -> `<Secondary-Ingress-IP>`
2. Lower DNS TTL to 60 seconds to ensure rapid client cutover.

### Step 4: Validate Application Endpoints
Execute automated smoke tests against the secondary region ingress:
```bash
curl -ik https://api.retailstore.local/actuator/health
curl -ik https://api.retailstore.local/catalog-service/api/catalog/products
```

## 5. Escalation
- **Incident Commander (Lead SRE / VP Engineering)**: Declares DR event, oversees regional cutover, coordinates customer communication.
- **Database Team Lead**: Validates database promotion integrity and signs off on data consistency.
- **Network / Security Architect**: Executes Global DNS switch and validates TLS certificate validity on the secondary ingress.

## 6. Recovery Verification
1. Verify global user traffic is arriving at the secondary region Ingress controller:
   - Ingress controller access logs show incoming 200 OK responses.
2. Confirm active order processing works end-to-end:
   - Place a synthetic test order through the webapp and verify payment and inventory reservations succeed.
3. Verify Prometheus and LGTM stack in secondary region are actively scraping all pods.
4. Document the actual RTO (time elapsed from DR declaration to DNS cutover) and RPO (gap between last committed transaction in primary and secondary DB).
