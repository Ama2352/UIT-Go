# =============================================================================
# Azure Infrastructure Variables
# =============================================================================

# -----------------------------------------------------------------------------
# Project Settings
# -----------------------------------------------------------------------------
variable "project_name" {
  description = "Name of the project (used for naming resources)"
  type        = string
  default     = "uitgo"
}

variable "location" {
  description = "Azure region to deploy resources"
  type        = string
  default     = "southeastasia"
}

variable "environment" {
  description = "Environment name (dev, prod, etc.)"
  type        = string
  default     = "dev"
}

# -----------------------------------------------------------------------------
# Azure Credentials
# -----------------------------------------------------------------------------
variable "azure_subscription_id" {
  description = "Azure Subscription ID"
  type        = string
  sensitive   = true
}

variable "azure_client_id" {
  description = "Azure Client ID"
  type        = string
  sensitive   = true
}

variable "azure_client_secret" {
  description = "Azure Client Secret"
  type        = string
  sensitive   = true
}

variable "azure_tenant_id" {
  description = "Azure Tenant ID"
  type        = string
  sensitive   = true
}

# -----------------------------------------------------------------------------
# VM Configuration
# -----------------------------------------------------------------------------
variable "vm_size" {
  description = "Size of the VM"
  type        = string
  default     = "Standard_B2ms"
}

variable "admin_username" {
  description = "Admin username for the VM"
  type        = string
  default     = "azureuser"
}

variable "ssh_public_key" {
  description = "The SSH public key content for VM access"
  type        = string
}

variable "additional_ssh_keys" {
  description = "Additional SSH public keys for VM access (e.g., local developer keys)"
  type        = list(string)
  default     = []
}

variable "allowed_ssh_cidr" {
  description = "CIDR block allowed to SSH into the VM"
  type        = string
  default     = "0.0.0.0/0"
}
