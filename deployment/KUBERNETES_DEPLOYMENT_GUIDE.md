# Kubernetes Deployment Guide

This guide describes how to deploy the Spring Boot microservices on a Kubernetes cluster (e.g., Kind or Minikube), including instructions for configuring Ingress, running overlays, and validating the deployment.

## Prerequisites
- **Kubernetes Cluster**: Minikube, Kind, or Docker Desktop with Kubernetes enabled.
- **Tools**: \kubectl\, \kustomize\, \helm\ (for ingress).
- **Ingress Controller**: You must have an Ingress controller installed (e.g., NGINX Ingress Controller).

## Cluster Setup & Ingress

### Local Hostnames
To ensure the OAuth2 flow works correctly with Keycloak, you must map the local hostnames in your \/etc/hosts\ (or \C:\Windows\System32\drivers\etc\hosts\):
\\\
127.0.0.1 retailstore.local keycloak.local api.retailstore.local
\\\
*(Note: If using Minikube without \minikube tunnel\, use the \minikube ip\ instead of 127.0.0.1).*

### Install NGINX Ingress (Kind)
\\\ash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
\\\

## Deployment Instructions

### Base & Overlays
The deployment manifests are packaged using Kustomize under \deployment/k8s/\:
- **base**: Shared resources, stateful components, and service configurations.
- **dev**: Local development overlay (single replicas, NodePort/Ingress).
- **ci**: CI test overlay (deterministic hostnames, IfNotPresent image policy, minimal resources).
- **prod**: Production stub (multiple replicas, TLS).
- **observability**: Deploys OTEL LGTM stack and wires metrics/tracing.
- **autoscaling**: Deploys HorizontalPodAutoscalers for business services.

### Obtaining Images
For local clusters, you must either:
1. **Pull and Load**: Pull the \dockertmt/mmv2-*\ images from DockerHub and load them into your cluster (\kind load docker-image ...\).
2. **Build**: Build locally using Spring Boot Buildpacks and load the built images.

### Deploying the Stack
A helper script is provided to apply the \dev\ overlay and wait for readiness:
\\\ash
cd deployment/k8s
./deploy.sh
\\\

Alternatively, to run the CI overlay:
\\\ash
kubectl apply -k deployment/k8s/overlays/ci
\\\

### Teardown
To cleanly remove all resources:
\\\ash
kubectl delete namespace retailstore
\\\

## CI Workflow Validation (Local Debugging)
To debug the GitHub Actions CI workflow locally:
1. **Provision Kind**: Create a cluster exposing ports 80/443 via \extraPortMappings\.
2. **Load Images**: Run \kind load docker-image <image>\ for all services.
3. **Ingress**: Install the ingress controller as shown above.
4. **Deploy**: \kubectl apply -k deployment/k8s/overlays/ci/\
5. **Wait**: Use \kubectl rollout status\ to verify infrastructure and services.
6. **Validate**: Run \	est-em-all.sh\ with the proper HOST/PORT targeting your ingress.

### Troubleshooting
- **ImagePullBackOff**: Ensure images are loaded into Kind/Minikube or \imagePullPolicy\ is \IfNotPresent\ for local images.
- **Config-Server Ordering**: InitContainers ensure services wait for config-server. If pods crash, check config-server logs.
- **Keycloak Hostname**: If OAuth2 redirects fail, verify \OAUTH2_SERVER_URL\ and \
etailstore.local\ host mappings are resolving to your Ingress controller.

## CI Diagnostics
When a CI run fails, check the artifacts for:
- \kubectl get pods -A\ status.
- Container logs (especially \config-server\ and \pi-gateway\).
- Resource describe output to catch memory limit OOMKills.
