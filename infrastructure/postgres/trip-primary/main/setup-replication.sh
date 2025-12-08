#!/bin/bash
# ============================================
# PostgreSQL Replication Setup Script
# One-time setup script for trip-postgres replication
# ============================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PRIMARY_CONTAINER="trip-postgres"
REPLICA_CONTAINER="trip-replica"
REPLICATION_USER="${TRIPDB_REPLICATION_USER:-replicator}"
REPLICATION_PASSWORD="${TRIPDB_REPLICATION_PASSWORD:-replicator}"
REPLICATION_SLOT="trip_replica_slot"
PRIMARY_DB_USER="${TRIPDB_USERNAME:-postgres}"
PRIMARY_DB_PASSWORD="${TRIPDB_PASSWORD:-postgres}"
REPLICA_VOLUME="trip_replica_data"

echo -e "${GREEN}🔧 PostgreSQL Replication Setup for Trip Service${NC}"
echo "============================================"
echo "Primary Container: $PRIMARY_CONTAINER"
echo "Replica Container: $REPLICA_CONTAINER"
echo "Replication User: $REPLICATION_USER"
echo "Replication Slot: $REPLICATION_SLOT"
echo "============================================"
echo ""

# Function to check if container is running
check_container() {
    if ! docker ps --format '{{.Names}}' | grep -q "^${1}$"; then
        echo -e "${RED}❌ Error: Container '$1' is not running!${NC}"
        echo "Please start it with: docker-compose up -d $1"
        exit 1
    fi
    echo -e "${GREEN}✓${NC} Container '$1' is running"
}

# Function to wait for PostgreSQL to be ready
wait_for_postgres() {
    local container=$1
    local max_attempts=30
    local attempt=0
    
    echo -e "${YELLOW}Waiting for PostgreSQL in $container to be ready...${NC}"
    while [ $attempt -lt $max_attempts ]; do
        if docker exec $container pg_isready -U postgres > /dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} PostgreSQL is ready"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    
    echo -e "${RED}❌ PostgreSQL in $container did not become ready${NC}"
    return 1
}

# Step 1: Check containers
echo -e "${YELLOW}Step 1: Checking containers...${NC}"
check_container $PRIMARY_CONTAINER
check_container $REPLICA_CONTAINER
wait_for_postgres $PRIMARY_CONTAINER
echo ""

# Step 2: Create replication user on primary
echo -e "${YELLOW}Step 2: Creating replication user on primary...${NC}"
docker exec -e PGPASSWORD="$PRIMARY_DB_PASSWORD" $PRIMARY_CONTAINER psql -U $PRIMARY_DB_USER -c "
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_user WHERE usename = '$REPLICATION_USER') THEN
        CREATE USER $REPLICATION_USER WITH REPLICATION ENCRYPTED PASSWORD '$REPLICATION_PASSWORD';
        RAISE NOTICE 'Replication user created';
    ELSE
        ALTER ROLE $REPLICATION_USER WITH REPLICATION ENCRYPTED PASSWORD '$REPLICATION_PASSWORD';
        RAISE NOTICE 'Replication user password refreshed';
    END IF;
END
\$\$;
" || {
    echo -e "${RED}❌ Failed to create replication user${NC}"
    exit 1
}
echo -e "${GREEN}✓${NC} Replication user ready"
echo ""

# Step 3: Create replication slot
echo -e "${YELLOW}Step 3: Creating replication slot...${NC}"
docker exec -e PGPASSWORD="$PRIMARY_DB_PASSWORD" $PRIMARY_CONTAINER psql -U $PRIMARY_DB_USER -c "
SELECT pg_create_physical_replication_slot('$REPLICATION_SLOT')
WHERE NOT EXISTS (
    SELECT 1 FROM pg_replication_slots WHERE slot_name = '$REPLICATION_SLOT'
);
" || {
    echo -e "${RED}❌ Failed to create replication slot${NC}"
    exit 1
}
echo -e "${GREEN}✓${NC} Replication slot created"
echo ""

# Step 4: Stop replica and clean up old data
echo -e "${YELLOW}Step 4: Preparing replica for base backup...${NC}"
echo "Stopping replica container..."
docker-compose stop $REPLICA_CONTAINER 2>/dev/null || true

echo "Removing old replica data volume..."
docker volume rm uit-go-1_$REPLICA_VOLUME 2>/dev/null || {
    echo -e "${YELLOW}⚠${NC}  Volume doesn't exist (this is OK for first run)"
}

echo "Creating fresh replica data volume..."
docker volume create uit-go-1_$REPLICA_VOLUME >/dev/null
echo ""

# Step 5: Perform base backup
echo -e "${YELLOW}Step 5: Performing base backup from primary to replica...${NC}"
echo "This may take a few moments..."
# Run pg_basebackup using a one-off container so postgres isn't running while we write the data dir
docker-compose run --rm \
  -e PGPASSWORD="$REPLICATION_PASSWORD" \
  -v uit-go-1_$REPLICA_VOLUME:/var/lib/postgresql/data \
  $REPLICA_CONTAINER \
  sh -c "rm -rf /var/lib/postgresql/data/* && pg_basebackup -h $PRIMARY_CONTAINER -D /var/lib/postgresql/data -U $REPLICATION_USER -v -P" || {
    echo -e "${RED}❌ Base backup failed${NC}"
    exit 1
}

# Verify base backup completed successfully (check volume contents)
if ! docker-compose run --rm -v uit-go-1_$REPLICA_VOLUME:/var/lib/postgresql/data $REPLICA_CONTAINER \
    test -f /var/lib/postgresql/data/PG_VERSION; then
  echo -e "${RED}❌ Base backup verification failed - PG_VERSION file not found${NC}"
  exit 1
fi
echo -e "${GREEN}✓${NC} Base backup completed and verified"
echo ""

# Step 6: Configure replica for streaming replication
echo -e "${YELLOW}Step 6: Configuring replica for streaming replication...${NC}"
docker-compose run --rm \
  -v uit-go-1_$REPLICA_VOLUME:/var/lib/postgresql/data \
  $REPLICA_CONTAINER sh -c "
    # Verify base backup completed
    if [ ! -f /var/lib/postgresql/data/PG_VERSION ]; then
        echo 'Error: Base backup incomplete'
        exit 1
    fi
    
    # Create postgresql.auto.conf with replication settings
    cat > /var/lib/postgresql/data/postgresql.auto.conf << EOF
# Replication configuration (auto-generated by setup script)
primary_conninfo = 'host=$PRIMARY_CONTAINER port=5432 user=$REPLICATION_USER password=$REPLICATION_PASSWORD'
primary_slot_name = '$REPLICATION_SLOT'
EOF
    
    # Create standby.signal file (tells PostgreSQL this is a replica)
    touch /var/lib/postgresql/data/standby.signal
    
    # Set proper permissions (run as postgres user)
    chown -R 999:999 /var/lib/postgresql/data 2>/dev/null || true
    chmod 700 /var/lib/postgresql/data
" || {
    echo -e "${RED}❌ Failed to configure replica${NC}"
    exit 1
}
echo -e "${GREEN}✓${NC} Replica configured"
echo ""

# Step 7: Restart replica
echo -e "${YELLOW}Step 7: Restarting replica...${NC}"
docker-compose up -d $REPLICA_CONTAINER
sleep 5
wait_for_postgres $REPLICA_CONTAINER
echo ""

# Step 8: Verify replication
echo -e "${YELLOW}Step 8: Verifying replication status...${NC}"
echo "Checking replication slot on primary:"
docker exec -e PGPASSWORD="$PRIMARY_DB_PASSWORD" $PRIMARY_CONTAINER psql -U $PRIMARY_DB_USER -c "
SELECT slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS lag
FROM pg_replication_slots
WHERE slot_name = '$REPLICATION_SLOT';
" || echo -e "${YELLOW}⚠${NC}  Could not check replication slot"

echo ""
echo "Checking replica status:"
docker exec -e PGPASSWORD="$PRIMARY_DB_PASSWORD" $REPLICA_CONTAINER psql -U $PRIMARY_DB_USER -c "
SELECT * FROM pg_stat_wal_receiver;
" 2>/dev/null || echo -e "${YELLOW}⚠${NC}  Replica may still be initializing"

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}✅ Replication setup complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "Next steps:"
echo "1. Test replication by writing to primary and reading from replica"
echo "2. Monitor replication lag: docker exec -e PGPASSWORD=\"$PRIMARY_DB_PASSWORD\" $REPLICA_CONTAINER psql -U $PRIMARY_DB_USER -c \"SELECT * FROM pg_stat_wal_receiver;\""
echo "3. Check replication slot: docker exec -e PGPASSWORD=\"$PRIMARY_DB_PASSWORD\" $PRIMARY_CONTAINER psql -U $PRIMARY_DB_USER -c \"SELECT * FROM pg_replication_slots;\""
echo ""