# UIT-Go Terraform Infrastructure Architecture

## Overview

This Terraform configuration provides a **multi-cloud infrastructure** supporting:
- **LocalStack** — Free local development (AWS API emulation)
- **AWS** — Real AWS cloud deployment
- **Azure** — Primary production deployment (recommended)

The architecture is designed with **modularity**, **flexibility**, and **cost optimization** in mind, allowing seamless switching between cloud providers with a single variable change.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         UIT-Go Infrastructure                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                     terraform.tfvars                                │   │
│   │                  cloud_provider = "azure"                           │   │
│   └──────────────────────────┬──────────────────────────────────────────┘   │
│                              │                                              │
│              ┌───────────────┼───────────────┐                              │
│              ▼               ▼               ▼                              │
│      ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                     │
│      │  LocalStack  │ │     AWS      │ │    Azure     │                     │
│      │   (Local)    │ │   (Cloud)    │ │  (Primary)   │                     │
│      └──────────────┘ └──────────────┘ └──────────────┘                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Provider Selection

Switch between cloud providers by changing a single variable in `terraform.tfvars`:

```hcl
cloud_provider = "localstack"  # Local development (free)
cloud_provider = "aws"         # AWS deployment
cloud_provider = "azure"       # Azure deployment (recommended)
```

---

## Azure Architecture (Primary Focus)

### High-Level Azure Infrastructure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Azure Resource Group (uitgo-rg)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                   Virtual Network (uitgo-vnet)                      │   │
│   │                        CIDR: 10.0.0.0/16                            │   │
│   │  ┌────────────────────────┐  ┌────────────────────────┐             │   │
│   │  │   Public Subnets       │  │   Private Subnets      │             │   │
│   │  │  - 10.0.1.0/24         │  │  - 10.0.3.0/24         │             │   │
│   │  │  - 10.0.2.0/24         │  │  - 10.0.4.0/24         │             │   │
│   │  └────────────────────────┘  └────────────────────────┘             │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                    Network Security Group                           │   │
│   │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │   │
│   │  │ SSH:22   │ │HTTP:8000 │ │HTTPS:443 │ │ HTTP:80  │ │RabbitMQ  │  │   │
│   │  │(Restrict)│ │  (Kong)  │ │  (TLS)   │ │(Standard)│ │  :15672  │  │   │
│   │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                   Linux Virtual Machine (uitgo-vm)                  │   │
│   │  ┌─────────────────────────────────────────────────────────────┐    │   │
│   │  │  Size: Standard_B2ms (2 vCPU, 8GB RAM)                      │    │   │
│   │  │  OS: Ubuntu 22.04 LTS                                       │    │   │
│   │  │  Auth: SSH Key                                              │    │   │
│   │  └─────────────────────────────────────────────────────────────┘    │   │
│   │  ┌──────────────────────┐  ┌──────────────────────┐                 │   │
│   │  │   OS Disk (64GB)     │  │  Data Disk (100GB)   │                 │   │
│   │  │   Premium SSD        │  │  Premium SSD         │                 │   │
│   │  │   (System + Docker)  │  │  (Docker Volumes)    │                 │   │
│   │  └──────────────────────┘  └──────────────────────┘                 │   │
│   │  ┌──────────────────────┐  ┌──────────────────────┐                 │   │
│   │  │   Public IP          │  │  Network Interface   │                 │   │
│   │  │   (Static)           │  │  (NIC)               │                 │   │
│   │  └──────────────────────┘  └──────────────────────┘                 │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Azure Resources Created

| Resource | Name | Description |
|----------|------|-------------|
| **Resource Group** | `uitgo-rg` | Container for all Azure resources |
| **Virtual Network** | `uitgo-vnet` | Isolated network (10.0.0.0/16) |
| **Public Subnets** | `uitgo-public-0/1` | Internet-facing subnets |
| **Private Subnets** | `uitgo-private-0/1` | Internal-only subnets |
| **Network Security Group** | `uitgo-vm-nsg` | Firewall rules for VM |
| **Public IP** | `uitgo-vm-pip` | Static IP for external access |
| **Network Interface** | `uitgo-vm-nic` | VM network attachment |
| **Linux VM** | `uitgo-vm` | Ubuntu 22.04 with Docker |
| **OS Disk** | `uitgo-vm-osdisk` | 64GB Premium SSD |
| **Data Disk** | `uitgo-vm-datadisk` | 100GB Premium SSD for Docker volumes |

### Network Security Rules

| Rule | Port | Source | Purpose |
|------|------|--------|---------|
| SSH | 22 | Restricted CIDR | Admin access |
| HTTP-Kong | 8000 | Any | API Gateway |
| HTTPS | 443 | Any | Secure traffic |
| HTTP | 80 | Any | Standard HTTP |
| RabbitMQ-Management | 15672 | Restricted CIDR | Queue monitoring |

### VM Configuration

```hcl
# VM Sizes Available
Standard_B2ms   → 2 vCPU, 8GB RAM   (~$60/month) - Dev/Demo (Default)
Standard_D2s_v3 → 2 vCPU, 8GB RAM   (~$96/month) - Production

# Cloud-Init Auto-Setup
- Docker CE + Docker Compose
- User added to docker group
- /opt/uit-go directory created
- Data disk formatted and mounted at /data
```

---

## Module Structure

```
infrastructure/terraform/
├── main.tf                 # Root configuration, module calls, outputs
├── variables.tf            # Input variables with validation
├── providers.tf            # AWS + Azure provider configuration
├── backend.tf              # Azure Storage backend for state
├── terraform.tfvars        # Environment-specific values
│
└── modules/
    ├── network/
    │   ├── aws.tf          # VPC, subnets, IGW, route tables
    │   ├── azure.tf        # Resource group, VNet, subnets
    │   ├── variables.tf    # Module inputs
    │   └── outputs.tf      # Module outputs
    │
    └── compute/
        ├── azure.tf        # VM, disks, NIC, NSG, public IP
        ├── variables.tf    # Module inputs
        └── outputs.tf      # Module outputs
```

### Module: Network

**Purpose:** Create the foundational network infrastructure.

| Provider | Resources Created |
|----------|-------------------|
| Azure | Resource Group, VNet, Public Subnets, Private Subnets |
| AWS/LocalStack | VPC, Internet Gateway, Subnets, Route Tables |

### Module: Compute

**Purpose:** Create compute resources to host the application.

| Provider | Resources Created |
|----------|-------------------|
| Azure | VM, NSG, NIC, Public IP, OS Disk, Data Disk |
| AWS | (Not implemented in current version) |

---

## State Management

Terraform state is stored in **Azure Blob Storage** for:
- Team collaboration
- CI/CD pipeline integration
- State locking (prevents concurrent modifications)

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

---

## Deployment Workflow

### Prerequisites

1. Azure CLI installed and authenticated
2. Terraform >= 1.0 installed
3. SSH key pair generated (`~/.ssh/id_rsa.pub`)
4. Azure Storage account for state (optional for local dev)

### Deploy to Azure

```bash
# 1. Navigate to terraform directory
cd infrastructure/terraform

# 2. Initialize Terraform
terraform init

# 3. Review the plan
terraform plan

# 4. Apply the infrastructure
terraform apply

# 5. Connect to VM
ssh azureuser@<VM_PUBLIC_IP>
```

### Post-Deployment Setup

```bash
# On the Azure VM, run the setup script
bash /opt/uit-go/infrastructure/scripts/setup-vm.sh
```

This script will:
- Clone the UIT-Go repository
- Create environment variables file
- Build and start all Docker services

---

## Key Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `cloud_provider` | `localstack` | Provider selection (localstack/aws/azure) |
| `project_name` | `uitgo` | Resource naming prefix |
| `region` | `ap-southeast-1` | Deployment region |
| `enable_vm` | `false` | Create VM (set to `true` for Azure) |
| `vm_size` | `Standard_B2ms` | Azure VM size |
| `vm_admin_username` | `azureuser` | SSH username |
| `ssh_public_key_path` | `~/.ssh/id_rsa.pub` | SSH public key |
| `allowed_ssh_cidr` | `0.0.0.0/0` | SSH access restriction |

---

## Cost Estimation (Azure)

| Resource | Monthly Cost (Approx.) |
|----------|------------------------|
| VM (Standard_B2ms) | $60 |
| Premium SSD (164GB) | $20 |
| Public IP (Static) | $3 |
| Bandwidth (50GB) | $4 |
| **Total** | **~$87/month** |

> **Note:** Azure for Students provides $100 credit, covering ~1 month of deployment.

---

## Security Considerations

1. **SSH Access:** Restrict `allowed_ssh_cidr` to your IP in production
2. **Secrets:** Use Azure Key Vault for sensitive data
3. **NSG Rules:** Minimize exposed ports
4. **Updates:** VM auto-updates enabled via cloud-init

---

## Future Enhancements

1. **Azure Kubernetes Service (AKS)** — Container orchestration
2. **Azure Database for PostgreSQL** — Managed database
3. **Azure Application Gateway** — Load balancing + WAF
4. **Multi-region deployment** — High availability
5. **Azure Monitor** — Centralized logging and metrics

---

## Quick Reference Commands

```bash
# Initialize
terraform init

# Plan changes
terraform plan

# Apply changes
terraform apply

# Destroy infrastructure
terraform destroy

# Show outputs
terraform output

# Format files
terraform fmt -recursive

# Validate configuration
terraform validate
```

---

## References

- [Terraform Azure Provider](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)
- [Azure Virtual Machines](https://docs.microsoft.com/en-us/azure/virtual-machines/)
- [LocalStack](https://localstack.cloud/)
- [Cloud-Init](https://cloudinit.readthedocs.io/)
