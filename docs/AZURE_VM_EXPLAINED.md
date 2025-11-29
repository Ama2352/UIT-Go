# Azure VM Setup - Explained Step by Step

This document explains exactly what each piece of Terraform code does to create an Azure Virtual Machine.

---

## Table of Contents
1. [What is a Virtual Machine?](#what-is-a-virtual-machine)
2. [The Big Picture](#the-big-picture)
3. [File Structure](#file-structure)
4. [Code Explanation - variables.tf](#variablestf---defining-what-we-need)
5. [Code Explanation - azure.tf](#azuretf---creating-the-resources)
6. [Code Explanation - outputs.tf](#outputstf---getting-information-back)
7. [How It All Connects](#how-it-all-connects)
8. [Deploying the VM](#deploying-the-vm)

---

## What is a Virtual Machine?

A **Virtual Machine (VM)** is like renting a computer in the cloud. Instead of buying physical hardware, you:
- Tell Azure what specs you want (CPU, RAM, disk size)
- Azure creates a computer for you in their data center
- You can SSH into it like a remote computer
- You pay only for the time it runs

**Our VM will:**
- Run Ubuntu Linux (operating system)
- Have Docker installed automatically
- Run all our microservices via docker-compose

---

## The Big Picture

```
┌─────────────────────────────────────────────────────────────────┐
│                         AZURE CLOUD                              │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    Resource Group                          │  │
│  │                    (uitgo-rg)                              │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              Virtual Network (VNet)                  │  │  │
│  │  │              (uitgo-vnet)                            │  │  │
│  │  │  ┌───────────────────────────────────────────────┐  │  │  │
│  │  │  │              Public Subnet                     │  │  │  │
│  │  │  │              (10.0.1.0/24)                     │  │  │  │
│  │  │  │  ┌─────────────────────────────────────────┐  │  │  │  │
│  │  │  │  │           VIRTUAL MACHINE               │  │  │  │  │
│  │  │  │  │           (uitgo-vm)                    │  │  │  │  │
│  │  │  │  │                                         │  │  │  │  │
│  │  │  │  │  • Ubuntu 22.04                         │  │  │  │  │
│  │  │  │  │  • 2 vCPUs, 8GB RAM                     │  │  │  │  │
│  │  │  │  │  • Docker installed                     │  │  │  │  │
│  │  │  │  │  • Your microservices running           │  │  │  │  │
│  │  │  │  │                                         │  │  │  │  │
│  │  │  │  │  [OS Disk: 64GB] [Data Disk: 100GB]     │  │  │  │  │
│  │  │  │  └─────────────────────────────────────────┘  │  │  │  │
│  │  │  │       │                                        │  │  │  │
│  │  │  │       │ Network Interface (NIC)                │  │  │  │
│  │  │  │       ▼                                        │  │  │  │
│  │  │  │  [Public IP: x.x.x.x]                          │  │  │  │
│  │  │  └───────────────────────────────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                            │  │
│  │  [Network Security Group - Firewall Rules]                 │  │
│  │   • Port 22 (SSH) - for remote access                      │  │
│  │   • Port 80/443 (HTTP/HTTPS) - web traffic                 │  │
│  │   • Port 8000 (Kong) - API Gateway                         │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Internet
                              ▼
                         [Your Computer]
                     ssh azureuser@x.x.x.x
```

---

## File Structure

```
modules/compute/
├── variables.tf    ← Defines WHAT we can configure (inputs)
├── azure.tf        ← Defines WHAT to create (resources)
└── outputs.tf      ← Defines WHAT to return (outputs)
```

---

## variables.tf - Defining What We Need

Variables are like **function parameters** - they let you customize the VM without changing the main code.

### Basic Variables

```hcl
variable "cloud_provider" {
  description = "Which cloud to use: 'localstack', 'aws', or 'azure'"
  type        = string
}
```

**Explanation:**
- `variable` = Keyword to define a variable
- `"cloud_provider"` = Name of the variable
- `description` = Human-readable explanation
- `type = string` = The variable must be text (not a number or list)

### Variable with Default Value

```hcl
variable "vm_size" {
  description = "Size of the VM"
  type        = string
  default     = "Standard_B2ms"   ← If not specified, use this value
}
```

### Common VM Sizes (Azure):

| Size | vCPUs | RAM | Use Case | Cost/month |
|------|-------|-----|----------|------------|
| Standard_B1s | 1 | 1 GB | Tiny test | ~$7 |
| Standard_B2ms | 2 | 8 GB | Dev/Demo | ~$60 |
| Standard_D2s_v3 | 2 | 8 GB | Small Prod | ~$70 |
| Standard_D4s_v3 | 4 | 16 GB | Production | ~$140 |

### Local Values

```hcl
locals {
  is_azure = var.cloud_provider == "azure"
  azure_location = var.region == "ap-southeast-1" ? "southeastasia" : var.region
}
```

**Explanation:**
- `locals` = Define computed values used within this module
- `is_azure` = Boolean (true/false) - is this Azure deployment?
- The `?` syntax is a **ternary operator**: `condition ? value_if_true : value_if_false`

---

## azure.tf - Creating the Resources

This file defines all the Azure resources to create. Let's go through each one:

### 1. Public IP Address

```hcl
resource "azurerm_public_ip" "vm" {
  count = local.is_azure && var.enable_public_ip ? 1 : 0

  name                = "${var.project_name}-vm-pip"
  location            = local.azure_location
  resource_group_name = var.resource_group_name
  allocation_method   = "Static"
  sku                 = "Standard"
}
```

**Line-by-line explanation:**

| Line | Meaning |
|------|---------|
| `resource "azurerm_public_ip" "vm"` | Create an Azure Public IP, call it "vm" internally |
| `count = local.is_azure && ... ? 1 : 0` | Only create if Azure AND public IP enabled (1=create, 0=skip) |
| `name = "${var.project_name}-vm-pip"` | Name it "uitgo-vm-pip" (pip = Public IP) |
| `location = local.azure_location` | Put it in Southeast Asia |
| `allocation_method = "Static"` | Keep the same IP (vs Dynamic which changes) |
| `sku = "Standard"` | Standard tier (required for VMs) |

**What is a Public IP?**
It's like your VM's phone number on the internet. Without it, you can't SSH into the VM from your computer.

---

### 2. Network Security Group (NSG)

```hcl
resource "azurerm_network_security_group" "vm" {
  count = local.is_azure ? 1 : 0

  name                = "${var.project_name}-vm-nsg"
  location            = local.azure_location
  resource_group_name = var.resource_group_name

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
  
  # ... more rules ...
}
```

**What is an NSG?**
It's a **firewall** - it controls what network traffic can reach your VM.

**Security Rule Explained:**

| Property | Value | Meaning |
|----------|-------|---------|
| `name` | "SSH" | Name of this rule |
| `priority` | 1001 | Lower = checked first (1-4096) |
| `direction` | "Inbound" | Traffic coming INTO the VM |
| `access` | "Allow" | Allow this traffic (vs "Deny") |
| `protocol` | "Tcp" | TCP protocol (SSH uses TCP) |
| `destination_port_range` | "22" | SSH runs on port 22 |
| `source_address_prefix` | "0.0.0.0/0" | Allow from anywhere (⚠️ restrict in production!) |

---

### 3. Network Interface (NIC)

```hcl
resource "azurerm_network_interface" "vm" {
  count = local.is_azure ? 1 : 0

  name                = "${var.project_name}-vm-nic"
  location            = local.azure_location
  resource_group_name = var.resource_group_name

  ip_configuration {
    name                          = "internal"
    subnet_id                     = var.subnet_id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = var.enable_public_ip ? azurerm_public_ip.vm[0].id : null
  }
}
```

**What is a NIC?**
It's the VM's **network card** - it connects the VM to the virtual network and assigns IP addresses.

**Key points:**
- `subnet_id` = Which subnet to put the VM in
- `private_ip_address_allocation = "Dynamic"` = Azure picks an IP automatically
- `public_ip_address_id` = Attaches the Public IP we created earlier

---

### 4. Cloud-Init Script

```hcl
locals {
  cloud_init_script = <<-EOF
    #cloud-config
    package_update: true
    package_upgrade: true

    packages:
      - docker-ce
      - docker-compose-plugin

    runcmd:
      - systemctl enable docker
      - systemctl start docker
      - usermod -aG docker azureuser
  EOF
}
```

**What is Cloud-Init?**
It's a script that runs **automatically when the VM first boots**. It's like a setup wizard.

**What our script does:**
1. Updates all packages (`apt update && apt upgrade`)
2. Installs Docker and Docker Compose
3. Starts Docker service
4. Adds the user to the docker group

---

### 5. The Virtual Machine

```hcl
resource "azurerm_linux_virtual_machine" "this" {
  count = local.is_azure ? 1 : 0

  name                = "${var.project_name}-vm"
  location            = local.azure_location
  resource_group_name = var.resource_group_name
  size                = var.vm_size              # "Standard_B2ms"
  admin_username      = var.admin_username       # "azureuser"

  network_interface_ids = [
    azurerm_network_interface.vm[0].id           # Connect to our NIC
  ]

  admin_ssh_key {
    username   = var.admin_username
    public_key = file(pathexpand(var.ssh_public_key_path))  # Your SSH key
  }

  os_disk {
    name                 = "${var.project_name}-vm-osdisk"
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"          # Fast SSD
    disk_size_gb         = var.os_disk_size_gb    # 64 GB
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts-gen2"
    version   = "latest"
  }

  custom_data = base64encode(local.cloud_init_script)  # Run our setup script
}
```

**Key Properties Explained:**

| Property | Meaning |
|----------|---------|
| `size` | VM specifications (CPU, RAM) |
| `admin_username` | Username to SSH with |
| `admin_ssh_key` | Your public SSH key for authentication |
| `os_disk` | The main disk with the operating system |
| `source_image_reference` | Which OS to install (Ubuntu 22.04) |
| `custom_data` | The cloud-init script (base64 encoded) |

---

### 6. Data Disk (Extra Storage)

```hcl
resource "azurerm_managed_disk" "data" {
  count = local.is_azure ? 1 : 0

  name                 = "${var.project_name}-vm-datadisk"
  location             = local.azure_location
  resource_group_name  = var.resource_group_name
  storage_account_type = "Premium_LRS"
  create_option        = "Empty"
  disk_size_gb         = var.data_disk_size_gb    # 100 GB
}

resource "azurerm_virtual_machine_data_disk_attachment" "data" {
  count = local.is_azure ? 1 : 0

  managed_disk_id    = azurerm_managed_disk.data[0].id
  virtual_machine_id = azurerm_linux_virtual_machine.this[0].id
  lun                = 0
  caching            = "ReadWrite"
}
```

**Why a separate Data Disk?**
- OS Disk: Contains Ubuntu, system files
- Data Disk: For Docker volumes, databases, your application data

**Benefits:**
- If you need to replace the VM, your data is safe on the separate disk
- Easier to resize or backup

---

## outputs.tf - Getting Information Back

After Terraform creates resources, outputs tell you important information:

```hcl
output "vm_public_ip" {
  description = "Public IP address of the VM"
  value       = local.is_azure && var.enable_public_ip ? azurerm_public_ip.vm[0].ip_address : ""
}

output "ssh_connection_string" {
  description = "SSH command to connect to the VM"
  value       = local.is_azure && var.enable_public_ip ? "ssh ${var.admin_username}@${azurerm_public_ip.vm[0].ip_address}" : ""
}
```

After `terraform apply`, you'll see:
```
vm_public_ip = "20.43.123.45"
ssh_connection_string = "ssh azureuser@20.43.123.45"
```

---

## How It All Connects

```
terraform.tfvars          main.tf                    modules/compute/
┌─────────────────┐      ┌─────────────────┐        ┌─────────────────┐
│ enable_vm = true│ ──▶  │ module "compute"│  ──▶   │ variables.tf    │
│ vm_size = "B2ms"│      │   source = ...  │        │ azure.tf        │
│ ssh_key = "..."│       │   vm_size = ... │        │ outputs.tf      │
└─────────────────┘      └─────────────────┘        └─────────────────┘
                                │                           │
                                ▼                           ▼
                         ┌─────────────────────────────────────┐
                         │            AZURE CLOUD              │
                         │  Creates: VM, Disks, Network, etc.  │
                         └─────────────────────────────────────┘
                                        │
                                        ▼
                         ┌─────────────────────────────────────┐
                         │    terraform output vm_ssh_command  │
                         │    >>> ssh azureuser@20.43.123.45   │
                         └─────────────────────────────────────┘
```

---

## Deploying the VM

### Step 1: Preview Changes
```powershell
terraform plan
```
This shows what will be created (no cost yet!)

### Step 2: Create Resources
```powershell
terraform apply
```
Type "yes" to confirm. **Costs start now.**

### Step 3: Connect to VM
```powershell
# Get the SSH command
terraform output vm_ssh_command

# Connect
ssh azureuser@<IP_ADDRESS>
```

### Step 4: Setup Your App (on the VM)
```bash
cd /opt/uit-go
git clone https://github.com/Ama2352/UIT-Go.git .
docker compose up -d
```

### Step 5: Destroy (Stop Costs)
```powershell
terraform destroy
```
Type "yes" to confirm. **Costs stop after this.**

---

## Quick Reference

| Terraform Command | What It Does |
|-------------------|--------------|
| `terraform init` | Download providers/modules |
| `terraform plan` | Preview changes (no cost) |
| `terraform apply` | Create resources (costs start) |
| `terraform output` | Show output values |
| `terraform destroy` | Delete everything (costs stop) |

| Resource Type | Purpose |
|---------------|---------|
| `azurerm_public_ip` | Internet-accessible IP address |
| `azurerm_network_security_group` | Firewall rules |
| `azurerm_network_interface` | VM's network card |
| `azurerm_linux_virtual_machine` | The actual VM |
| `azurerm_managed_disk` | Extra storage disk |

---

## Cost Summary

| Action | Cost |
|--------|------|
| `terraform plan` | Free |
| `terraform apply` | ~$0.08/hour |
| Running 1 hour then destroy | ~$0.10 |
| Running 1 day | ~$2 |
| Running 1 month | ~$60 |
