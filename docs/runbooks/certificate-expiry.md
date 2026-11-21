# Operational Runbook: Certificate Expiry and Renewal Failure

> **Note**: The current external Gateway perimeter is configured for **HTTP-only** routing; external TLS listener termination via Gateway API `certificateRefs` is planned for future iterations. This runbook documents diagnosis and renewal for internal/future TLS certificates and Gateway listener bindings.

## 1. Symptoms
- **Alert Triggered**: `CertificateExpiringSoon` (certificate expiration < 14 days or < 48 hours) or `CertificateRenewalFailed`.
- **Client Impact**: Web browsers and API clients display `SSL_ERROR_EXPIRED_CERTIFICATE` or `CERT_HAS_EXPIRED` when accessing TLS-enabled endpoints.
- **Kubernetes Events**: Cert-manager `Certificate` or `CertificateRequest` custom resources in `Ready: False` or `Error` state, or Gateway listeners reporting `ResolvedRefs=False` / `Programmed=False`.

## 2. Dashboards
- **Cert-Manager Dashboard**: `http://grafana:3000/d/cert-manager` (Days until expiration per certificate, renewal status, ACME order failures).
- **Gateway API Dashboard**: Status of Gateway listeners and Envoy proxy instances.

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
3. **Inspect Gateway Listener and CertificateRefs Status**:
   ```bash
   kubectl describe gateway retailstore-gateway -n retailstore
   ```
   *Inspect listener conditions: check for `Programmed=True`, `Accepted=True`, and `ResolvedRefs=True` under listener status.*
4. **Inspect Cert-Manager Controller Logs**:
   ```bash
   kubectl logs -n cert-manager deployment/cert-manager --tail=100
   ```
   *Check for DNS challenge timeout, Let's Encrypt rate-limiting, or invalid domain configuration.*
5. **Inspect ClusterIssuer / Issuer Status**:
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
   - If HTTP-01 challenge is failing: verify route configuration allows traffic to `.well-known/acme-challenge/` and that external DNS points to the cluster Gateway NodePort or external IP.
   - If DNS-01 challenge is failing: verify cloud DNS credentials and API tokens stored in the `cert-manager` namespace secret.
3. **Emergency Self-Signed / Fallback Certificate Injection**:
   If upstream CA is unavailable and expiration is imminent (< 1 hour):
   - Switch `issuerRef` to an internal self-signed `ClusterIssuer` to maintain encrypted transport while resolving upstream validation.
4. **Restart Envoy Proxy Deployment (When Needed)**:
   If the TLS secret was updated but Envoy has not reloaded the certificate in its dynamic configuration:
   ```bash
   kubectl rollout restart deployment -n envoy-gateway-system -l gateway.envoyproxy.io/owning-gateway-name=retailstore-gateway
   ```
   *(Or restart all proxy deployments in `envoy-gateway-system`)*

## 5. Escalation
- **Level 1 (On-Call SRE)**: Check certificate expiration status, inspect cert-manager events, trigger manual renewal.
- **Level 2 (Security / Infrastructure Lead)**: Escalate if domain DNS ownership validation fails or ACME rate-limits are reached.
- **Level 3 (Enterprise Network Operations)**: Escalate for external DNS registrar or commercial certificate authority contract issues.

## 6. Recovery Verification
1. Verify `kubectl get certificate -n retailstore` reports `READY: True`.
2. Inspect the expiration date of the live TLS certificate using OpenSSL (if TLS listener configured):
   ```bash
   echo | openssl s_client -connect api.retailstore.local:443 -servername api.retailstore.local 2>/dev/null | openssl x509 -noout -dates
   ```
   *Verify `notAfter` is at least 30-90 days in the future.*
3. Verify client requests connect securely without SSL warnings:
   ```bash
   curl -vk https://api.retailstore.local:443/actuator/health
   ```
4. Confirm Prometheus alert `CertificateExpiringSoon` has cleared.
