# =============================================================================
# Network Module Outputs
# =============================================================================
# Returns the appropriate values based on which cloud provider is used
# =============================================================================

output "network_id" {
  description = "VPC ID (AWS/LocalStack) or VNet ID (Azure)"
  value = (
    local.is_azure
    ? azurerm_virtual_network.this[0].id
    : aws_vpc.this[0].id
  )
}

output "public_subnet_ids" {
  description = "List of public subnet IDs"
  value = (
    local.is_azure
    ? azurerm_subnet.public[*].id
    : aws_subnet.public[*].id
  )
}

output "private_subnet_ids" {
  description = "List of private subnet IDs"
  value = (
    local.is_azure
    ? azurerm_subnet.private[*].id
    : aws_subnet.private[*].id
  )
}

output "resource_group_name" {
  description = "Azure Resource Group name (empty for AWS/LocalStack)"
  value       = local.is_azure ? azurerm_resource_group.this[0].name : ""
}
