# terraform.tfvars
#     ↓
# envs/local/variables.tf (defines var.cloud_provider)
#     ↓
# envs/local/main.tf (passes var.cloud_provider → module's cloud_provider)
#     ↓
# modules/vpc/variables.tf (receives cloud_provider value)
#     ↓
# modules/vpc/main.tf (uses var.cloud_provider)


module "vpc" {
  source = "../../modules/vpc"

  cloud_provider = var.cloud_provider
  name           = var.project_name

  cidr_block = "10.0.0.0/16"

  public_subnets = [
    "10.0.1.0/24",
    "10.0.2.0/24"
  ]

  private_subnets = [
    "10.0.3.0/24",
    "10.0.4.0/24"
  ]
}


output "vpc_id" {
  value = module.vpc.vpc_id
}

output "public_subnets" {
  value = module.vpc.public_subnets
}

output "private_subnets" {
  value = module.vpc.private_subnets
}
