# UIT-Go Challenges & Lessons Learned

> **Document Type**: Technical Retrospective  
> **Module**: A — Scalability & Performance  
> **Last Updated**: November 2025  
> **Team**: Nguyen Thanh Kiet, Huynh Chi Hen, Ho Nguyen Minh Sang

---

## Table of Contents

1. [Integration Challenges](#1-integration-challenges)
2. [Infrastructure Challenges](#2-infrastructure-challenges)
3. [Development Challenges](#3-development-challenges)
4. [Security Challenges](#4-security-challenges)
5. [Lessons Learned](#5-lessons-learned)
6. [Best Practices Discovered](#6-best-practices-discovered)

---

## 1. Integration Challenges

### 1.1 RabbitMQ Event Routing

**Challenge**: Designing a consistent exchange-queue-binding pattern across multiple services.

**Symptoms**:
- Messages not reaching consumers
- Duplicate event processing
- Unclear ownership of event schemas

**Solution**:

Established a standardized event routing model:

```
Exchange: trip.events (topic)
├── trip.requested → trip.requested.queue (Driver Service)
├── trip.assigned  → notification_queue
├── trip.started   → notification_queue
├── trip.completed → notification_queue
├── trip.cancelled → notification_queue
└── trip.offered   → notification_queue
```

**Code Pattern (Spring Boot Publisher)**:
```java
@Component
public class TripEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    
    public void publishTripRequested(TripRequestedEvent event) {
        rabbitTemplate.convertAndSend(
            "trip.events",           // exchange
            "trip.requested",        // routing key
            event
        );
    }
}
```

**Lesson**: Define event schemas and routing keys in a shared documentation before implementation.

---

### 1.2 Redis as Primary Driver Data Store

**Challenge**: Using Redis as the sole data store for driver locations and status (no PostgreSQL).

**Symptoms**:
- Stale location data after driver goes offline
- Memory management for GEO entries
- Ensuring data consistency with TTL-based cleanup

**Solution**:

1. **Explicit status management**: Drivers explicitly go ONLINE/OFFLINE
2. **TTL for trip cache**: Passenger-trip mapping expires after 5 minutes
3. **Separate keys per concern**: Locations, status, and metadata in different Redis structures

```java
@Service
public class DriverService {
    private static final String DRIVER_LOCATION_KEY = "driver:locations";
    private static final String DRIVER_STATUS_KEY = "driver:status";
    
    public void updateDriverLocation(String driverId, double lat, double lng) {
        // Store in GEO set for radius queries
        geoOps.add(DRIVER_LOCATION_KEY, new Point(lng, lat), driverId);
    }
    
    public void setDriverOnline(String driverId) {
        hashOps.put(DRIVER_STATUS_KEY, driverId, "ONLINE");
    }
    
    public void setDriverOffline(String driverId) {
        hashOps.put(DRIVER_STATUS_KEY, driverId, "OFFLINE");
        geoOps.remove(DRIVER_LOCATION_KEY, driverId);
    }
    
    public void cacheTripPassenger(UUID tripId, UUID passengerId) {
        // TTL ensures cleanup if trip is abandoned
        stringRedisTemplate.opsForValue().set(
            "trip:passenger:" + tripId,
            passengerId.toString(),
            Duration.ofMinutes(5)
        );
    }
}
```

**Lesson**: Redis can be a primary store for real-time data when persistence is not critical.

---

### 1.3 Kong JWT Validation

**Challenge**: Configuring RS256 JWT validation with a shared public key across services.

**Symptoms**:
- 401 errors despite valid tokens
- Token validation working locally but failing in Docker
- Inconsistent claim extraction

**Solution**:

1. **Standardized JWT structure**:
```json
{
  "sub": "user-uuid",
  "role": "PASSENGER",
  "iss": "uit-go",
  "iat": 1699999999,
  "exp": 1700003599
}
```

2. **Kong consumer configuration** (kong.yml):
```yaml
consumers:
  - username: uit-go-app
    jwt_secrets:
      - algorithm: RS256
        key: uit-go
        rsa_public_key: |
          -----BEGIN PUBLIC KEY-----
          MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...
          -----END PUBLIC KEY-----
```

3. **Service-level JWT plugin**:
```yaml
services:
  - name: trip-service
    url: http://trip-service:8081
    routes:
      - name: trip-route
        paths:
          - /trips
    plugins:
      - name: jwt
        config:
          claims_to_verify:
            - exp
```

**Lesson**: Use environment variables for keys and test JWT validation independently before integrating with Kong.

---

## 2. Infrastructure Challenges

### 2.1 Docker Compose Startup Order

**Challenge**: Services failing because dependencies (PostgreSQL, RabbitMQ) weren't ready.

**Symptoms**:
- "Connection refused" errors on startup
- Inconsistent behavior between restarts
- Services in crash loops

**Solution**:

Health checks with proper conditions:

```yaml
services:
  trip-postgres:
    image: postgres:15-alpine
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${TRIPDB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 5

  trip-service:
    depends_on:
      trip-postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      driver-redis:
        condition: service_healthy
```

**Lesson**: Always use `service_healthy` conditions, not just `service_started`.

---

### 2.2 Terraform State Management

**Challenge**: Managing Terraform state across local development and CI/CD.

**Symptoms**:
- State conflicts between team members
- Lost infrastructure changes
- "Resource already exists" errors

**Solution**:

1. **Remote state backend** (backend.tf):
```hcl
terraform {
  backend "azurerm" {
    resource_group_name  = "uit-go-tfstate-rg"
    storage_account_name = "uitgotfstate"
    container_name       = "tfstate"
    key                  = "terraform.tfstate"
  }
}
```

2. **State locking**: Azure Storage provides automatic locking

3. **Multi-cloud support**: Provider selection via variables
```hcl
# terraform.tfvars
cloud_provider = "azure"  # or "aws" or "localstack"
```

**Lesson**: Set up remote state from day one; migrating later is painful.

---

### 2.3 Azure VM Connectivity

**Challenge**: Services inaccessible from external clients after deployment.

**Symptoms**:
- Local tests pass, production fails
- Timeout on port 8000 (Kong)
- SSH works but HTTP doesn't

**Solution**:

1. **NSG rules in Terraform** (modules/compute/azure.tf):
```hcl
resource "azurerm_network_security_group" "vm" {
  name                = "${var.project_name}-vm-nsg"
  location            = local.azure_location
  resource_group_name = var.resource_group_name

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
}
```

2. **Docker Compose port binding**:
```yaml
services:
  kong:
    ports:
      - "8000:8000"  # Gateway
      - "8003:8003"  # Admin API
```

**Lesson**: Always verify firewall rules AND Docker port bindings match.

---

## 3. Development Challenges

### 3.1 Polyglot Consistency

**Challenge**: Maintaining consistent patterns across Java (Spring) and TypeScript (NestJS) services.

**Areas Affected**:
- Error response formats
- Logging standards
- Configuration management

**Solution**:

1. **Standardized error response**:
```json
{
  "error": {
    "code": "TRIP_NOT_FOUND",
    "message": "Trip with ID xyz not found",
    "timestamp": "2025-11-15T10:30:00Z"
  }
}
```

2. **Shared configuration pattern**:
```yaml
# Both frameworks read from environment
DATABASE_URL=postgresql://...
RABBITMQ_HOST=rabbitmq
REDIS_HOST=redis
```

**Lesson**: Define interface contracts in shared documentation; don't assume frameworks will align automatically.

---

### 3.2 Database Migration Coordination

**Challenge**: Multiple services with independent databases and migration timelines.

**Symptoms**:
- Schema version conflicts
- Migrations running out of order
- Data inconsistencies during updates

**Solution**:

1. **Prisma migrations (User Service)**:
```bash
npx prisma migrate dev --name add_phone_field
npx prisma migrate deploy  # Production
```

2. **Flyway migrations (Trip Service)**:
```
resources/db/migration/
├── V1__create_trips_table.sql
├── V2__add_trip_ratings.sql
└── ...
```

Note: Driver Service uses Redis only and does not require database migrations.

3. **CI/CD migration step**:
```yaml
- name: Run Migrations
  run: |
    docker compose exec -T user-service npx prisma migrate deploy
    # Trip Service uses Flyway with spring.flyway.enabled=true
```

**Lesson**: Automate migrations in CI/CD; never rely on manual execution.

---

## 4. Security Challenges

### 4.1 JWT Token Revocation

**Challenge**: Invalidating tokens before expiration (logout, password change).

**Problem**: JWTs are stateless; once issued, they're valid until expiration.

**Solution**:

Redis-based token blacklist:

```typescript
@Injectable()
export class TokenBlacklistService {
  constructor(private redis: RedisService) {}
  
  async blacklistToken(token: string, expiresAt: Date): Promise<void> {
    const ttl = Math.ceil((expiresAt.getTime() - Date.now()) / 1000);
    await this.redis.set(`blacklist:${token}`, '1', 'EX', ttl);
  }
  
  async isBlacklisted(token: string): Promise<boolean> {
    return await this.redis.exists(`blacklist:${token}`) === 1;
  }
}
```

**Lesson**: Plan for token revocation from the start; adding it later requires touching every authenticated endpoint.

---

### 4.2 Shared Secret Distribution

**Challenge**: Distributing the JWT public key to all services that need to validate tokens.

**Initial Approach**: Copy-paste the key into each service's configuration.

**Problem**: Key rotation required updating 4+ services.

**Solution**:

1. **Shared public key via volume mount**:
```yaml
services:
  user-service:
    volumes:
      - ./services/user-service/private.pem:/app/private.pem:ro
      - ./services/shared/public.pem:/app/public.pem:ro
  
  driver-service:
    volumes:
      - ./services/shared/public.pem:/app/keys/public.pem:ro
  
  notification-service:
    volumes:
      - ./services/shared/public.pem:/app/public.pem:ro
```

2. **Kong validates for protected routes**:
   - User Service `/users`, `/sessions` routes are public (no JWT plugin)
   - Trip Service and Driver Service routes require JWT validation
   - Backend services also validate JWT for additional security

**Lesson**: Centralize authentication at the gateway; don't distribute secrets.

---

## 5. Lessons Learned

### 5.1 Start with Observability

**What We Did**: Added logging as an afterthought.

**What We Should Have Done**: Set up structured logging, metrics, and tracing from day one.

**Impact**: Debugging production issues required SSHing into VM and reading container logs.

---

### 5.2 Contract-First API Design

**What We Did**: Built services independently, aligned contracts later.

**What We Should Have Done**: Define OpenAPI specs before implementation.

**Impact**: Multiple iterations to align request/response formats.

---

### 5.3 Local-First Development

**What We Did**: LocalStack for AWS, Docker Compose for all services.

**What Worked Well**: Developers can run entire stack locally without cloud costs.

**Recommendation**: Always ensure local development parity with production.

---

### 5.4 Documentation as Code

**What We Did**: ADRs in version control, kept alongside code.

**What Worked Well**: Decision history is preserved, searchable, and linked to commits.

**Recommendation**: Treat documentation as a first-class citizen.

---

## 6. Best Practices Discovered

### 6.1 Health Check Standards

Every service exposes `/ping` or `/health`:

```typescript
@Controller('health')
export class HealthController {
  @Get()
  check() {
    return {
      status: 'ok',
      timestamp: new Date().toISOString(),
      service: 'notification-service',
      version: process.env.npm_package_version
    };
  }
}
```

### 6.2 Graceful Shutdown

Handle SIGTERM for clean container stops:

```java
@PreDestroy
public void onShutdown() {
    log.info("Shutting down gracefully...");
    // Complete in-flight requests
    // Close database connections
    // Disconnect from RabbitMQ
}
```

### 6.3 Environment-Based Configuration

Never hardcode connection strings:

```yaml
# docker-compose.yml
environment:
  - DATABASE_URL=${DATABASE_URL:-postgresql://postgres:postgres@postgres:5432/db}
  - RABBITMQ_HOST=${RABBITMQ_HOST:-rabbitmq}
```

### 6.4 Idempotent Event Handlers

Always check for duplicate processing:

```java
@RabbitListener(queues = "trip.assigned")
public void handleTripAssigned(TripAssignedEvent event) {
    if (notificationRepository.existsByEventId(event.getEventId())) {
        log.info("Event already processed: {}", event.getEventId());
        return;
    }
    // Process event
    notificationRepository.save(new ProcessedEvent(event.getEventId()));
}
```

---

## 7. Conclusion

Building UIT-Go provided valuable experience in:

1. **Microservices integration** across different technology stacks
2. **Infrastructure automation** with Terraform and Docker
3. **Event-driven architecture** patterns with RabbitMQ
4. **Real-time communication** using WebSocket

The challenges documented here are common in distributed systems and represent learning opportunities for future projects.

---

## References

- [DESIGN_DECISIONS.md](./DESIGN_DECISIONS.md) - Trade-offs summary
- [ADR Folder](./ADR/) - Architectural decisions
- [DEPLOYMENT.md](./DEPLOYMENT.md) - Production setup guide
