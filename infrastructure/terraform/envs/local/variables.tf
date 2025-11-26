variable "cloud_provider" {
  type    = string
  default = "localstack"
}

variable "project_name" {
  type    = string
  default = "uit-go"
}

variable "aws_access_key" {
  type        = string
  description = "AWS access key for LocalStack"
  default     = "test"
  sensitive   = true
}

variable "aws_secret_key" {
  type        = string
  description = "AWS secret key for LocalStack"
  default     = "test"
  sensitive   = true
}

variable "azure_subscription_id" {
  type        = string
  description = "Azure subscription ID"
  sensitive   = true
}
