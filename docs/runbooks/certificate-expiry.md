# Operational Runbook: Certificate Expiry and Renewal Failure

> The production Gateway uses the `retailstore-tls` Secret for its HTTPS listener. The `.local` hostnames require a private CA; use public DNS names for public ACME validation.

## 1. Symptoms
- **Alert Triggered**: `CertificateExpiringSoon` (certificate expiration < 14 days or < 48 hours) or `CertificateRenewalFailed`.
- **Client Impact**: Web browsers and API clients display `SSL_ERROR_EXPIRED_CERTIFICATE` or `CERT_HAS_EXPIRED` when accessing TLS-enabled endpoints.
- **Kubernetes Events**: Cert-manager `Certificate` or `CertificateRequest` custom resources in `Ready: False` or `Error` state, or Gateway listeners reporting `ResolvedRefs=False` / `Programmed=False`.

## 2. Dashboards
- **Cert-Manager Dashboard**: `http://grafana:3000/d/cert-manager` (Days until expiration per certificate, renewal status, ACME order failures).
- **Gateway API Dashboard**: Status of Gateway listeners and Envoy proxy instances.

## 3. Diagnosis

If cert-manager is installed, check whether a `Certificate` in `retailstore` has `spec.secretName: retailstore-tls`. If none manages the Secret, use the manual Gateway rotation in section 4. Cert-manager resource checks only apply to certificates it manages.

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
1. **Renew the certificate using its provisioning method**:
   - **Cert-manager managed:** Renew the `Certificate` that owns the Secret:
     ```bash
     kubectl cert-manager renew <cert-name> -n retailstore
     ```
   - **Manually provisioned Gateway Secret:** Obtain a replacement certificate and full chain from your trusted CA, along with the matching private key. Include every configured Gateway hostname in the certificate SANs: `retailstore.local`, `api.retailstore.local`, `keycloak.local`, and `jobrunr.local` by default. Use your organization's CA for the `.local` names. Update the existing Secret in place from Bash:
     ```bash
     (
       set -o pipefail
       kubectl -n retailstore create secret tls retailstore-tls \
         --cert=fullchain.pem --key=privkey.pem --dry-run=client -o yaml |
         kubectl apply -f -
     )
     ```
     Verify that the Gateway actually serves the replacement using section 6.

2. **Fix Failing HTTP-01 / DNS-01 ACME Challenge**:
   - If HTTP-01 is failing: verify that public DNS points to a load balancer or forwarding address and that public TCP port 80 reaches the Gateway HTTP listener (for example, by forwarding it to NodePort 30080). DNS does not specify a port. Verify that the Gateway serves `/.well-known/acme-challenge/` through the challenge route. If you cannot expose public port 80, use DNS-01.
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
1. For cert-manager managed certificates only, verify `kubectl get certificate -n retailstore` reports `READY: True` (e.g. for `keycloak-internal-tls`).
2. Inspect the expiration date of the live TLS certificate using OpenSSL against the relevant TLS endpoint (when configured):
   - For internal Keycloak TLS:
     ```bash
     kubectl run openssl-check --rm -i --image=alpine/openssl -n retailstore --restart=Never -- \
       s_client -connect keycloak:8443 -servername keycloak 2>/dev/null | openssl x509 -noout -dates
     ```
   - For the production Gateway HTTPS listener, verify the trusted chain and hostname, then compare the live leaf certificate's SHA-256 fingerprint with the replacement. Use the CA-issued `fullchain.pem` for a manual rotation, or save the renewed Secret's public `tls.crt` as `fullchain.pem` for a cert-manager renewal. Use the CA trust bundle in `ca-bundle.pem`. If DNS does not point at the Gateway you updated, replace the `-connect` value with that Gateway's reachable address and port; keep `-servername` and `-verify_hostname` set to the route hostname.
     ```bash
     (
       set -e -o pipefail
       expected=$(openssl x509 -in fullchain.pem -noout -sha256 -fingerprint)
       served=$(openssl s_client -connect api.retailstore.local:443 \
         -servername api.retailstore.local -verify_hostname api.retailstore.local \
         -verify_return_error -CAfile ca-bundle.pem </dev/null | openssl x509 -outform PEM)
       actual=$(openssl x509 -noout -sha256 -fingerprint <<<"$served")
       test "$actual" = "$expected" || { echo "Gateway is not serving the replacement certificate" >&2; exit 1; }
       openssl x509 -noout -dates <<<"$served"
     )
     ```
     Confirm the command succeeds and the new `notAfter` date matches the expected renewal window.
3. Verify client requests connect securely without SSL warnings against the verified TLS endpoint:
   - For internal Keycloak HTTPS:
     ```bash
     kubectl run curl-check --rm -i --image=curlimages/curl -n retailstore --restart=Never -- \
       curl -fsS --cacert /etc/x509/ca/tls.crt https://keycloak:8443/health/ready
     ```
   - For the production Gateway HTTPS listener, use curl's `--resolve` option if DNS does not point at the Gateway you updated:
     ```bash
     curl -fsS --cacert ca-bundle.pem https://api.retailstore.local:443/actuator/health
     ```
4. Confirm Prometheus alert `CertificateExpiringSoon` has cleared.
