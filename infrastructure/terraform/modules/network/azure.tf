# =============================================================================
# Network Module - Azure Resources
# =============================================================================
# These resources are created when cloud_provider = "azure"
# =============================================================================

# -----------------------------------------------------------------------------
# Resource Group (required in Azure for all resources)
# -----------------------------------------------------------------------------
resource "azurerm_resource_group" "this" {
  count = local.is_azure ? 1 : 0

  name     = "${var.project_name}-rg"
  location = local.azure_location

  tags = {
    Project     = var.project_name
    Environment = "demo"
  }
}

# -----------------------------------------------------------------------------
# Virtual Network (Azure's equivalent of VPC)
# -----------------------------------------------------------------------------
resource "azurerm_virtual_network" "this" {
  count = local.is_azure ? 1 : 0

  name                = "${var.project_name}-vnet"
  address_space       = [var.cidr_block]
  location            = azurerm_resource_group.this[0].location
  resource_group_name = azurerm_resource_group.this[0].name

  tags = {
    Project     = var.project_name
    Environment = "demo"
  }
}

# -----------------------------------------------------------------------------
# Public Subnets
# -----------------------------------------------------------------------------
resource "azurerm_subnet" "public" {
  count = local.is_azure ? length(var.public_subnets) : 0

  name                 = "${var.project_name}-public-${count.index}"
  resource_group_name  = azurerm_resource_group.this[0].name
  virtual_network_name = azurerm_virtual_network.this[0].name
  address_prefixes     = [var.public_subnets[count.index]]
}

# -----------------------------------------------------------------------------
# Private Subnets
# -----------------------------------------------------------------------------
resource "azurerm_subnet" "private" {
  count = local.is_azure ? length(var.private_subnets) : 0

  name                 = "${var.project_name}-private-${count.index}"
  resource_group_name  = azurerm_resource_group.this[0].name
  virtual_network_name = azurerm_virtual_network.this[0].name
  address_prefixes     = [var.private_subnets[count.index]]
}
