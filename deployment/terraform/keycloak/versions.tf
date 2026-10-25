terraform {
  required_version = ">= 1.9.0"

  required_providers {
    keycloak = {
      source  = "keycloak/keycloak"
      version = "5.10.0"
    }
  }
}
