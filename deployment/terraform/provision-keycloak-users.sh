#!/usr/bin/env sh
set -eu

: "${TF_VAR_keycloak_url:?TF_VAR_keycloak_url is required}"
: "${TF_VAR_keycloak_admin_username:?TF_VAR_keycloak_admin_username is required}"
: "${TF_VAR_keycloak_admin_password:?TF_VAR_keycloak_admin_password is required}"
: "${KEYCLOAK_RAJA_PASSWORD:?KEYCLOAK_RAJA_PASSWORD is required}"
: "${KEYCLOAK_RETAIL_PASSWORD:?KEYCLOAK_RETAIL_PASSWORD is required}"

token="$(curl --fail --silent --show-error \
  ${CURL_CA_BUNDLE:+--cacert "$CURL_CA_BUNDLE"} \
  --request POST \
  --data-urlencode 'client_id=admin-cli' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode "username=${TF_VAR_keycloak_admin_username}" \
  --data-urlencode "password=${TF_VAR_keycloak_admin_password}" \
  "${TF_VAR_keycloak_url}/realms/master/protocol/openid-connect/token" |
  jq --exit-status --raw-output '.access_token')"

set_password() {
  username="$1"
  password="$2"

  user_id="$(curl --fail --silent --show-error --get \
    ${CURL_CA_BUNDLE:+--cacert "$CURL_CA_BUNDLE"} \
    --header "Authorization: Bearer ${token}" \
    --data-urlencode "username=${username}" \
    --data-urlencode 'exact=true' \
    "${TF_VAR_keycloak_url}/admin/realms/retailstore/users" |
    jq --exit-status --raw-output --arg username "$username" \
      'if length == 1 and .[0].username == $username then .[0].id else error("expected exactly one matching user") end')"

    jq --null-input --compact-output --arg value "$password" \
    '{type: "password", value: $value, temporary: false}' |
    curl --fail --silent --show-error \
      ${CURL_CA_BUNDLE:+--cacert "$CURL_CA_BUNDLE"} \
      --request PUT \
      --header "Authorization: Bearer ${token}" \
      --header 'Content-Type: application/json' \
      --data-binary @- \
      "${TF_VAR_keycloak_url}/admin/realms/retailstore/users/${user_id}/reset-password"

  echo "[password-provisioner] Password updated for ${username}."
}

set_password raja "$KEYCLOAK_RAJA_PASSWORD"
set_password retail "$KEYCLOAK_RETAIL_PASSWORD"
