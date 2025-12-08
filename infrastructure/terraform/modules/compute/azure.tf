# =============================================================================
# Compute Module - Azure Virtual Machine
# =============================================================================
# Creates an Azure VM configured to run the entire UIT-Go project using Docker
# =============================================================================

# -----------------------------------------------------------------------------
# Public IP Address (optional)
# -----------------------------------------------------------------------------
resource "azurerm_public_ip" "vm" {
  count = local.is_azure && var.enable_public_ip ? 1 : 0

  name                = "${var.project_name}-vm-pip"
  location            = local.azure_location
  resource_group_name = var.resource_group_name
  allocation_method   = "Static"
  sku                 = "Standard"

  tags = {
    Project     = var.project_name
    Environment = "demo"
  }
}

# -----------------------------------------------------------------------------
# Network Security Group
# -----------------------------------------------------------------------------
resource "azurerm_network_security_group" "vm" {
  count = local.is_azure ? 1 : 0

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

  # HTTP standard port
  security_rule {
    name                       = "HTTP"
    priority                   = 1004
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "80"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  # RabbitMQ Management UI (optional - for debugging)
  security_rule {
    name                       = "RabbitMQ-Management"
    priority                   = 1010
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "15672"
    source_address_prefix      = var.allowed_ssh_cidr
    destination_address_prefix = "*"
  }

  tags = {
    Project     = var.project_name
    Environment = "demo"
  }
}

# -----------------------------------------------------------------------------
# Network Interface
# -----------------------------------------------------------------------------
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

  tags = {
    Project     = var.project_name
    Environment = "demo"
  }
}

# -----------------------------------------------------------------------------
# Associate NSG with NIC
# -----------------------------------------------------------------------------
resource "azurerm_network_interface_security_group_association" "vm" {
  count = local.is_azure ? 1 : 0

  network_interface_id      = azurerm_network_interface.vm[0].id
  network_security_group_id = azurerm_network_security_group.vm[0].id
}

# -----------------------------------------------------------------------------
# Cloud-Init Script to Setup Docker and Clone Project
# -----------------------------------------------------------------------------
locals {
  cloud_init_script = <<-EOF
    #cloud-config
    package_update: true
    package_upgrade: true

    packages:
      - apt-transport-https
      - ca-certificates
      - curl
      - gnupg
      - lsb-release
      - git
      - unzip

    runcmd:
      # Install Docker
      - curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
      - echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
      - apt-get update
      - apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      
      # Add user to docker group
      - usermod -aG docker ${var.admin_username}
      
      # Enable and start Docker
      - systemctl enable docker
      - systemctl start docker
      
      # Create app directory
      - mkdir -p /opt/uit-go
      - chown ${var.admin_username}:${var.admin_username} /opt/uit-go
      
      # Clone the repository (user will need to do this manually with proper auth)
      - echo "VM is ready! Clone your repository to /opt/uit-go and run docker compose up -d"
      
      # Setup data disk if attached
      - |
        if [ -e /dev/sdc ]; then
          mkfs.ext4 /dev/sdc
          mkdir -p /data
          mount /dev/sdc /data
          echo "/dev/sdc /data ext4 defaults 0 2" >> /etc/fstab
          chown ${var.admin_username}:${var.admin_username} /data
        fi

    final_message: "UIT-Go VM is ready after $UPTIME seconds"
  EOF
}

# -----------------------------------------------------------------------------
# Virtual Machine
# -----------------------------------------------------------------------------
resource "azurerm_linux_virtual_machine" "this" {
  count = local.is_azure ? 1 : 0

  name                = "${var.project_name}-vm"
  location            = local.azure_location
  resource_group_name = var.resource_group_name
  size                = var.vm_size
  admin_username      = var.admin_username

  network_interface_ids = [
    azurerm_network_interface.vm[0].id
  ]

  admin_ssh_key {
    username   = var.admin_username
    public_key = var.ssh_public_key != "" ? var.ssh_public_key : file(pathexpand(var.ssh_public_key_path))
  }

  os_disk {
    name                 = "${var.project_name}-vm-osdisk"
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"
    disk_size_gb         = var.os_disk_size_gb
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts-gen2"
    version   = "latest"
  }

  custom_data = base64encode(local.cloud_init_script)

  tags = {
    Project     = var.project_name
    Environment = "demo"
  }
}

# -----------------------------------------------------------------------------
# Data Disk for Docker Volumes (optional but recommended)
# -----------------------------------------------------------------------------
resource "azurerm_managed_disk" "data" {
  count = local.is_azure ? 1 : 0

  name                 = "${var.project_name}-vm-datadisk"
  location             = local.azure_location
  resource_group_name  = var.resource_group_name
  storage_account_type = "Premium_LRS"
  create_option        = "Empty"
  disk_size_gb         = var.data_disk_size_gb

  tags = {
    Project     = var.project_name
    Environment = "demo"
  }
}

resource "azurerm_virtual_machine_data_disk_attachment" "data" {
  count = local.is_azure ? 1 : 0

  managed_disk_id    = azurerm_managed_disk.data[0].id
  virtual_machine_id = azurerm_linux_virtual_machine.this[0].id
  lun                = 0
  caching            = "ReadWrite"
}
