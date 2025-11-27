# =============================================================================
# UIT-Go Infrastructure
# =============================================================================
# To switch between providers, just change "cloud_provider" in terraform.tfvars:
#   - "localstack" → Local development with LocalStack (free, no cloud needed)
#   - "aws"        → Real AWS deployment
#   - "azure"      → Real Azure deployment
# =============================================================================

# -----------------------------------------------------------------------------
# VPC / VNet Module
# -----------------------------------------------------------------------------
module "network" {
  source = "./modules/network"

  cloud_provider  = var.cloud_provider
  project_name    = var.project_name
  region          = var.region
  cidr_block      = var.cidr_block
  public_subnets  = var.public_subnets
  private_subnets = var.private_subnets
}

# -----------------------------------------------------------------------------
# Outputs
# -----------------------------------------------------------------------------

output "cloud_provider" {
  description = "The cloud provider in use"
  value       = var.cloud_provider
}

output "network_id" {
  description = "VPC ID (AWS/LocalStack) or VNet ID (Azure)"
  value       = module.network.network_id
}

output "public_subnet_ids" {
  description = "List of public subnet IDs"
  value       = module.network.public_subnet_ids
}

output "private_subnet_ids" {
  description = "List of private subnet IDs"
  value       = module.network.private_subnet_ids
}

output "resource_group_name" {
  description = "Azure Resource Group name (only for Azure)"
  value       = module.network.resource_group_name
}

