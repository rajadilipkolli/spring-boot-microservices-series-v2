# ---------------------------------------------------------------------------
# Keycloak connection
# ---------------------------------------------------------------------------

variable "keycloak_url" {
  type        = string
  description = <<-EOT
    Base URL for the Keycloak server (no trailing slash).
    Typical values:
      prod runner : https://keycloak:8443
      local compose: http://localhost:9191  (or http://keycloak:9191 inside Docker)
      CI          : http://keycloak:8080
  EOT
  default     = "https://keycloak:8443"
}

variable "keycloak_root_ca_certificate" {
  type        = string
  description = "PEM-encoded CA certificate used to verify the production Keycloak HTTPS endpoint."
  default     = ""
}

variable "keycloak_admin_username" {
  type        = string
  description = <<-EOT
    Admin username for the master realm.
    Must match the value in the `keycloak-admin-credentials` Secret key KEYCLOAK_ADMIN.
  EOT
  default     = "admin"
}

variable "keycloak_admin_password" {
  type        = string
  sensitive   = true
  description = <<-EOT
    Admin password for the master realm.
    Must match the value in the `keycloak-admin-credentials` Secret key KEYCLOAK_ADMIN_PASSWORD.
    Exposed to the runner container via TF_VAR_keycloak_admin_password.
  EOT
}

# ---------------------------------------------------------------------------
# retailstore-webapp client secret
# ---------------------------------------------------------------------------

variable "webapp_client_secret" {
  type        = string
  sensitive   = true
  description = <<-EOT
    OAuth2 client secret for the `retailstore-webapp` confidential client.
    MUST match the value bound to the `webapp-oauth2-credentials` Secret key
    OAUTH2_CLIENT_SECRET, which is what the Spring Boot webapp reads at runtime.
    Changing this variable without updating that Secret will break authentication.
    Exposed to the runner container via TF_VAR_webapp_client_secret.
  EOT
}
