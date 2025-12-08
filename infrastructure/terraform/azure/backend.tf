# =============================================================================
# Backend Configuration
# =============================================================================
# The backend configuration is partially defined here.
# The full configuration (resource_group_name, storage_account_name, container_name, key)
# is passed via the `terraform init` command in the CI/CD pipeline.
# =============================================================================

terraform {
  backend "azurerm" {}
}
