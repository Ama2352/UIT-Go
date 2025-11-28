#!/bin/bash
# =============================================================================
# UIT-Go VM Setup Script
# =============================================================================
# Run this script on the Azure VM after SSH-ing into it
# =============================================================================

set -e

echo "=========================================="
echo "UIT-Go VM Setup Script"
echo "=========================================="

# Variables - Update these!
GITHUB_REPO="https://github.com/Ama2352/UIT-Go.git"
BRANCH="main"
APP_DIR="/opt/uit-go"

# Wait for cloud-init to complete
echo "Waiting for cloud-init to complete..."
cloud-init status --wait || true

# Ensure Docker is running
echo "Checking Docker..."
sudo systemctl start docker
sudo systemctl enable docker

# Add current user to docker group if not already
if ! groups | grep -q docker; then
    sudo usermod -aG docker $USER
    echo "Added $USER to docker group. You may need to log out and back in."
fi

# Create app directory if not exists
sudo mkdir -p $APP_DIR
sudo chown $USER:$USER $APP_DIR

# Clone repository
echo "Cloning repository..."
if [ -d "$APP_DIR/.git" ]; then
    echo "Repository already exists, pulling latest..."
    cd $APP_DIR
    git pull origin $BRANCH
else
    git clone --branch $BRANCH $GITHUB_REPO $APP_DIR
    cd $APP_DIR
fi

# Create .env file if not exists
if [ ! -f "$APP_DIR/.env" ]; then
    echo "Creating .env file..."
    cat > $APP_DIR/.env << 'EOF'
# =============================================================================
# UIT-Go Environment Variables
# =============================================================================

# User Service
USERDB_USERNAME=postgres
USERDB_PASSWORD=postgres123
USERDB_DATABASE=userdb
DATABASE_URL=postgresql://postgres:postgres123@user-postgres:5432/userdb

# Trip Service
TRIPDB_USERNAME=postgres
TRIPDB_PASSWORD=postgres123
TRIPDB_DATABASE=tripdb
TRIPDB_URL=jdbc:postgresql://trip-postgres:5432/tripdb

# JWT Configuration
JWT_SECRET=your-super-secret-jwt-key-change-in-production
JWT_EXPIRES_IN=7d

# RabbitMQ
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
EOF
    echo "Created .env file. Please update with secure passwords!"
fi

# Build and start services
echo "Starting services with Docker Compose..."
cd $APP_DIR
docker compose pull || true
docker compose build
docker compose up -d

# Show status
echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
docker compose ps
echo ""
echo "Services are starting up. Access the API at:"
echo "  http://$(curl -s ifconfig.me):8000"
echo ""
echo "Useful commands:"
echo "  docker compose logs -f          # View all logs"
echo "  docker compose logs -f kong     # View Kong logs"
echo "  docker compose ps               # Check status"
echo "  docker compose restart          # Restart all services"
echo "==========================================" 
