# ADR: WAF Integration Strategy

## Context
As part of edge security for the retailstore application, we need to protect against common web exploits (e.g., OWASP Top 10) such as SQL injection, Cross-Site Scripting (XSS), and malicious bots. 

## Decision
We will adopt a layered Web Application Firewall (WAF) approach:

1.  **Ingress-based WAF (ModSecurity)**: We will enable ModSecurity with the OWASP Core Rule Set (CRS) on the Nginx Ingress Controller. This provides a baseline layer of protection inside the Kubernetes cluster.
    - Implementation: Use `nginx.ingress.kubernetes.io/enable-modsecurity: "true"` and `nginx.ingress.kubernetes.io/modsecurity-snippet` annotations.
2.  **Cloud Provider Edge WAF**: For production deployments exposed to the internet, we recommend terminating traffic at a Cloud WAF (e.g., AWS WAF, Cloudflare, or Azure Front Door) before it reaches the Kubernetes Ingress. This provides DDoS protection and offloads deep packet inspection from the cluster ingress nodes.

## Consequences
- ModSecurity introduces a slight latency overhead and increased memory footprint on the ingress controller pods.
- We must monitor for false positives and tune the CRS rules (e.g., by adding exclusion rules for specific APIs that might trigger false alarms).

