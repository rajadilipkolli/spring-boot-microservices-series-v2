# ---------------------------------------------------------------------------
# Keycloak connection
# ---------------------------------------------------------------------------

variable "keycloak_url" {
  type        = string
  description = <<-EOT
    Base URL for the Keycloak server (no trailing slash).
    Typical values:
      in-cluster  : http://keycloak:8080
      local compose: http://localhost:9191  (or http://keycloak:9191 inside Docker)
      CI          : http://keycloak:8080
  EOT
  default     = "http://keycloak:8080"
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

# ---------------------------------------------------------------------------
# Seed user passwords
# ---------------------------------------------------------------------------

variable "raja_password" {
  type        = string
  sensitive   = true
  description = <<-EOT
    Password for the seed user `raja`.
    Exposed to the runner container via TF_VAR_raja_password.
  EOT
}

variable "retail_password" {
  type        = string
  sensitive   = true
  description = <<-EOT
    Password for the seed user `retail`.
    Exposed to the runner container via TF_VAR_retail_password.
  EOT
}
