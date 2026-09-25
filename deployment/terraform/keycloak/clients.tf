# ---------------------------------------------------------------------------
# retailstore-webapp client
#
# Reproduces the confidential OIDC client from retailstore-realm.json.
# ---------------------------------------------------------------------------
resource "keycloak_openid_client" "retailstore_webapp" {
  realm_id                     = keycloak_realm.retailstore.id
  client_id                    = "retailstore-webapp"
  name                         = "retailstore-webapp"
  enabled                      = true
  access_type                  = "CONFIDENTIAL"
  client_secret                = var.webapp_client_secret
  standard_flow_enabled        = true
  implicit_flow_enabled        = false
  direct_access_grants_enabled = true
  service_accounts_enabled     = true
  frontchannel_logout_enabled  = false

  backchannel_logout_session_required        = true
  backchannel_logout_revoke_offline_sessions = false
  oauth2_device_authorization_grant_enabled  = false
  display_on_consent_screen                  = false

  valid_redirect_uris = [
    "http://localhost:8080/login/oauth2/code/keycloak",
    "https://api.retailstore.local/login/oauth2/code/keycloak",
    "https://retailstore.local/login/oauth2/code/keycloak"
  ]

  valid_post_logout_redirect_uris = [
    "http://localhost:8080/",
    "https://api.retailstore.local/",
    "https://retailstore.local/"
  ]

  web_origins = [
    "http://localhost:8080",
    "https://api.retailstore.local",
    "https://retailstore.local"
  ]
}

# ---------------------------------------------------------------------------
# Client Protocol Mappers
#
# Ensures client roles and session data (Client ID, IP address, host) are
# injected into tokens, matching the mappers defined in the realm export.
# ---------------------------------------------------------------------------

resource "keycloak_openid_user_client_role_protocol_mapper" "client_roles_mapper" {
  realm_id                    = keycloak_realm.retailstore.id
  client_id                   = keycloak_openid_client.retailstore_webapp.id
  name                        = "client roles"
  claim_name                  = "resource_access.${keycloak_openid_client.retailstore_webapp.client_id}.roles"
  multivalued                 = true
  client_id_for_role_mappings = keycloak_openid_client.retailstore_webapp.client_id
  add_to_id_token             = true
  add_to_access_token         = true
  add_to_userinfo             = false
}

resource "keycloak_openid_user_session_note_protocol_mapper" "client_id_mapper" {
  realm_id         = keycloak_realm.retailstore.id
  client_id        = keycloak_openid_client.retailstore_webapp.id
  name             = "Client ID"
  claim_name       = "clientId"
  claim_value_type = "String"
  session_note     = "clientId"
}

resource "keycloak_openid_user_session_note_protocol_mapper" "client_address_mapper" {
  realm_id         = keycloak_realm.retailstore.id
  client_id        = keycloak_openid_client.retailstore_webapp.id
  name             = "Client IP Address"
  claim_name       = "clientAddress"
  claim_value_type = "String"
  session_note     = "clientAddress"
}

resource "keycloak_openid_user_session_note_protocol_mapper" "client_host_mapper" {
  realm_id         = keycloak_realm.retailstore.id
  client_id        = keycloak_openid_client.retailstore_webapp.id
  name             = "Client Host"
  claim_name       = "clientHost"
  claim_value_type = "String"
  session_note     = "clientHost"
}
