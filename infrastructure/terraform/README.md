# UIT-Go Terraform Infrastructure

A simple, clean Terraform setup that works with **LocalStack**, **AWS**, or **Azure**.

## 📁 Structure

```
terraform/
├── main.tf              # Main configuration (uses network module)
├── variables.tf         # All variable definitions
├── providers.tf         # AWS and Azure provider configuration
├── terraform.tfvars     # ⭐ YOUR CONFIGURATION FILE (change provider here!)
├── backend.tf           # State backend configuration
└── modules/
    └── network/
        ├── variables.tf # Module inputs
        ├── aws.tf       # AWS/LocalStack resources
        ├── azure.tf     # Azure resources
        └── outputs.tf   # Module outputs
```

## 🚀 Quick Start

### 1. Switch Cloud Provider

Edit `terraform.tfvars` and change ONE line:

```hcl
# Choose one:
cloud_provider = "localstack"   # Free local development
cloud_provider = "aws"          # Real AWS
cloud_provider = "azure"        # Real Azure
```

### 2. Run Terraform

```bash
# Initialize (first time only)
terraform init

# See what will be created
terraform plan

# Deploy
terraform apply

# Destroy (clean up)
terraform destroy
```

## ☁️ Provider Details

### LocalStack (Default)
- **Free** - runs on your local machine
- Requires LocalStack running (`docker-compose up`)
- Uses fake credentials (`test/test`)

### AWS
- Requires real AWS credentials
- Set `aws_access_key` and `aws_secret_key` in `terraform.tfvars`
- Or use AWS CLI: `aws configure`

### Azure
- Requires Azure subscription
- Login first: `az login`
- Set `azure_subscription_id` in `terraform.tfvars`

## 🔧 What Gets Created

| Resource | AWS/LocalStack | Azure |
|----------|----------------|-------|
| Network | VPC | VNet |
| Resource Group | - | ✅ |
| Public Subnets | 2 | 2 |
| Private Subnets | 2 | 2 |
| Internet Gateway | ✅ | - |
| Route Table | ✅ | - |

## 💡 Tips

- Always run `terraform plan` before `terraform apply`
- Use `terraform destroy` to avoid billing on real clouds
- The state file (`terraform.tfstate`) tracks your infrastructure - don't delete it!
