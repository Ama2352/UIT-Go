# =============================================================================
# Outputs
# =============================================================================

output "vm_public_ip" {
  description = "Public IP address of the VM"
  value       = azurerm_public_ip.vm.ip_address
}

output "admin_username" {
  description = "Admin username for the VM"
  value       = var.admin_username
}

output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.main.name
}
