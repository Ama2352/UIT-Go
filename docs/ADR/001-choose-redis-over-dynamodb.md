# ADR 001: Choosing Redis over DynamoDB for Geospatial Driver Tracking

## Status

**Accepted** — November 2025

---

## Context

UIT-Go requires real-time tracking of driver locations for:

1. **Nearby driver search**: Find available drivers within X km of passenger
2. **ETA calculation**: Estimate arrival time based on driver position
3. **Live tracking**: Show driver movement during trip

### Requirements

| Requirement | Priority | Specification |
|-------------|----------|---------------|
| Geospatial queries | Critical | Radius search within 5km |
| Query latency | Critical | < 50ms for search |
| Update frequency | High | Every 5 seconds per driver |
| Data freshness | High | < 10 seconds staleness |
| Scalability | Medium | 10,000 concurrent drivers |

### Options Evaluated

1. **Redis GEO**: In-memory, native geospatial commands
2. **DynamoDB with Geo Library**: AWS managed, partition-based
3. **PostgreSQL PostGIS**: Extension for spatial data

---

## Decision

We chose **Redis 7** with **GEO commands** for real-time driver location storage.

---

## Rationale

### 1. Native Geospatial Commands

Redis provides built-in commands optimized for our use case:

```bash
# Store driver location
GEOADD driver:locations 106.6297 10.8231 "driver-123"

# Find drivers within 5km
GEORADIUS driver:locations 106.6297 10.8231 5 km WITHDIST WITHCOORD

# Get distance to driver
GEODIST driver:locations "driver-123" "passenger-location" km
```

### 2. Sub-Millisecond Latency

In-memory operations deliver consistent performance:

| Operation | Redis | DynamoDB | PostgreSQL |
|-----------|-------|----------|------------|
| Single lookup | < 1ms | 5-10ms | 2-5ms |
| Radius search | 1-5ms | 20-50ms | 10-20ms |
| Update | < 1ms | 5-10ms | 2-5ms |

### 3. Multi-Purpose Data Structures

Redis serves multiple purposes in the Driver Service:

```
┌─────────────────────────────────────────────────────────────────┐
│                      Redis 7 (Driver Service)                    │
├──────────────────┬──────────────────┬───────────────────────────┤
│   GEO Data       │   Hash Data      │      Key-Value            │
│ driver:locations │ driver:status    │ trip:passenger:{id}       │
│ (lat/lng)        │ driver:meta:{id} │ (with 5min TTL)           │
└──────────────────┴──────────────────┴───────────────────────────┘
```

### 4. Driver Status Management

Drivers are tracked with a status hash and explicit cleanup on offline:

```java
// Set driver online (no TTL - explicit status)
redisTemplate.opsForHash().put(DRIVER_STATUS_KEY, driverId, "ONLINE");

// Set driver offline and remove from GEO index
redisTemplate.opsForHash().put(DRIVER_STATUS_KEY, driverId, "OFFLINE");
geoOps.remove(DRIVER_LOCATION_KEY, driverId);
```

---

## Implementation

### Driver Service (Actual Code)

```java
@Service
@RequiredArgsConstructor
public class DriverService {
    
    private static final String DRIVER_LOCATION_KEY = "driver:locations";
    private static final String DRIVER_STATUS_KEY = "driver:status";
    private static final String DRIVER_META_PREFIX = "driver:meta:";
    
    private final StringRedisTemplate redisTemplate;
    private GeoOperations<String, String> geoOps;
    
    public void updateDriverLocation(String driverId, double lat, double lng) {
        geoOps.add(DRIVER_LOCATION_KEY, new Point(lng, lat), driverId);
    }
    
    public List<String> findNearbyDrivers(double lat, double lng, double radiusKm) {
        Circle area = new Circle(
            new Point(lng, lat), 
            new Distance(radiusKm, Metrics.KILOMETERS)
        );
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = 
            geoOps.radius(DRIVER_LOCATION_KEY, area);
        
        if (results == null) return List.of();
        
        return results.getContent().stream()
            .map(res -> res.getContent().getName())
            .filter(driverId -> "ONLINE".equals(
                redisTemplate.opsForHash().get(DRIVER_STATUS_KEY, driverId)))
            .toList();
    }
    
    public void setDriverOnline(String driverId) {
        redisTemplate.opsForHash().put(DRIVER_STATUS_KEY, driverId, "ONLINE");
    }
    
    public void setDriverOffline(String driverId) {
        redisTemplate.opsForHash().put(DRIVER_STATUS_KEY, driverId, "OFFLINE");
        geoOps.remove(DRIVER_LOCATION_KEY, driverId);
    }
}
```

### Docker Compose Configuration

```yaml
services:
  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5
```

---

## Trade-offs Accepted

| Trade-off | Impact | Mitigation |
|-----------|--------|------------|
| **Volatile storage** | Data loss on restart | AOF persistence + periodic backup |
| **Memory cost** | ~100 bytes per driver | Acceptable for 10k drivers (~1MB) |
| **No complex queries** | Limited to radius/box search | Sufficient for driver matching |
| **Single node limit** | ~1M operations/sec | Cluster mode if needed |

---

## Alternatives Considered

### DynamoDB with Geo Library

**Pros**:
- Fully managed by AWS
- Automatic scaling
- Built-in replication

**Cons**:
- Higher latency (5-10ms minimum)
- Complex Geo library setup
- Per-request pricing adds up
- Cannot run locally without LocalStack

### PostgreSQL with PostGIS

**Pros**:
- Already using PostgreSQL
- Powerful spatial queries
- ACID compliance

**Cons**:
- Higher latency for simple lookups
- Additional extension management
- Overkill for ephemeral location data

---

## Consequences

### Positive

- Sub-10ms response time for driver search
- Simple API with native GEO commands
- Reusable for session management and caching
- Lower operational complexity (single Redis instance)

### Negative

- Additional infrastructure component
- Requires proper persistence configuration
- Memory-bound scaling

---

## Future Considerations

1. **Redis Cluster**: For horizontal scaling beyond 10k drivers
2. **Redis Streams**: For location history and analytics
3. **Hybrid Approach**: Hot data in Redis, cold data in PostgreSQL

---

## References

- [Redis GEO Commands](https://redis.io/commands/?group=geo)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Redis Persistence](https://redis.io/docs/management/persistence/)
