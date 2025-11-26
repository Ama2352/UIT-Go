provider "aws" {
  region = "ap-southeast-1"

  # These make AWS provider work with LocalStack
  skip_credentials_validation = true
  skip_requesting_account_id  = true
  skip_region_validation      = true

  access_key = var.aws_access_key
  secret_key = var.aws_secret_key

  endpoints {
    ec2 = "http://localhost:4566"
    s3  = "http://localhost:4566"
    iam = "http://localhost:4566"
    sts = "http://localhost:4566"
  }
}


# Azure Provider (required even if not used when cloud_provider != "azure")
# Using resource_provider_registrations = "none" to avoid registering providers
provider "azurerm" {
  features {}
  resource_provider_registrations = "none"
  subscription_id                 = var.azure_subscription_id
}
