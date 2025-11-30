# UIT-Go - Ride-Hailing Backend System

A cloud-native microservices backend for a ride-hailing application, built as part of the SE360 course at UIT.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Local Development](#local-development)
5. [Deploy to Azure](#deploy-to-azure)
6. [API Endpoints](#api-endpoints)
7. [Documentation](#documentation)

---

## Overview

UIT-Go is a backend system that allows:
- **Passengers** to register, book trips, and track drivers in real-time
- **Drivers** to go online, receive trip requests, and update their location

### Technology Stack

| Component | Technology |
|-----------|------------|
| User Service | NestJS + PostgreSQL |
| Trip Service | Spring Boot + PostgreSQL |
| Driver Service | Spring Boot + Redis |
| Notification Service | NestJS + RabbitMQ + Socket.IO |
| API Gateway | Kong (DB-less mode) |
| Infrastructure | Docker, Terraform, Azure |

---

## Architecture

![Architecture](./assets/UIT-Go%20Architecture.png)

### Services

| Service | Port | Description |
|---------|------|-------------|
| Kong Gateway | 8000 | API routing, JWT validation |
| User Service | 3000 | Authentication, user profiles |
| Trip Service | 8081 | Trip lifecycle, fare calculation |
| Driver Service | 8082 | Location tracking, driver matching |
| Notification Service | 3001 | WebSocket notifications |
| RabbitMQ | 5672, 15672 | Message broker |
| PostgreSQL (User) | 5433 | User database |
| PostgreSQL (Trip) | 5434 | Trip database |
| Redis | 6379 | Driver locations & cache |

---

## Prerequisites

### For Local Development
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (v20.10+)
- [Git](https://git-scm.com/)

### For Azure Deployment
- [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli)
- Azure subscription with credits
- GitHub account (for CI/CD)

---

## Local Development

### Step 1: Clone the Repository

```bash
git clone https://github.com/Ama2352/UIT-Go.git
cd UIT-Go
```

### Step 2: Generate JWT Keys

The system uses RS256 (asymmetric) JWT tokens. Generate key pair:

```bash
# Create keys directory
mkdir -p services/user-service
mkdir -p services/shared

# Generate private key (for User Service)
openssl genrsa -out services/user-service/private.pem 2048

# Generate public key (shared across services)
openssl rsa -in services/user-service/private.pem -pubout -out services/shared/public.pem
```

### Step 3: Create Environment File

Create `.env` file in the root directory:

```bash
# User Service
USERDB_USERNAME=postgres
USERDB_PASSWORD=postgres
USERDB_DATABASE=user-db
DATABASE_URL=postgresql://postgres:postgres@user-postgres:5432/user-db

# Trip Service
TRIPDB_USERNAME=postgres
TRIPDB_PASSWORD=postgres
TRIPDB_DATABASE=trip-db
TRIPDB_URL=jdbc:postgresql://trip-postgres:5432/trip-db

# JWT
JWT_EXPIRES_IN=7d

# RabbitMQ
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
```

### Step 4: Start All Services

```bash
docker compose up -d --build
```

This will start:
- 2 PostgreSQL databases
- 1 Redis instance
- 1 RabbitMQ message broker
- 4 application services (User, Trip, Driver, Notification)
- 1 Kong API Gateway

### Step 5: Verify Services

Wait about 30 seconds for all services to start, then check:

```bash
# Check all containers are running
docker compose ps

# Test Kong Gateway
curl http://localhost:8000

# Test User Service
curl http://localhost:8000/users/health

# Test Trip Service
curl http://localhost:8000/trips/health

# Test Driver Service
curl http://localhost:8000/drivers/health
```

### Step 6: Access Services

| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8000 |
| RabbitMQ Management | http://localhost:15672 (guest/guest) |
| User Service (direct) | http://localhost:3000 |
| Trip Service (direct) | http://localhost:8081 |
| Driver Service (direct) | http://localhost:8082 |
| Notification Service (direct) | http://localhost:3001 |

### Useful Commands

```bash
# View logs for all services
docker compose logs -f

# View logs for specific service
docker compose logs -f user-service

# Restart all services
docker compose restart

# Stop all services
docker compose down

# Stop and remove volumes (reset databases)
docker compose down -v
```

---

## Deploy to Azure

The project uses GitHub Actions for automated deployment to Azure.

### Option 1: Automated Deployment (CI/CD)

#### Prerequisites

1. Create Azure Service Principal:
```bash
az login
az ad sp create-for-rbac \
  --name "uitgo-github-actions" \
  --role Contributor \
  --scopes /subscriptions/<YOUR_SUBSCRIPTION_ID>
```

2. Add GitHub Secrets (Repository → Settings → Secrets):

| Secret | Value |
|--------|-------|
| `AZURE_CLIENT_ID` | App ID from step 1 |
| `AZURE_CLIENT_SECRET` | Password from step 1 |
| `AZURE_SUBSCRIPTION_ID` | Your Azure subscription ID |
| `AZURE_TENANT_ID` | Tenant ID from step 1 |

#### Deploy

1. Go to **Actions** → **Infrastructure - Terraform**
2. Click **Run workflow** → Select **apply**
3. Wait ~10 minutes for VM creation
4. CD workflow will automatically deploy the application

Access at: `http://<VM_PUBLIC_IP>:8000`

### Option 2: Manual Deployment

#### Step 1: Create Azure VM

```bash
# Create resource group
az group create --name uit-go-rg --location southeastasia

# Create VM
az vm create \
  --resource-group uit-go-rg \
  --name uit-go-vm \
  --image Ubuntu2204 \
  --size Standard_B2s \
  --admin-username azureuser \
  --generate-ssh-keys
```

#### Step 2: SSH into VM

```bash
ssh azureuser@<VM_PUBLIC_IP>
```

#### Step 3: Install Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# Logout and login again
```

#### Step 4: Clone and Run

```bash
git clone https://github.com/Ama2352/UIT-Go.git
cd UIT-Go

# Create .env file (same as local development)
nano .env

# Generate JWT keys
openssl genrsa -out services/user-service/private.pem 2048
openssl rsa -in services/user-service/private.pem -pubout -out services/shared/public.pem

# Start services
docker compose up -d --build
```

#### Step 5: Open Firewall Ports

```bash
az vm open-port --resource-group uit-go-rg --name uit-go-vm --port 8000
az vm open-port --resource-group uit-go-rg --name uit-go-vm --port 80
```

---

## API Endpoints

All requests go through Kong Gateway at port 8000.

### User Service (`/users`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/users/register` | Register new user | No |
| POST | `/users/login` | Login, get JWT token | No |
| GET | `/users/profile` | Get current user profile | Yes |
| PUT | `/users/profile` | Update profile | Yes |

### Trip Service (`/trips`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/trips/estimate` | Estimate fare | Yes |
| POST | `/trips` | Create new trip | Yes |
| GET | `/trips/{id}` | Get trip details | Yes |
| POST | `/trips/{id}/cancel` | Cancel trip | Yes |
| POST | `/trips/{id}/start` | Start trip (driver) | Yes |
| POST | `/trips/{id}/complete` | Complete trip (driver) | Yes |

### Driver Service (`/drivers`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| PUT | `/drivers/location` | Update driver location | Yes |
| POST | `/drivers/online` | Go online | Yes |
| POST | `/drivers/offline` | Go offline | Yes |
| POST | `/drivers/trips/{id}/accept` | Accept trip | Yes |
| GET | `/drivers/nearby?lat=...&lng=...` | Find nearby drivers | Yes |

### WebSocket Endpoints

| Endpoint | Service | Description |
|----------|---------|-------------|
| `ws://host:3001/notifications` | Notification Service | Real-time trip updates |
| `ws://host:8082/ws/driver-location` | Driver Service | GPS streaming |

---

## Documentation

| Document | Description |
|----------|-------------|
| [ARCHITECTURE.MD](./ARCHITECTURE.MD) | System architecture diagrams |
| [REPORT_PHASE2.md](./REPORT_PHASE2.md) | Final project report |
| [docs/DESIGN_DECISIONS.md](./docs/DESIGN_DECISIONS.md) | Technology choices and trade-offs |
| [docs/CHALLENGES.md](./docs/CHALLENGES.md) | Challenges faced and solutions |
| [docs/FUTURE.md](./docs/FUTURE.md) | Results and future improvements |
| [docs/ADR/](./docs/ADR/) | Architectural Decision Records |


