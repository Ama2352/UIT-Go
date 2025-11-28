# =============================================================================
# Variables
# =============================================================================

# -----------------------------------------------------------------------------
# Provider Selection (THE MAIN SWITCH!)
# -----------------------------------------------------------------------------
variable "cloud_provider" {
  description = "Which cloud to use: 'localstack', 'aws', or 'azure'"
  type        = string
  default     = "localstack"

  validation {
    condition     = contains(["localstack", "aws", "azure"], var.cloud_provider)
    error_message = "cloud_provider must be 'localstack', 'aws', or 'azure'."
  }
}

# -----------------------------------------------------------------------------
# Project Settings
# -----------------------------------------------------------------------------
variable "project_name" {
  description = "Name of the project (used for naming resources)"
  type        = string
  default     = "uitgo"
}

variable "region" {
  description = "Region to deploy resources"
  type        = string
  default     = "ap-southeast-1" # Singapore for AWS, southeastasia for Azure
}

# -----------------------------------------------------------------------------
# Network Settings
# -----------------------------------------------------------------------------
variable "cidr_block" {
  description = "CIDR block for VPC/VNet"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnets" {
  description = "List of public subnet CIDRs"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnets" {
  description = "List of private subnet CIDRs"
  type        = list(string)
  default     = ["10.0.3.0/24", "10.0.4.0/24"]
}

# -----------------------------------------------------------------------------
# AWS/LocalStack Credentials
# -----------------------------------------------------------------------------
variable "aws_access_key" {
  description = "AWS Access Key (use 'test' for LocalStack)"
  type        = string
  default     = "test"
  sensitive   = true
}

variable "aws_secret_key" {
  description = "AWS Secret Key (use 'test' for LocalStack)"
  type        = string
  default     = "test"
  sensitive   = true
}

# -----------------------------------------------------------------------------
# Azure Credentials
# -----------------------------------------------------------------------------
variable "azure_subscription_id" {
  description = "Azure Subscription ID"
  type        = string
  default     = ""
  sensitive   = true
}

# -----------------------------------------------------------------------------
# Compute Settings (VM Configuration)
# -----------------------------------------------------------------------------
variable "enable_vm" {
  description = "Whether to create a VM to host the project"
  type        = bool
  default     = false
}

variable "vm_size" {
  description = "Size of the VM. Recommended: Standard_B2ms (dev), Standard_D2s_v3 (prod)"
  type        = string
  default     = "Standard_B2ms"
}

variable "vm_admin_username" {
  description = "Admin username for the VM"
  type        = string
  default     = "azureuser"
}

variable "ssh_public_key_path" {
  description = "Path to SSH public key file for VM access"
  type        = string
  default     = "~/.ssh/id_rsa.pub"
}

variable "vm_os_disk_size_gb" {
  description = "Size of the OS disk in GB"
  type        = number
  default     = 64
}

variable "vm_data_disk_size_gb" {
  description = "Size of the data disk in GB (for Docker volumes)"
  type        = number
  default     = 100
}

variable "allowed_ssh_cidr" {
  description = "CIDR block allowed to SSH into the VM (restrict in production!)"
  type        = string
  default     = "0.0.0.0/0"
}
