# ---------------------------------------------------------------------------
# Keycloak provider — authenticates against the master realm using admin-cli
# which supports the Resource Owner Password Credentials grant without
# requiring a client secret.
# ---------------------------------------------------------------------------
provider "keycloak" {
  url       = var.keycloak_url
  client_id = "admin-cli"
  username  = var.keycloak_admin_username
  password  = var.keycloak_admin_password

  # The provider reaches the Keycloak Admin REST API.  All communication is
  # over plain HTTP inside the cluster (TLS is terminated at the Ingress).
  tls_insecure_skip_verify = false
}
