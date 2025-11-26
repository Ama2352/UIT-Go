output "vpc_id" {
  value = (
    var.cloud_provider == "azure" ?
    azurerm_virtual_network.this[0].id :
    aws_vpc.this[0].id
  )
}

output "public_subnets" {
  value = (
    var.cloud_provider == "azure" ?
    azurerm_subnet.public[*].id :
    aws_subnet.public[*].id
  )
}

output "private_subnets" {
  value = (
    var.cloud_provider == "azure" ?
    azurerm_subnet.private[*].id :
    aws_subnet.private[*].id
  )
}
