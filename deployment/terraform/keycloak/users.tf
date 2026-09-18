# ---------------------------------------------------------------------------
# Seed users
#
# Reproduces the two seed users from retailstore-realm.json:
#   • raja  (rajakolli@gmail.com)  — realm role: default-roles-retailstore
#   • retail (retailstore@gmail.com) — realm role: default-roles-retailstore
#                                       + client role: ADMIN on retailstore-webapp
# ---------------------------------------------------------------------------

# ---- raja ----

resource "keycloak_user" "raja" {
  realm_id       = keycloak_realm.retailstore.id
  username       = "raja"
  enabled        = true
  email          = "rajakolli@gmail.com"
  first_name     = "Raja Dilip"
  last_name      = "Kolli"
  email_verified = true
}

# Assign default realm role. `default-roles-retailstore` is a Keycloak built-in
# composite role created automatically; we reference it by name only via the
# keycloak_user_roles resource which accepts role objects.
data "keycloak_role" "default_roles_retailstore" {
  realm_id = keycloak_realm.retailstore.id
  name     = "default-roles-retailstore"

  depends_on = [keycloak_realm.retailstore]
}

resource "keycloak_user_roles" "raja_roles" {
  realm_id = keycloak_realm.retailstore.id
  user_id  = keycloak_user.raja.id

  role_ids = [
    data.keycloak_role.default_roles_retailstore.id,
  ]
}

# ---- retail ----

resource "keycloak_user" "retail" {
  realm_id       = keycloak_realm.retailstore.id
  username       = "retail"
  enabled        = true
  email          = "retailstore@gmail.com"
  first_name     = "Retail"
  last_name      = "Store"
  email_verified = true
}

resource "keycloak_user_roles" "retail_roles" {
  realm_id = keycloak_realm.retailstore.id
  user_id  = keycloak_user.retail.id

  role_ids = [
    data.keycloak_role.default_roles_retailstore.id,
    # Client role ADMIN — dependency graph: realm → client → client role → user role assignment
    keycloak_role.webapp_admin_role.id,
  ]
}
