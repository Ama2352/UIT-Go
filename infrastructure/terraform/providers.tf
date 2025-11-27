# =============================================================================
# Provider Configuration
# =============================================================================
# Both providers are configured, but only one will create resources
# based on the "cloud_provider" variable.
# =============================================================================

terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }
}

# -----------------------------------------------------------------------------
# AWS Provider (used for both AWS and LocalStack)
# -----------------------------------------------------------------------------
provider "aws" {
  region     = var.region
  access_key = var.aws_access_key
  secret_key = var.aws_secret_key

  # Skip validation when using LocalStack OR Azure (since AWS isn't used with Azure)
  skip_credentials_validation = var.cloud_provider != "aws"
  skip_requesting_account_id  = var.cloud_provider != "aws"
  skip_region_validation      = var.cloud_provider != "aws"

  # LocalStack endpoints (only used when cloud_provider = "localstack")
  dynamic "endpoints" {
    for_each = var.cloud_provider == "localstack" ? [1] : []
    content {
      ec2 = "http://localhost:4566"
      s3  = "http://localhost:4566"
      iam = "http://localhost:4566"
      sts = "http://localhost:4566"
    }
  }
}

# -----------------------------------------------------------------------------
# Azure Provider
# -----------------------------------------------------------------------------
provider "azurerm" {
  features {}
  subscription_id = var.azure_subscription_id
}
