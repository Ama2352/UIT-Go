variable "cloud_provider" {
  description = "Choose between: aws, localstack, azure"
  type        = string
}

variable "name" {
  type        = string
  description = "Base name for the VPC / VNet"
}

variable "cidr_block" {
  type        = string
  description = "VPC or VNet CIDR block"
  default     = "10.0.0.0/16"
}

variable "public_subnets" {
  type        = list(string)
  description = "List of public subnet CIDRs"
}

variable "private_subnets" {
  type        = list(string)
  description = "List of private subnet CIDRs"
}


