# NOTICE:
# This configuration uses the local backend for Terraform state.
# The state file (terraform.tfstate) will be stored on your local machine.
# For team or production use, consider a remote backend (e.g., S3, Azure Storage)
# to enable state sharing, locking, and better security.

terraform {
  backend "local" {
    path = "terraform.tfstate"
  }
}
