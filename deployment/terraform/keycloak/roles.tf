# ---------------------------------------------------------------------------
# Realm role: user
#
# Reproduces the custom realm role from retailstore-realm.json.
# The built-in realm roles (offline_access, uma_authorization,
# default-roles-retailstore) are created automatically by Keycloak.
# ---------------------------------------------------------------------------
resource "keycloak_role" "user_realm_role" {
  realm_id    = keycloak_realm.retailstore.id
  name        = "user"
  description = "User role"
}

# ---------------------------------------------------------------------------
# Client role: ADMIN on retailstore-webapp
#
# Reproduces the client role from retailstore-realm.json
# (id: e00cc8c4-e170-4faf-bc5e-6cee3cebc874).
# Depends on the client resource so Terraform builds the correct graph:
#   realm → client → client role
# ---------------------------------------------------------------------------
resource "keycloak_role" "webapp_admin_role" {
  realm_id    = keycloak_realm.retailstore.id
  client_id   = keycloak_openid_client.retailstore_webapp.id
  name        = "ADMIN"
  description = "retail store Administrator"
}
