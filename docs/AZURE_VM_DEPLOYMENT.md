# Azure VM Deployment Guide

This guide explains how to deploy the UIT-Go project to an Azure Virtual Machine.

## Prerequisites

1. **Azure CLI** installed and logged in (`az login`)
2. **Terraform** installed (v1.0+)
3. **SSH key pair** generated (`ssh-keygen -t rsa -b 4096`)

## VM Sizing Recommendations

| Environment | VM Size | vCPUs | RAM | Monthly Cost* |
|------------|---------|-------|-----|---------------|
| Development/Demo | Standard_B2ms | 2 | 8 GB | ~$60 |
| Small Production | Standard_D2s_v3 | 2 | 8 GB | ~$70 |
| Production | Standard_D4s_v3 | 4 | 16 GB | ~$140 |

*Costs are approximate and vary by region.

## Deployment Steps

### 1. Configure Terraform Variables

Edit `terraform.tfvars`:

```hcl
cloud_provider = "azure"
enable_vm = true

# Choose your VM size
vm_size = "Standard_B2ms"

# Set your SSH key path
ssh_public_key_path = "~/.ssh/id_rsa.pub"

# IMPORTANT: Restrict SSH to your IP in production!
allowed_ssh_cidr = "YOUR_PUBLIC_IP/32"
```

### 2. Deploy Infrastructure

```bash
cd infrastructure/terraform

# Initialize Terraform
terraform init

# Preview changes
terraform plan

# Apply changes
terraform apply
```

### 3. Connect to VM

After deployment, get the SSH command:

```bash
terraform output vm_ssh_command
```

Connect to the VM:
```bash
ssh azureuser@<VM_PUBLIC_IP>
```

### 4. Setup Application

Once connected to the VM, run the setup script:

```bash
# Download and run setup script
curl -fsSL https://raw.githubusercontent.com/Ama2352/UIT-Go/main/infrastructure/scripts/setup-vm.sh | bash
```

Or manually:

```bash
# Clone repository
cd /opt/uit-go
git clone https://github.com/Ama2352/UIT-Go.git .

# Create environment file
cp .env.example .env
nano .env  # Edit with your values

# Start services
docker compose up -d
```

### 5. Access the Application

The API Gateway (Kong) is accessible at:
- **HTTP**: `http://<VM_PUBLIC_IP>:8000`

## What's Included in the VM

The VM is automatically configured with:
- ✅ Ubuntu 22.04 LTS
- ✅ Docker CE + Docker Compose
- ✅ 64GB OS disk (Premium SSD)
- ✅ 100GB data disk for Docker volumes
- ✅ Network Security Group with ports:
  - 22 (SSH)
  - 80 (HTTP)
  - 443 (HTTPS)
  - 8000 (Kong Gateway)
  - 15672 (RabbitMQ Management - restricted to SSH CIDR)

## Security Recommendations

1. **Restrict SSH Access**: Set `allowed_ssh_cidr` to your IP only
2. **Use Strong Passwords**: Update `.env` with secure credentials
3. **Enable Azure Firewall**: Consider adding Azure Firewall for production
4. **Setup SSL/TLS**: Use Let's Encrypt or Azure Application Gateway
5. **Regular Updates**: Keep the VM updated (`sudo apt update && sudo apt upgrade`)

## Monitoring & Maintenance

### Check Service Status
```bash
docker compose ps
docker compose logs -f
```

### Restart Services
```bash
docker compose restart
```

### Update Application
```bash
cd /opt/uit-go
git pull
docker compose build
docker compose up -d
```

### View Resource Usage
```bash
docker stats
```

## Cost Optimization Tips

1. **Use B-series VMs** for development (burstable, cheaper)
2. **Stop VM when not in use**: VMs are billed per hour
3. **Use Azure Reserved Instances** for production (up to 72% savings)
4. **Enable auto-shutdown** for development VMs

## Troubleshooting

### VM not accessible
- Check Network Security Group rules
- Verify public IP is assigned
- Check VM is running in Azure Portal

### Docker services not starting
```bash
# Check Docker status
sudo systemctl status docker

# View service logs
docker compose logs <service-name>
```

### Disk space issues
```bash
# Check disk usage
df -h

# Clean Docker resources
docker system prune -a
```
