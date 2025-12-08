# ADR 007: Choosing Terraform for Multi-Cloud Infrastructure

## Status

**Accepted** — November 2025

---

## Context

UIT-Go requires infrastructure automation for:

1. **Local development**: Fast, free testing environment
2. **Cloud deployment**: Production-ready Azure infrastructure
3. **CI/CD integration**: Automated provisioning via GitHub Actions
4. **Cost optimization**: Leverage free Azure student credits

### Why Azure?

Azure was selected as the primary cloud provider because of the **Azure for Students** program. Team members have access to a **one-time $100 credit** via their university email (education package), making Azure the most cost-effective choice for this academic project. This credit covers approximately 2 months of VM hosting for the MVP.

### Requirements

| Requirement | Priority | Notes |
|-------------|----------|-------|
| Azure deployment | High | Free student credits available |
| Local development | High | Free, fast iteration |
| Version control | High | Infrastructure as Code (IaC) |
| CI/CD integration | High | GitHub Actions compatibility |
| State management | Medium | Track infrastructure changes |
| Modular design | Medium | Reusable components |

---

## Decision

We chose **Terraform** with:
- **LocalStack** for local development (AWS API emulation)
- **Azure** for production deployment
- **Modular structure** for reusable network/compute components

---

## Rationale

### 1. Provider Flexibility

Terraform's provider model enables cloud switching:

```hcl
# terraform.tfvars - Switch with one line
cloud_provider = "localstack"  # or "azure" or "aws"
```

### 2. LocalStack for Local Development

Free, fast local testing using AWS-compatible APIs:

```hcl
provider "aws" {
  alias = "localstack"
  
  endpoints {
    s3        = "http://localhost:4566"
    dynamodb  = "http://localhost:4566"
    sqs       = "http://localhost:4566"
  }
  
  skip_credentials_validation = true
  skip_requesting_account_id  = true
}
```

### 3. Modular Infrastructure

Reusable modules for network and compute:

```
infrastructure/terraform/
├── main.tf              # Root configuration
├── variables.tf         # Input variables
├── providers.tf         # Provider configuration
├── terraform.tfvars     # Environment settings
└── modules/
    ├── network/
    │   ├── aws.tf       # VPC, subnets, gateways
    │   ├── azure.tf     # VNet, subnets, NSG
    │   └── outputs.tf
    └── compute/
        ├── azure.tf     # VM, disk, NIC
        └── outputs.tf
```

### 4. Azure Production Configuration

Single VM deployment for MVP (cost-effective):

```hcl
resource "azurerm_linux_virtual_machine" "main" {
  name                = "uit-go-vm"
  resource_group_name = azurerm_resource_group.main.name
  location            = var.azure_region
  size                = "Standard_B2s"  # 2 vCPU, 4GB RAM
  
  admin_username = "azureuser"
  
  network_interface_ids = [
    azurerm_network_interface.main.id
  ]

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts"
    version   = "latest"
  }
}
```

### 5. CI/CD Integration

GitHub Actions workflow for automated deployment:

```yaml
name: Deploy Infrastructure

on:
  workflow_dispatch:
    inputs:
      action:
        type: choice
        options: [apply, destroy]

jobs:
  terraform:
    runs-on: ubuntu-latest
    steps:
      - uses: hashicorp/setup-terraform@v3
      
      - name: Terraform Init
        run: terraform init
        
      - name: Terraform Apply
        if: inputs.action == 'apply'
        run: terraform apply -auto-approve
```

---

## Trade-offs Accepted

| Trade-off | Impact | Mitigation |
|-----------|--------|------------|
| **Learning curve** | Team needs Terraform knowledge | Comprehensive documentation |
| **State file management** | Risk of state corruption | Azure Storage backend |
| **Provider version locks** | Breaking changes possible | Pin provider versions |
| **Single VM** | No horizontal scaling | Adequate for MVP; upgrade to AKS later |

---

## Alternatives Considered

### AWS-Only (CloudFormation)
- Deep AWS integration
- AWS vendor lock-in
- No Azure support

### Azure-Only (ARM/Bicep)
- Native Azure integration
- Azure vendor lock-in
- Verbose JSON syntax (ARM)

### Kubernetes (AKS/EKS)
- Production-grade orchestration
- Overkill for MVP
- Higher cost and complexity

---

## Consequences

### Positive

- Multi-cloud flexibility
- Infrastructure as code (version-controlled)
- Local development with LocalStack
- CI/CD integration
- Cost-effective single VM for MVP

### Negative

- Single point of failure (single VM)
- Manual scaling required
- State file management complexity

---

## Future Evolution

### Phase 2: Container Orchestration

```
┌─────────────────────────────────────────────────────────────┐
│                     Azure Kubernetes Service                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ User Svc │  │ Trip Svc │  │Driver Svc│  │Notif Svc │    │
│  │ (3 pods) │  │ (3 pods) │  │ (5 pods) │  │ (2 pods) │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### Phase 3: Multi-Region

```hcl
module "network_eastus" {
  source = "./modules/network"
  region = "eastus"
}

module "network_westeurope" {
  source = "./modules/network"
  region = "westeurope"
}
```

---

## Implementation Details

### State Backend Configuration

```hcl
# backend.tf
terraform {
  backend "azurerm" {
    resource_group_name  = "uit-go-tfstate-rg"
    storage_account_name = "uitgotfstate"
    container_name       = "tfstate"
    key                  = "terraform.tfstate"
  }
}
```

### Network Security Group Rules

```hcl
resource "azurerm_network_security_group" "vm" {
  name                = "${var.project_name}-vm-nsg"
  location            = local.azure_location
  resource_group_name = var.resource_group_name

  # SSH Access
  security_rule {
    name                       = "SSH"
    priority                   = 1001
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = var.allowed_ssh_cidr
    destination_address_prefix = "*"
  }

  # HTTP (Kong Gateway)
  security_rule {
    name                       = "HTTP-Kong"
    priority                   = 1002
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "8000"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  # HTTPS
  security_rule {
    name                       = "HTTPS"
    priority                   = 1003
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}
```

---

## References

- [Terraform Documentation](https://www.terraform.io/docs)
- [Azure Provider](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)
- [LocalStack](https://localstack.cloud/)
- [Terraform Best Practices](https://www.terraform-best-practices.com/)
