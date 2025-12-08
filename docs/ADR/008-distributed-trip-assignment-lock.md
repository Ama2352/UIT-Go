# ADR 008: Distributed Trip Assignment Lock in Driver Service

## Status

**Accepted** — November 2025

---

## Context

When a passenger requests a trip, multiple nearby drivers receive the trip offer simultaneously via WebSocket notifications. This creates a **race condition** where multiple drivers may attempt to accept the same trip at nearly the same moment.

### The Problem

```
Passenger requests trip
        |
        v
+------------------+
|   Trip Service   |  --> publishes trip.requested
+------------------+
        |
        v
+------------------+
|  Driver Service  |  --> finds nearby drivers, publishes trip.offered to each
+------------------+
        |
        +--> Driver A receives offer --> clicks "Accept"
        +--> Driver B receives offer --> clicks "Accept"  (race condition!)
        +--> Driver C receives offer --> clicks "Accept"
```

Without synchronization, all three drivers could "successfully" accept the trip, leading to:
- Passenger confusion (multiple drivers showing up)
- Driver disputes over the fare
- Inconsistent system state

### Requirements

| Requirement | Priority | Rationale |
|-------------|----------|-----------|
| Exactly-once assignment | Critical | Only one driver can be assigned |
| Low latency | High | First driver to accept should win |
| Distributed safety | High | Works across multiple service instances |
| Automatic cleanup | Medium | Locks should expire if not released |

---

## Decision

We implement a **distributed lock using Redis** in the **Driver Service** to ensure exactly-once trip assignment.

**Lock Key Pattern:** `lock:trip:assign:{tripId}`

**Lock Value:** `{driverId}` (the driver attempting to acquire)

**Lock TTL:** 30 seconds (auto-expires if not released)

---

## Rationale

### 1. Why Redis Distributed Lock?

Redis provides atomic operations that are ideal for distributed locking:

```java
@Service
@RequiredArgsConstructor
public class TripAssignmentLockService {
    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "lock:trip:assign:";

    public boolean tryAcquire(UUID tripId, UUID driverId, long ttlSeconds) {
        String key = KEY_PREFIX + tripId;
        String value = driverId.toString();

        // SETNX with TTL - atomic operation
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

        return Boolean.TRUE.equals(ok);
    }

    public void release(UUID tripId, UUID driverId) {
        String key = KEY_PREFIX + tripId;
        String current = redisTemplate.opsForValue().get(key);
        // Only release if we own the lock
        if (driverId.toString().equals(current)) {
            redisTemplate.delete(key);
        }
    }
}
```

**Key Properties:**
- `setIfAbsent` (SETNX) is atomic - no race conditions
- TTL ensures locks don't persist forever if service crashes
- Lock ownership check prevents accidental release by wrong driver

### 2. Why Lock in Driver Service Instead of Trip Service?

This is a deliberate architectural trade-off based on **domain proximity** and **latency optimization**:

| Aspect | Lock in Trip Service | Lock in Driver Service |
|--------|---------------------|------------------------|
| **Domain Ownership** | Trip owns assignment logic | Driver owns acceptance logic |
| **Redis Access** | Trip Service would need Redis | Driver Service already has Redis |
| **Latency** | Extra network hop to Trip Service | Direct Redis access (sub-ms) |
| **Coupling** | Looser coupling | Tighter coupling with Redis |

**Our Choice:** Driver Service already has Redis for location tracking. Adding lock logic here:
- Avoids introducing Redis dependency to Trip Service
- Minimizes latency for the critical "accept" path
- Keeps all driver-related real-time operations in one service

### 3. Event-Driven State Synchronization

After successful lock acquisition, Driver Service publishes `trip.assigned` event:

```java
@PutMapping("/{driverId}/trips/{tripId}/accept")
public ResponseEntity<String> acceptTrip(
        @PathVariable UUID driverId,
        @PathVariable UUID tripId
) {
    // 1. Attempt to acquire lock
    boolean acquired = tripAssignmentLockService.tryAcquire(tripId, driverId, 30);
    if (!acquired) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Trip already assigned to another driver.");
    }

    // 2. Get passenger info from cache (set during trip.requested)
    UUID passengerId = driverService.getPassengerIdForTrip(tripId);

    // 3. Publish assignment event
    TripAssignedEvent event = TripAssignedEvent.builder()
            .tripId(tripId)
            .driverId(driverId)
            .passengerId(passengerId)
            .build();
    eventPublisher.publishTripAssigned(event);

    return ResponseEntity.ok("Driver accepted trip & trip.assigned published!");
}
```

**Trip Service consumes `trip.assigned`** to update its database:

```
Driver Service                    Trip Service
     |                                 |
     | -- trip.assigned event -------> |
     |                                 | UPDATE trips SET 
     |                                 |   driver_id = ?,
     |                                 |   status = 'ASSIGNED'
     |                                 | WHERE id = ?
```

### 4. Passenger-Trip Cache for Event Context

When `trip.requested` is received, Driver Service caches the passenger ID:

```java
@RabbitListener(queues = RabbitMQConfiguration.REQUESTED_QUEUE)
public void handleTripRequested(TripRequestedEvent event) {
    // Cache passenger ID for later use in trip.assigned event
    driverService.cacheTripPassenger(event.getTripId(), event.getPassengerId());

    // Find and notify nearby drivers
    List<String> drivers = driverService.findNearbyDrivers(
            event.getPickupLat(), event.getPickupLng(), 3.0);

    for (String driverId : drivers) {
        eventPublisher.publishTripOffered(TripOfferedEvent.builder()
                .tripId(event.getTripId())
                .driverId(UUID.fromString(driverId))
                .pickupLat(event.getPickupLat())
                .pickupLng(event.getPickupLng())
                .build());
    }
}
```

---

## Trade-offs Accepted

| Trade-off | Impact | Mitigation |
|-----------|--------|------------|
| **Cross-service data** | Trip state split between services | Event sourcing ensures eventual consistency |
| **Lock in "wrong" service** | Purists may argue Trip should own | Pragmatic: latency + existing Redis |
| **No distributed transaction** | Assignment not atomic with DB update | TTL + idempotent event handling |
| **Cache dependency** | Passenger ID must be cached | TTL ensures cache validity matches trip lifecycle |

---

## Alternatives Considered

### 1. Optimistic Locking in Trip Service Database

```sql
UPDATE trips SET driver_id = ?, status = 'ASSIGNED', version = version + 1
WHERE id = ? AND status = 'SEARCHING' AND version = ?
```

**Pros:**
- Pure database solution
- Strong consistency

**Cons:**
- Higher latency (DB round-trip vs Redis)
- Requires Driver Service to call Trip Service API
- More complex error handling

### 2. Saga Pattern with Compensation

**Pros:**
- Clean separation of concerns
- Explicit failure handling

**Cons:**
- Significant complexity for MVP
- Overkill for single-lock scenario

### 3. Message Queue with Single Consumer

**Pros:**
- Natural serialization of requests

**Cons:**
- Added latency
- Queue becomes bottleneck

---

## Consequences

### Positive

- Sub-millisecond lock acquisition (Redis is fast)
- Exactly-once trip assignment guaranteed
- Automatic lock expiry prevents deadlocks
- Leverages existing Redis infrastructure
- Clear conflict response (HTTP 409) for failed attempts

### Negative

- Trip assignment logic split across services
- Requires careful TTL tuning (30s chosen for driver response time)
- Driver Service has more responsibility than typical "resource" service

---

## Sequence Diagram

```
Passenger          Trip Service       Driver Service        Redis           Notification
    |                   |                   |                 |                   |
    |-- Create Trip --->|                   |                 |                   |
    |                   |-- trip.requested->|                 |                   |
    |                   |                   |--cache passenger|                   |
    |                   |                   |--find drivers-->|                   |
    |                   |                   |<--driver list---|                   |
    |                   |                   |                 |                   |
    |                   |                   |-- trip.offered----------------->|   |
    |                   |                   |                 |            (notify drivers)
    |                   |                   |                 |                   |
    |                   |                   |<-- Driver A accepts              |
    |                   |                   |--SETNX lock---->|                   |
    |                   |                   |<-----OK---------|                   |
    |                   |                   |                 |                   |
    |                   |                   |<-- Driver B accepts              |
    |                   |                   |--SETNX lock---->|                   |
    |                   |                   |<----FAIL--------|                   |
    |                   |                   |--409 Conflict-->|                   |
    |                   |                   |                 |                   |
    |                   |<-trip.assigned----|                 |                   |
    |                   |--update DB------->|                 |                   |
    |                   |                   |-- trip.assigned---------------->|   |
    |<------------------(notify passenger)----------------------|                   |
```

---

## Future Improvements

1. **Move Lock to Trip Service**: When Trip Service adds Redis for caching, migrate lock logic
2. **Redlock Algorithm**: For multi-Redis-instance deployments
3. **Lock Metrics**: Monitor lock contention and acquisition times
4. **Graceful Lock Transfer**: Allow assignment cancellation with lock release

---

## References

- [Redis SET with NX and EX](https://redis.io/commands/set/)
- [Distributed Locks with Redis](https://redis.io/docs/manual/patterns/distributed-locks/)
- [Martin Kleppmann on Distributed Locking](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)
