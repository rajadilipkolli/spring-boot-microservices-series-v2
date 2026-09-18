terraform {
  backend "kubernetes" {
    secret_suffix     = "keycloak-realm"
    namespace         = "retailstore"
    in_cluster_config = true
  }
}
