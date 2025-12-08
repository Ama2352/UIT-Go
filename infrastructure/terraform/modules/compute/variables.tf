# =============================================================================
# Compute Module Variables
# =============================================================================

variable "cloud_provider" {
  description = "Which cloud to use: 'localstack', 'aws', or 'azure'"
  type        = string
}

variable "project_name" {
  description = "Name of the project"
  type        = string
}

variable "region" {
  description = "Region to deploy resources"
  type        = string
}

# -----------------------------------------------------------------------------
# Azure-specific variables
# -----------------------------------------------------------------------------
variable "resource_group_name" {
  description = "Azure Resource Group name"
  type        = string
  default     = ""
}

variable "subnet_id" {
  description = "Subnet ID to deploy the VM into"
  type        = string
}

# -----------------------------------------------------------------------------
# VM Configuration
# -----------------------------------------------------------------------------
variable "vm_size" {
  description = "Size of the VM (Azure: Standard_B2ms, Standard_D2s_v3, etc.)"
  type        = string
  default     = "Standard_B2ms"
}

variable "admin_username" {
  description = "Admin username for the VM"
  type        = string
  default     = "azureuser"
}

variable "ssh_public_key_path" {
  description = "Path to SSH public key file for VM access"
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

variable "ssh_public_key" {
  description = "The actual SSH public key content (passed from CI/CD)"
  type        = string
  default     = ""
}

variable "enable_public_ip" {
  description = "Whether to assign a public IP to the VM"
  type        = bool
  default     = true
}

variable "allowed_ssh_cidr" {
  description = "CIDR block allowed to SSH into the VM"
  type        = string
  default     = "0.0.0.0/0" # Restrict this in production!
}

variable "os_disk_size_gb" {
  description = "Size of the OS disk in GB"
  type        = number
  default     = 64
}

variable "data_disk_size_gb" {
  description = "Size of the data disk in GB (for Docker volumes)"
  type        = number
  default     = 100
}

# -----------------------------------------------------------------------------
# Local values
# -----------------------------------------------------------------------------
locals {
  is_aws   = var.cloud_provider == "aws" || var.cloud_provider == "localstack"
  is_azure = var.cloud_provider == "azure"

  # Map AWS region to Azure location
  azure_location = var.region == "ap-southeast-1" ? "southeastasia" : var.region
}
