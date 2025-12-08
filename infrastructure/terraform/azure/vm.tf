# =============================================================================
# Virtual Machine Resources
# =============================================================================

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

    final_message: "UIT-Go VM is ready after $UPTIME seconds"
  EOF
}

resource "azurerm_linux_virtual_machine" "main" {
  name                = "${var.project_name}-vm"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  size                = var.vm_size
  admin_username      = var.admin_username
  network_interface_ids = [
    azurerm_network_interface.vm.id,
  ]

  # Primary SSH key (GitHub Actions generated)
  admin_ssh_key {
    username   = var.admin_username
    public_key = var.ssh_public_key
  }

  # Additional SSH keys (local developer keys, etc.)
  dynamic "admin_ssh_key" {
    for_each = var.additional_ssh_keys
    content {
      username   = var.admin_username
      public_key = admin_ssh_key.value
    }
  }

  os_disk {
    name                 = "${var.project_name}-vm-osdisk"
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
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
    Environment = var.environment
  }
}
