# CI/CD Guide for UIT-Go

This guide explains CI/CD concepts and how to implement them for the UIT-Go project.

---

## Table of Contents
1. [What is CI/CD?](#what-is-cicd)
2. [CI/CD for Our Project](#cicd-for-our-project)
3. [GitHub Actions Basics](#github-actions-basics)
4. [Implementation Step by Step](#implementation-step-by-step)
5. [Complete Workflow Files](#complete-workflow-files)
6. [How to Set Up](#how-to-set-up)

---

## What is CI/CD?

### CI = Continuous Integration

**"Automatically test code when developers push changes"**

```
Developer pushes code
        │
        ▼
┌─────────────────────────┐
│   CI Pipeline Runs      │
│   • Build the code      │
│   • Run unit tests      │
│   • Run lint checks     │
│   • Build Docker images │
└─────────────────────────┘
        │
        ▼
   ✅ Pass or ❌ Fail
```

**Benefits:**
- Catch bugs early (before they reach production)
- Ensure code quality
- Everyone's code is tested the same way

---

### CD = Continuous Deployment/Delivery

**"Automatically deploy code to servers after tests pass"**

```
CI Pipeline Passes (✅)
        │
        ▼
┌─────────────────────────┐
│   CD Pipeline Runs      │
│   • Push Docker images  │
│   • Deploy to Azure VM  │
│   • Run health checks   │
└─────────────────────────┘
        │
        ▼
   🚀 App is Live!
```

**Two types:**
- **Continuous Delivery**: Deploys automatically to staging, manual approval for production
- **Continuous Deployment**: Deploys automatically to production (fully automated)

---

## CI/CD for Our Project

### The Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           GITHUB REPOSITORY                              │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    Developer pushes to 'main' branch
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         GITHUB ACTIONS (CI)                              │
│                                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │ user-service│  │ trip-service│  │driver-service│  │notification │    │
│  │             │  │             │  │             │  │   service   │    │
│  │ • npm test  │  │ • mvn test  │  │ • mvn test  │  │ • npm test  │    │
│  │ • npm build │  │ • mvn build │  │ • mvn build │  │ • npm build │    │
│  │ • docker    │  │ • docker    │  │ • docker    │  │ • docker    │    │
│  │   build     │  │   build     │  │   build     │  │   build     │    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
│         │                │                │                │            │
│         └────────────────┴────────────────┴────────────────┘            │
│                                    │                                     │
│                          Push to Docker Registry                         │
│                       (GitHub Container Registry)                        │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                           All tests pass ✅
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         GITHUB ACTIONS (CD)                              │
│                                                                          │
│  1. SSH into Azure VM                                                    │
│  2. Pull latest Docker images                                            │
│  3. Run docker compose up -d                                             │
│  4. Run health check                                                     │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            AZURE VM                                      │
│                                                                          │
│   🚀 Application is updated and running!                                 │
│                                                                          │
│   http://20.43.123.45:8000  ← Kong API Gateway                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## GitHub Actions Basics

GitHub Actions uses **YAML files** in `.github/workflows/` folder.

### Basic Structure

```yaml
# .github/workflows/example.yml

name: My Workflow                    # Name shown in GitHub UI

on:                                  # When to run
  push:
    branches: [main]                 # Run when pushing to main
  pull_request:
    branches: [main]                 # Run when PR targets main

jobs:                                # What to do
  build:                             # Job name
    runs-on: ubuntu-latest           # Use Ubuntu VM
    
    steps:                           # Steps in the job
      - name: Checkout code          # Step name
        uses: actions/checkout@v4    # Use pre-made action
      
      - name: Run tests
        run: npm test                # Run shell command
```

### Key Concepts

| Concept | Meaning |
|---------|---------|
| `on:` | Trigger events (push, pull_request, schedule) |
| `jobs:` | Independent tasks that can run in parallel |
| `steps:` | Sequential commands within a job |
| `uses:` | Use a pre-made action from GitHub Marketplace |
| `run:` | Run a shell command |
| `env:` | Environment variables |
| `secrets:` | Encrypted variables (passwords, keys) |

---

## Implementation Step by Step

### Step 1: CI - Test & Build Each Service

For each microservice, we:
1. Check out the code
2. Set up the language (Node.js or Java)
3. Install dependencies
4. Run tests
5. Build Docker image
6. Push to GitHub Container Registry

### Step 2: CD - Deploy to Azure VM

After all services pass:
1. SSH into the Azure VM
2. Pull the new Docker images
3. Restart services with docker compose
4. Verify everything is healthy

---

## Complete Workflow Files

### File 1: CI Workflow

```yaml
# .github/workflows/ci.yml
name: CI - Build and Test

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository_owner }}/uitgo

jobs:
  # =========================================
  # Test and Build: User Service (NestJS)
  # =========================================
  user-service:
    name: User Service
    runs-on: ubuntu-latest
    
    defaults:
      run:
        working-directory: ./services/user-service
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: ./services/user-service/package-lock.json
      
      - name: Install dependencies
        run: npm ci
      
      - name: Run linter
        run: npm run lint
      
      - name: Run tests
        run: npm run test
      
      - name: Build application
        run: npm run build
      
      # Build and push Docker image (only on main branch)
      - name: Login to Container Registry
        if: github.ref == 'refs/heads/main'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push Docker image
        if: github.ref == 'refs/heads/main'
        uses: docker/build-push-action@v5
        with:
          context: ./services/user-service
          push: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}-user-service:latest

  # =========================================
  # Test and Build: Trip Service (Spring Boot)
  # =========================================
  trip-service:
    name: Trip Service
    runs-on: ubuntu-latest
    
    defaults:
      run:
        working-directory: ./services/trip_service
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run tests
        run: ./mvnw test
      
      - name: Build application
        run: ./mvnw package -DskipTests
      
      - name: Login to Container Registry
        if: github.ref == 'refs/heads/main'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push Docker image
        if: github.ref == 'refs/heads/main'
        uses: docker/build-push-action@v5
        with:
          context: ./services/trip_service
          push: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}-trip-service:latest

  # =========================================
  # Test and Build: Driver Service (Spring Boot)
  # =========================================
  driver-service:
    name: Driver Service
    runs-on: ubuntu-latest
    
    defaults:
      run:
        working-directory: ./services/driver-service
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run tests
        run: ./mvnw test
      
      - name: Build application
        run: ./mvnw package -DskipTests
      
      - name: Login to Container Registry
        if: github.ref == 'refs/heads/main'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push Docker image
        if: github.ref == 'refs/heads/main'
        uses: docker/build-push-action@v5
        with:
          context: ./services/driver-service
          push: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}-driver-service:latest

  # =========================================
  # Test and Build: Notification Service (NestJS)
  # =========================================
  notification-service:
    name: Notification Service
    runs-on: ubuntu-latest
    
    defaults:
      run:
        working-directory: ./services/notification-service
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: ./services/notification-service/package-lock.json
      
      - name: Install dependencies
        run: npm ci
      
      - name: Run linter
        run: npm run lint
      
      - name: Run tests
        run: npm run test
      
      - name: Build application
        run: npm run build
      
      - name: Login to Container Registry
        if: github.ref == 'refs/heads/main'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push Docker image
        if: github.ref == 'refs/heads/main'
        uses: docker/build-push-action@v5
        with:
          context: ./services/notification-service
          push: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}-notification-service:latest
```

### File 2: CD Workflow

```yaml
# .github/workflows/cd.yml
name: CD - Deploy to Azure VM

on:
  # Run after CI completes successfully
  workflow_run:
    workflows: ["CI - Build and Test"]
    types:
      - completed
    branches: [main]

jobs:
  deploy:
    name: Deploy to Azure VM
    runs-on: ubuntu-latest
    # Only run if CI was successful
    if: ${{ github.event.workflow_run.conclusion == 'success' }}
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Deploy to Azure VM via SSH
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.AZURE_VM_HOST }}
          username: ${{ secrets.AZURE_VM_USERNAME }}
          key: ${{ secrets.AZURE_VM_SSH_KEY }}
          script: |
            # Navigate to app directory
            cd /opt/uit-go
            
            # Pull latest code
            git pull origin main
            
            # Login to GitHub Container Registry
            echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin
            
            # Pull latest images
            docker compose pull
            
            # Restart services
            docker compose up -d
            
            # Clean up old images
            docker image prune -f
            
            # Show status
            docker compose ps
      
      - name: Health Check
        run: |
          # Wait for services to start
          sleep 30
          
          # Check if Kong is responding
          curl -f http://${{ secrets.AZURE_VM_HOST }}:8000/health || exit 1
          
          echo "✅ Deployment successful!"
```

### File 3: Infrastructure Workflow (Optional)

```yaml
# .github/workflows/infrastructure.yml
name: Infrastructure - Terraform

on:
  push:
    branches: [main]
    paths:
      - 'infrastructure/terraform/**'
  pull_request:
    branches: [main]
    paths:
      - 'infrastructure/terraform/**'

jobs:
  terraform:
    name: Terraform Plan
    runs-on: ubuntu-latest
    
    defaults:
      run:
        working-directory: ./infrastructure/terraform
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Setup Terraform
        uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: 1.6.0
      
      - name: Terraform Init
        run: terraform init
        env:
          ARM_CLIENT_ID: ${{ secrets.AZURE_CLIENT_ID }}
          ARM_CLIENT_SECRET: ${{ secrets.AZURE_CLIENT_SECRET }}
          ARM_SUBSCRIPTION_ID: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
          ARM_TENANT_ID: ${{ secrets.AZURE_TENANT_ID }}
      
      - name: Terraform Format Check
        run: terraform fmt -check
      
      - name: Terraform Validate
        run: terraform validate
      
      - name: Terraform Plan
        run: terraform plan -no-color
        env:
          ARM_CLIENT_ID: ${{ secrets.AZURE_CLIENT_ID }}
          ARM_CLIENT_SECRET: ${{ secrets.AZURE_CLIENT_SECRET }}
          ARM_SUBSCRIPTION_ID: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
          ARM_TENANT_ID: ${{ secrets.AZURE_TENANT_ID }}
      
      # Only apply on main branch (manual approval recommended)
      - name: Terraform Apply
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        run: terraform apply -auto-approve
        env:
          ARM_CLIENT_ID: ${{ secrets.AZURE_CLIENT_ID }}
          ARM_CLIENT_SECRET: ${{ secrets.AZURE_CLIENT_SECRET }}
          ARM_SUBSCRIPTION_ID: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
          ARM_TENANT_ID: ${{ secrets.AZURE_TENANT_ID }}
```

---

## How to Set Up

### Step 1: Create the Workflow Files

Create the folder structure:
```
.github/
└── workflows/
    ├── ci.yml
    └── cd.yml
```

### Step 2: Add Secrets to GitHub

Go to: Repository → Settings → Secrets and variables → Actions

Add these secrets:

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `AZURE_VM_HOST` | `20.43.123.45` | Your VM's public IP |
| `AZURE_VM_USERNAME` | `azureuser` | SSH username |
| `AZURE_VM_SSH_KEY` | `-----BEGIN RSA...` | Your private SSH key |

**How to get SSH private key (Windows PowerShell):**
```powershell
Get-Content ~/.ssh/id_rsa
```
Copy the entire output including `-----BEGIN RSA PRIVATE KEY-----` and `-----END RSA PRIVATE KEY-----`

### Step 3: Enable GitHub Container Registry

1. Go to repository Settings → Actions → General
2. Under "Workflow permissions", select "Read and write permissions"
3. Check "Allow GitHub Actions to create and approve pull requests"

### Step 4: Push and Watch!

```bash
git add .github/
git commit -m "Add CI/CD workflows"
git push origin main
```

Go to your repository → Actions tab to see the workflow running!

---

## Summary

| Phase | What Happens | When |
|-------|--------------|------|
| **CI** | Test, build, create Docker images | On every push/PR |
| **CD** | Deploy to Azure VM | After CI passes on main |
| **Infrastructure** | Update Azure resources | When Terraform files change |

### Visual Timeline

```
Developer pushes code
        │
        ▼ (Immediate)
┌───────────────────┐
│ CI: Test & Build  │  ← ~5-10 minutes
└───────────────────┘
        │
        ▼ (If passes)
┌───────────────────┐
│ CD: Deploy to VM  │  ← ~2-3 minutes
└───────────────────┘
        │
        ▼
   🚀 App Updated!
```

---

## Next Steps

1. Create the workflow files
2. Set up GitHub secrets
3. Push to trigger the first run
4. Monitor in the Actions tab

Would you like me to create these workflow files in your repository?
