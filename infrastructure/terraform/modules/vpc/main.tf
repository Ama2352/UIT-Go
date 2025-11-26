###############################################
# AWS + LocalStack VPC
###############################################

resource "aws_vpc" "this" {
  count                = var.cloud_provider == "aws" || var.cloud_provider == "localstack" ? 1 : 0
  cidr_block           = var.cidr_block
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.name}-vpc"
  }
}

resource "aws_subnet" "public" {
  count = var.cloud_provider != "azure" ? length(var.public_subnets) : 0

  vpc_id                  = aws_vpc.this[0].id
  cidr_block              = var.public_subnets[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.name}-public-${count.index}"
  }
}


resource "aws_subnet" "private" {
  count = var.cloud_provider != "azure" ? length(var.private_subnets) : 0

  vpc_id     = aws_vpc.this[0].id
  cidr_block = var.private_subnets[count.index]

  tags = {
    Name = "${var.name}-private-${count.index}"
  }
}


resource "aws_internet_gateway" "this" {
  count = var.cloud_provider != "azure" ? 1 : 0

  vpc_id = aws_vpc.this[0].id

  tags = {
    Name = "${var.name}-igw"
  }
}

resource "aws_route_table" "public" {
  count = var.cloud_provider != "azure" ? 1 : 0

  vpc_id = aws_vpc.this[0].id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this[0].id
  }

  tags = {
    Name = "${var.name}-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  count = var.cloud_provider != "azure" ? length(var.public_subnets) : 0

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public[0].id
}


###############################################
# AZURE VNET
###############################################

resource "azurerm_virtual_network" "this" {
  count = var.cloud_provider == "azure" ? 1 : 0

  name                = "${var.name}-vnet"
  address_space       = [var.cidr_block]
  location            = "southeastasia"
  resource_group_name = "uitgo-rg"
}

resource "azurerm_subnet" "public" {
  count = var.cloud_provider == "azure" ? length(var.public_subnets) : 0

  name                 = "${var.name}-public-${count.index}"
  resource_group_name  = "uitgo-rg"
  virtual_network_name = azurerm_virtual_network.this[0].name
  address_prefixes     = [var.public_subnets[count.index]]
}


resource "azurerm_subnet" "private" {
  count = var.cloud_provider == "azure" ? length(var.private_subnets) : 0

  name                 = "${var.name}-private-${count.index}"
  resource_group_name  = "uitgo-rg"
  virtual_network_name = azurerm_virtual_network.this[0].name
  address_prefixes     = [var.private_subnets[count.index]]
}
