# =============================================================================
# Compute Module Outputs
# =============================================================================

output "vm_id" {
  description = "ID of the Virtual Machine"
  value       = local.is_azure ? azurerm_linux_virtual_machine.this[0].id : ""
}

output "vm_name" {
  description = "Name of the Virtual Machine"
  value       = local.is_azure ? azurerm_linux_virtual_machine.this[0].name : ""
}

output "vm_public_ip" {
  description = "Public IP address of the VM"
  value       = local.is_azure && var.enable_public_ip ? azurerm_public_ip.vm[0].ip_address : ""
}

output "vm_private_ip" {
  description = "Private IP address of the VM"
  value       = local.is_azure ? azurerm_network_interface.vm[0].private_ip_address : ""
}

output "ssh_connection_string" {
  description = "SSH command to connect to the VM"
  value       = local.is_azure && var.enable_public_ip ? "ssh ${var.admin_username}@${azurerm_public_ip.vm[0].ip_address}" : ""
}

output "admin_username" {
  description = "Admin username for the VM"
  value       = var.admin_username
}
