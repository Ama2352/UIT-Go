# =============================================================================
# Terraform Backend Configuration
# =============================================================================
# Using Azure Storage for remote state management.
# This ensures state is persisted between GitHub Actions runs.
# =============================================================================

terraform {
  backend "azurerm" {
    resource_group_name  = "uit-go-tfstate-rg"
    storage_account_name = "uitgotfstate"
    container_name       = "tfstate"
    key                  = "terraform.tfstate"
  }
}
