# =============================================================================
# Terraform Backend Configuration
# =============================================================================
# Using local backend for simplicity.
# For production, consider using remote backends like S3 or Azure Storage.
# =============================================================================

terraform {
  backend "local" {
    path = "terraform.tfstate"
  }
}
