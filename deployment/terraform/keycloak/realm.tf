# ---------------------------------------------------------------------------
# retailstore realm
# ---------------------------------------------------------------------------
resource "keycloak_realm" "retailstore" {
  realm   = "retailstore"
  enabled = true

  access_token_lifespan                   = "300s"
  access_token_lifespan_for_implicit_flow = "900s"
  access_code_lifespan                    = "60s"
  access_code_lifespan_user_action        = "300s"
  access_code_lifespan_login              = "1800s"

  action_token_generated_by_admin_lifespan = "43200s"
  action_token_generated_by_user_lifespan  = "300s"

  sso_session_idle_timeout       = "1800s"
  sso_session_max_lifespan       = "36000s"
  offline_session_idle_timeout   = "2592000s"
  offline_session_max_lifespan_enabled = false

  # Explicitly define ALL duration fields to avoid provider parsing bugs
  sso_session_idle_timeout_remember_me = "0s"
  sso_session_max_lifespan_remember_me = "0s"
  client_session_idle_timeout          = "0s"
  client_session_max_lifespan          = "0s"
  oauth2_device_code_lifespan          = "600s"
  oauth2_device_polling_interval       = 5
  offline_session_max_lifespan         = "5184000s"

  ssl_required               = "external"
  registration_allowed       = true
  registration_email_as_username = false
  login_with_email_allowed   = true
  duplicate_emails_allowed   = false
  reset_password_allowed     = false
  edit_username_allowed      = false

  default_signature_algorithm = "RS256"

  remember_me  = false
  verify_email = false
}
