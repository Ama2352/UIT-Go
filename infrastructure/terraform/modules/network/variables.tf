# =============================================================================
# Network Module Variables
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

variable "cidr_block" {
  description = "CIDR block for VPC/VNet"
  type        = string
}

variable "public_subnets" {
  description = "List of public subnet CIDRs"
  type        = list(string)
}

variable "private_subnets" {
  description = "List of private subnet CIDRs"
  type        = list(string)
}

# -----------------------------------------------------------------------------
# Local values for cleaner conditionals
# -----------------------------------------------------------------------------
locals {
  is_aws   = var.cloud_provider == "aws" || var.cloud_provider == "localstack"
  is_azure = var.cloud_provider == "azure"

  # Map AWS region to Azure location
  azure_location = var.region == "ap-southeast-1" ? "southeastasia" : var.region
}
