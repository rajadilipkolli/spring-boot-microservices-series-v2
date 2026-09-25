terraform {
  required_version = ">= 1.9.0"

  required_providers {
    keycloak = {
      source  = "mrparkers/keycloak"
      version = "~> 4.4"
    }
  }
}
