# UIT-Go Deployment Guide

This guide explains how to deploy the UIT-Go project using GitHub Actions CI/CD pipelines.

## Overview

The deployment is **fully automated** using three GitHub Actions workflows:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        DEPLOYMENT PIPELINE                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. CI Workflow (ci.yml)                                                 │
│     ├── Triggered: On push/PR to main                                   │
│     └── Builds Docker images for all services                           │
│                                                                          │
│  2. Terraform Workflow (terraform.yml)                                   │
│     ├── Triggered: Manual or on infrastructure changes                  │
│     ├── Creates Azure VM with Docker pre-installed                      │
│     └── Stores state in Azure Storage (auto-created)                    │
│                                                                          │
│  3. CD Workflow (cd.yml)                                                 │
│     ├── Triggered: After Terraform completes or manual                  │
│     ├── SSHs into Azure VM                                              │
│     ├── Deploys services via docker-compose                             │
│     └── Runs health checks on all endpoints                             │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Prerequisites

### GitHub Secrets Required

Go to **Repository → Settings → Secrets and variables → Actions** and add:

| Secret | Description | How to Get |
|--------|-------------|------------|
| `AZURE_CLIENT_ID` | Azure Service Principal App ID | `az ad sp create-for-rbac` |
| `AZURE_CLIENT_SECRET` | Azure Service Principal Password | From sp creation output |
| `AZURE_SUBSCRIPTION_ID` | Azure Subscription ID | `az account show` |
| `AZURE_TENANT_ID` | Azure Tenant ID | `az account show` |
| `GH_TOKEN_WITH_SECRET_WRITE` | GitHub PAT with `repo` scope | GitHub Settings → Developer settings → PAT |

### Create Azure Service Principal

```bash
# Login to Azure
az login

# Create Service Principal with Contributor role
az ad sp create-for-rbac \
  --name "uitgo-github-actions" \
  --role Contributor \
  --scopes /subscriptions/<YOUR_SUBSCRIPTION_ID>
```

Output will contain: `appId` (CLIENT_ID), `password` (CLIENT_SECRET), `tenant` (TENANT_ID)

---

## Deployment Steps

### Step 1: First-Time Setup (Apply Infrastructure)

1. Go to **Actions** → **Infrastructure - Terraform**
2. Click **Run workflow**
3. Select action: **`apply`**
4. Wait for completion (~5-10 minutes)

This will:
- ✅ Auto-create Azure Storage for Terraform state
- ✅ Create Azure VM with Ubuntu 22.04 + Docker
- ✅ Configure Network Security Group (SSH, HTTP, Kong)
- ✅ Save VM IP and SSH key to GitHub Secrets

### Step 2: Deploy Application

The CD workflow triggers automatically after Terraform, or you can:

1. Go to **Actions** → **CD - Deploy to Azure**
2. Click **Run workflow**
3. Wait for completion (~5-10 minutes)

This will:
- ✅ SSH into the Azure VM
- ✅ Clone/pull the repository
- ✅ Run `docker compose up -d --build`
- ✅ Test all API endpoints

### Step 3: Access Your Application

After deployment, access the API at:

```
http://<VM_PUBLIC_IP>:8000
```

**Available Endpoints:**
- User Service: `http://<VM_IP>:8000/users/*`
- Trip Service: `http://<VM_IP>:8000/trips/*`
- Driver Service: `http://<VM_IP>:8000/drivers/*`

---

## Workflow Details

### CI Workflow (`ci.yml`)

**Triggers:** Push to `main`, Pull Requests to `main`

**Jobs:**
| Job | Description |
|-----|-------------|
| `user-service` | Build & push User Service Docker image |
| `trip-service` | Build & push Trip Service Docker image |
| `driver-service` | Build & push Driver Service Docker image |
| `ci-success` | Summary job |

**Docker Images:** Pushed to `ghcr.io/ama2352/uitgo-*`

---

### Terraform Workflow (`terraform.yml`)

**Triggers:** Manual dispatch, Push to `infrastructure/terraform/**`

**Actions:**
| Action | Description |
|--------|-------------|
| `plan` | Preview infrastructure changes |
| `apply` | Create/update Azure resources |
| `destroy` | Delete all Azure resources |

**Resources Created:**
- Resource Group: `uitgo-rg`
- Virtual Network + Subnet
- Network Security Group (ports: 22, 80, 443, 8000)
- Public IP (static)
- Virtual Machine (Standard_B2ms: 2 vCPU, 8GB RAM)
- OS Disk (64GB) + Data Disk (100GB)

**State Storage (auto-created):**
- Resource Group: `uit-go-tfstate-rg`
- Storage Account: `uitgotfstate`
- Container: `tfstate`

---

### CD Workflow (`cd.yml`)

**Triggers:** After Terraform completes, Manual dispatch

**Steps:**
1. Wait for VM to be ready (60s)
2. SSH into VM
3. Install Docker if missing
4. Clone/pull repository to `/opt/uit-go`
5. Create `.env` file if missing
6. Run `docker compose up -d --build`
7. Wait for services to start (60s)
8. Run health checks on all endpoints

**Health Checks:**
- Kong Gateway: `GET /` (expects 404)
- User Service: `GET /users/ping`
- Trip Service: `GET /trips/ping`
- Driver Service: `GET /drivers/ping`

---

## Managing Infrastructure

### Destroy All Resources

⚠️ **Warning:** This deletes the VM and all data!

1. Go to **Actions** → **Infrastructure - Terraform**
2. Click **Run workflow**
3. Select action: **`destroy`**

### Update Application Only

If you only changed code (not infrastructure):

1. Push changes to `main` branch
2. CI will build new Docker images
3. Manually trigger CD workflow, or SSH into VM and run:

```bash
cd /opt/uit-go
git pull
sudo docker compose up -d --build
```

### View Logs on VM

```bash
# SSH into VM
ssh azureuser@<VM_IP>

# View all logs
cd /opt/uit-go
sudo docker compose logs -f

# View specific service
sudo docker compose logs -f user-service
```

---

## VM Specifications

| Component | Specification |
|-----------|---------------|
| **OS** | Ubuntu 22.04 LTS |
| **Size** | Standard_B2ms (2 vCPU, 8GB RAM) |
| **OS Disk** | 64GB Premium SSD |
| **Data Disk** | 100GB (for Docker volumes) |
| **Location** | Southeast Asia |

### Open Ports

| Port | Service | Access |
|------|---------|--------|
| 22 | SSH | Restricted (configurable) |
| 80 | HTTP | Public |
| 443 | HTTPS | Public |
| 8000 | Kong Gateway | Public |
| 15672 | RabbitMQ UI | Restricted to SSH CIDR |

---

## Cost Estimate

| Resource | Cost (approx.) |
|----------|----------------|
| VM (Standard_B2ms) | ~$60/month |
| OS Disk (64GB) | ~$5/month |
| Data Disk (100GB) | ~$8/month |
| Public IP | ~$3/month |
| **Total** | **~$76/month** |

💡 **Tip:** Use `destroy` action when not in use to stop costs.

---

## Troubleshooting

### Terraform: "Backend storage not found"

The storage is auto-created. If it fails:
1. Check Azure credentials are correct
2. Verify Service Principal has Contributor role
3. Check Azure subscription is active

### CD: "Docker command not found"

The CD workflow auto-installs Docker if missing. If it still fails:
```bash
# SSH into VM and install manually
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
```

### CD: "Connection refused"

1. Check VM is running in Azure Portal
2. Verify `AZURE_VM_HOST` secret is set correctly
3. Check NSG allows SSH from GitHub Actions IPs

### Services not starting

```bash
# SSH into VM
ssh azureuser@<VM_IP>

# Check container status
sudo docker compose ps

# View logs for failed service
sudo docker compose logs <service-name>

# Restart all services
sudo docker compose restart
```

### Health checks failing

Services may need more time to start. Wait 2-3 minutes and check manually:
```bash
curl http://<VM_IP>:8000/api/users/health
```

---

## Local Development

For local development without deploying to Azure:

```bash
# Clone repository
git clone https://github.com/Ama2352/UIT-Go.git
cd UIT-Go

# Create .env file
cp .env.example .env

# Start all services
docker compose up -d

# Access at http://localhost:8000
```

---

*Last Updated: November 2025*
