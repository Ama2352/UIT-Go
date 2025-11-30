# ADR 005: Choosing PostgreSQL over MongoDB

## Status

**Accepted** — November 2025

---

## Context

UIT-Go requires persistent storage for:

1. **User data**: Profiles, authentication, access tokens
2. **Trip records**: Booking history, status transitions, ratings

Note: Driver Service uses **Redis only** for real-time location and status tracking (see ADR 001). It does not have a traditional relational database.

### Data Characteristics

| Entity | Relationships | Consistency | Query Pattern |
|--------|--------------|-------------|---------------|
| Users | 1:N with Access Tokens | Strong | CRUD, search by email/phone |
| Trips | References User IDs | Strong | Range queries, status filters |
| Trip Ratings | 1:1 with Trips | Strong | Aggregations |

### Key Requirements

| Requirement | Priority | Rationale |
|-------------|----------|-----------|
| ACID transactions | Critical | Data integrity |
| ORM support | High | Prisma (NestJS), JPA (Spring) |
| Schema migrations | High | Evolving data model |
| UUID support | High | Cross-service references |

---

## Decision

We chose **PostgreSQL** with **database-per-service** pattern:
- `userdb` — User Service (Prisma ORM)
- `tripdb` — Trip Service (JPA/Hibernate)

Driver Service uses Redis instead of PostgreSQL (see ADR 001).

---

## Rationale

### 1. Strong ACID Guarantees

User and trip data require transactional consistency:

```sql
-- Atomic trip status update
BEGIN;
UPDATE trips SET trip_status = 'COMPLETED', completed_at = NOW()
WHERE id = $1 AND trip_status = 'IN_PROGRESS';
COMMIT;
```

### 2. Database-Per-Service Pattern

Each service owns its database:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  User Service   │    │  Trip Service   │    │ Driver Service  │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                      │                      │
    ┌────▼────┐            ┌────▼────┐            ┌────▼────┐
    │ userdb  │            │ tripdb  │            │  Redis  │
    │(Postgres)│           │(Postgres)│           │ (Cache) │
    └─────────┘            └─────────┘            └─────────┘
```

**Benefits:**
- Independent schema evolution
- Service isolation
- Clear data ownership

### 3. Actual Schema Implementation

**User Service Schema (Prisma):**
```prisma
model User {
  id        String   @id @default(uuid())
  email     String   @unique
  password  String
  fullName  String
  phoneNumber String? @unique
  userType  UserType @default(PASSENGER)
  
  resetToken          String?   @unique
  resetTokenExpiresAt DateTime?
  
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt
  
  accessTokens AccessToken[]
  
  @@map("users")
}

model AccessToken {
  id        String   @id @default(uuid())
  token     String   @unique
  userId    String
  expiresAt DateTime
  deviceInfo String?
  ipAddress  String?
  createdAt DateTime @default(now())
  
  user User @relation(fields: [userId], references: [id], onDelete: Cascade)
  
  @@map("access_tokens")
  @@index([userId])
  @@index([token])
}
```

**Trip Service Schema (JPA):**
```java
@Entity
@Table(name = "trips",
        indexes = {
                @Index(name = "idx_trips_passenger_id", columnList = "passenger_id"),
                @Index(name = "idx_trips_driver_id", columnList = "driver_id"),
                @Index(name = "idx_trips_status", columnList = "trip_status"),
                @Index(name = "idx_trips_created_at", columnList = "created_at")
        })
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "passenger_id", nullable = false)
    private UUID passengerId;
    
    @Column(name = "driver_id")
    private UUID driverId;
    
    @Column(name = "pickup_lat", precision = 10, scale = 8)
    private BigDecimal pickupLat;
    
    @Column(name = "pickup_lng", precision = 10, scale = 8)
    private BigDecimal pickupLng;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trip_status", length = 20)
    private TripStatus tripStatus;
    
    @Column(name = "estimated_price", precision = 10, scale = 2)
    private BigDecimal estimatedPrice;
    
    @Version
    private int version;  // Optimistic locking
}
```

---

## Alternatives Considered

### MongoDB
- Flexible schema for rapid prototyping
- Native horizontal sharding
- Weaker ACID guarantees
- Complex joins with aggregation pipeline

### MySQL
- Wide adoption
- Fewer advanced features than PostgreSQL
- Weaker JSON support

---

## Consequences

### Positive

- Strong ACID guarantees for user and trip data
- Excellent ORM support (Prisma, JPA)
- Mature ecosystem and tooling
- Rich indexing options (see Trip entity indexes)
- Optimistic locking support (version field)

### Negative

- Requires schema migrations for changes
- Vertical scaling has limits

---

## Docker Compose Configuration

```yaml
services:
  user-postgres:
    image: postgres:15-alpine
    container_name: user-postgres
    environment:
      POSTGRES_USER: ${USERDB_USERNAME}
      POSTGRES_PASSWORD: ${USERDB_PASSWORD}
      POSTGRES_DB: ${USERDB_DATABASE}
    ports:
      - "5433:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  trip-postgres:
    image: postgres:15-alpine
    container_name: trip-postgres
    environment:
      POSTGRES_USER: ${TRIPDB_USERNAME}
      POSTGRES_PASSWORD: ${TRIPDB_PASSWORD}
      POSTGRES_DB: ${TRIPDB_DATABASE}
    ports:
      - "5434:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${TRIPDB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 5
```

---

## Schema Migration Strategy

**Prisma (User Service):**
```bash
npx prisma migrate dev --name add_phone_verification
npx prisma migrate deploy  # Production
```

**Flyway (Trip Service):**
```properties
# application.properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration
```

```
resources/db/migration/
├── V1__create_trips_table.sql
├── V2__add_trip_ratings.sql
└── ...
```

---

## Future Considerations

1. **Read Replicas**: Add PostgreSQL streaming replication for read scaling
2. **Partitioning**: Time-based partitioning for trips table as data grows
3. **Driver Persistence**: If driver profiles need persistence, add driverdb

---

## References

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Prisma with PostgreSQL](https://www.prisma.io/docs/concepts/database-connectors/postgresql)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [ADR 001: Redis for Driver Service](./001-choose-redis-over-dynamodb.md)
