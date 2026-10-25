# ---------------------------------------------------------------------------
# Keycloak provider (keycloak/keycloak) — authenticates against the master
# realm using admin-cli which supports the Resource Owner Password Credentials
# grant without requiring a client secret.
#
# The empty default base_path is intentional for Quarkus-based Keycloak 26.8.1.
# ---------------------------------------------------------------------------
provider "keycloak" {
  url       = var.keycloak_url
  client_id = "admin-cli"
  username  = var.keycloak_admin_username
  password  = var.keycloak_admin_password

  # Production supplies the internal CA so the runner can verify Keycloak's
  # HTTPS certificate. Local development may leave it unset while overriding
  # keycloak_url with its loopback HTTP endpoint.
  root_ca_certificate      = var.keycloak_root_ca_certificate != "" ? var.keycloak_root_ca_certificate : null
  tls_insecure_skip_verify = false
}
