# ---------------------------------------------------------------------------
# Dockerfile — Terraform Keycloak realm runner
#
# Copies the terraform/keycloak module into the image.
# Runs as a non-root user (UID 65532) to satisfy the Kubernetes Pod Security
# `restricted` standard (runAsNonRoot: true).
#
# Build context: repo root (or deployment/)
#   docker build -f deployment/terraform/Dockerfile deployment/terraform/
# ---------------------------------------------------------------------------
FROM hashicorp/terraform:1.9

# Install wget for the readiness healthcheck in entrypoint.sh.
# The hashicorp/terraform image is Alpine-based.
RUN apk add --no-cache wget

# Copy the Terraform module.
COPY keycloak/ /terraform/

# Copy the entrypoint script.
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Create a writable .terraform directory inside the module directory.
# The Kubernetes Job mounts an emptyDir at /terraform/.terraform so Terraform
# can write provider binaries and state-backend metadata at runtime.
# Pre-creating the directory here allows the mount to work on a read-only root.
RUN mkdir -p /terraform/.terraform

# Switch to a non-root user.
# UID 65532 matches the distroless `nonroot` convention used across this repo.
USER 65532:65532

WORKDIR /terraform

ENTRYPOINT ["/entrypoint.sh"]
