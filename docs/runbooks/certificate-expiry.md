# Operational Runbook: Certificate Expiry and Renewal Failure

## 1. Symptoms
- **Alert Triggered**: `CertificateExpiringSoon` (certificate expiration < 14 days or < 48 hours) or `CertificateRenewalFailed`.
- **Client Impact**: Web browsers and API clients display `SSL_ERROR_EXPIRED_CERTIFICATE` or `CERT_HAS_EXPIRED` when accessing `retailstore.local`, `api.retailstore.local`, or `keycloak.local`.
- **Kubernetes Events**: Cert-manager `Certificate` or `CertificateRequest` custom resources in `Ready: False` or `Error` state.

## 2. Dashboards
- **Cert-Manager Dashboard**: `http://grafana:3000/d/cert-manager` (Days until expiration per certificate, renewal status, ACME order failures).
- **Ingress TLS Dashboard**: Status of TLS secret mounts and ingress controller SSL termination.

## 3. Diagnosis
1. **List all cluster certificates and check expiration dates**:
   ```bash
   kubectl get certificates -A
   kubectl get certificaterequests -A
   ```
   *Identify certificates where `READY` is `False` or expiration is imminent.*
2. **Describe the failing certificate resource**:
   ```bash
   kubectl describe certificate <cert-name> -n retailstore
   ```
   *Look at `Status.Conditions` and `Events` (e.g., `Failed to order certificate`, `ACME challenge failed`).*
3. **Inspect Cert-Manager Controller Logs**:
   ```bash
   kubectl logs -n cert-manager deployment/cert-manager --tail=100
   ```
   *Check for DNS challenge timeout, Let's Encrypt rate-limiting, or invalid ingress domain configuration.*
4. **Inspect ClusterIssuer / Issuer Status**:
   ```bash
   kubectl describe clusterissuer letsencrypt-prod
   ```

## 4. Mitigation
1. **Trigger Manual Certificate Re-Issuance**:
   Force cert-manager to re-attempt certificate issuance immediately:
   ```bash
   # Using cmctl / kubectl cert-manager plugin:
   kubectl cert-manager renew <cert-name> -n retailstore
   ```
2. **Fix Failing HTTP-01 / DNS-01 ACME Challenge**:
   - If HTTP-01 challenge is failing: verify ingress routing allows traffic to `.well-known/acme-challenge/` and that external DNS points to the cluster Ingress IP.
   - If DNS-01 challenge is failing: verify cloud DNS credentials and API tokens stored in the `cert-manager` namespace secret.
3. **Emergency Self-Signed / Fallback Certificate Injection**:
   If upstream CA is unavailable and expiration is imminent (< 1 hour):
   - Switch `issuerRef` to an internal self-signed `ClusterIssuer` to maintain encrypted transport while resolving upstream validation.
4. **Restart Ingress Controller**:
   If the secret was updated but the Ingress controller has not reloaded the certificate:
   ```bash
   kubectl rollout restart deployment/ingress-nginx-controller -n ingress-nginx
   ```

## 5. Escalation
- **Level 1 (On-Call SRE)**: Check certificate expiration status, inspect cert-manager events, trigger manual renewal.
- **Level 2 (Security / Infrastructure Lead)**: Escalate if domain DNS ownership validation fails or ACME rate-limits are reached.
- **Level 3 (Enterprise Network Operations)**: Escalate for external DNS registrar or commercial certificate authority contract issues.

## 6. Recovery Verification
1. Verify `kubectl get certificate -n retailstore` reports `READY: True`.
2. Inspect the expiration date of the live TLS certificate using OpenSSL:
   ```bash
   echo | openssl s_client -connect api.retailstore.local:443 -servername api.retailstore.local 2>/dev/null | openssl x509 -noout -dates
   ```
   *Verify `notAfter` is at least 30-90 days in the future.*
3. Verify client requests connect securely without SSL warnings:
   ```bash
   curl -vk https://api.retailstore.local:443/actuator/health
   ```
4. Confirm Prometheus alert `CertificateExpiringSoon` has cleared.
