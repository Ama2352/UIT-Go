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
# Compute Module (Azure VM to host the entire project)
# -----------------------------------------------------------------------------
module "compute" {
  source = "./modules/compute"
  count  = var.enable_vm ? 1 : 0

  cloud_provider      = var.cloud_provider
  project_name        = var.project_name
  region              = var.region
  resource_group_name = module.network.resource_group_name
  subnet_id           = length(module.network.public_subnet_ids) > 0 ? module.network.public_subnet_ids[0] : ""

  # VM Configuration
  vm_size             = var.vm_size
  admin_username      = var.vm_admin_username
  ssh_public_key_path = var.ssh_public_key_path
  ssh_public_key      = var.ssh_public_key
  os_disk_size_gb     = var.vm_os_disk_size_gb
  data_disk_size_gb   = var.vm_data_disk_size_gb
  allowed_ssh_cidr    = var.allowed_ssh_cidr
  enable_public_ip    = true
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

# -----------------------------------------------------------------------------
# VM Outputs (only when VM is enabled)
# -----------------------------------------------------------------------------
output "vm_public_ip" {
  description = "Public IP address of the VM"
  value       = var.enable_vm ? module.compute[0].vm_public_ip : ""
}

output "vm_ssh_command" {
  description = "SSH command to connect to the VM"
  value       = var.enable_vm ? module.compute[0].ssh_connection_string : ""
}

output "vm_private_ip" {
  description = "Private IP address of the VM"
  value       = var.enable_vm ? module.compute[0].vm_private_ip : ""
}

