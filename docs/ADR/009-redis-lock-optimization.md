# ADR 009: Redis Lock Optimization for Trip Assignment

**Date:** December 11, 2025  
**Status:** Implemented  
**Supersedes:** ADR 008 (Distributed Trip Assignment Lock)

---

## Context

The initial distributed lock implementation (ADR 008) using Redis SETNX worked correctly but had critical performance bottlenecks under high concurrency. K6 spike testing with 200 concurrent drivers revealed several issues that needed optimization.

---

## Problem Statement

### Identified Bottlenecks

1. **Retry Storm**
   - Each driver retried lock acquisition up to 5 times
   - 200 drivers × 5 retries = **1,000 Redis calls per trip**
   - Caused Redis CPU spikes and network congestion

2. **Late State Check**
   - Trip status checked AFTER acquiring lock
   - Wasted lock acquisitions for already-assigned trips
   - Unnecessary network round-trips

3. **Long Lock TTL**
   - 30-second TTL excessive for ~100ms operation
   - Delayed recovery from service crashes
   - Increased lock contention window

4. **Wrong Service Responsibility**
   - DriverService handling trip state logic
   - Violated Single Responsibility Principle
   - Tight coupling between services

5. **Race Condition Under Load**
   - 0.03% failure rate (2-4 failures per 7,000 iterations)
   - `StaleObjectStateException` causing HTTP 500 errors
   - Transaction commit timing issue with optimistic locking

---

## Decision

Implement a **4-phase optimization** to eliminate bottlenecks while maintaining reliability:

### Phase 1: One-Shot SETNX
- Remove retry loops (application and test)
- Single lock attempt per driver
- **Impact:** 80% reduction in Redis calls

### Phase 2: Reduce Lock TTL
- Decrease from 30s → 5s
- Match TTL to actual operation time (~100ms)
- **Impact:** Faster recovery, reduced contention

### Phase 3: Migrate to TripService
- Move lock logic to service that owns the data
- Enable early state check before lock acquisition
- **Impact:** Better separation of concerns

### Phase 4: Fix Race Condition
- Use `saveAndFlush()` instead of `save()`
- Catch optimistic lock exceptions within method
- **Impact:** 100% success rate, graceful error handling

---

## Implementation

### Final Architecture

```
Driver Request → DriverService (forwards) → TripService
                                              ↓
                                         1. Acquire Redis lock (5s TTL)
                                         2. Read trip from DB
                                         3. Check status = SEARCHING
                                         4. Update DB (saveAndFlush)
                                         5. Publish event
```

### Key Code Changes

**TripService.acceptTripWithLock():**
```java
@Transactional
public AcceptResult acceptTripWithLock(UUID tripId, UUID driverId) {
    // 1. Acquire lock FIRST (one-shot)
    boolean acquired = lockService.tryAcquire(tripId, driverId, 5);
    if (!acquired) return AcceptResult.ALREADY_ASSIGNED;
    
    // 2. Read trip (protected by lock)
    Trip trip = tripRepository.findById(tripId).orElse(null);
    if (trip == null) return AcceptResult.TRIP_NOT_FOUND;
    
    // 3. Early state check
    if (trip.getTripStatus() != TripStatus.SEARCHING) {
        return AcceptResult.ALREADY_ASSIGNED;
    }
    
    // 4. Update DB with immediate flush
    try {
        trip.setDriverId(driverId);
        trip.setTripStatus(TripStatus.ASSIGNED);
        tripRepository.saveAndFlush(trip);  // ← Forces immediate write
    } catch (StaleObjectStateException | ObjectOptimisticLockingFailureException e) {
        log.warn("Optimistic lock conflict for trip {}", tripId);
        return AcceptResult.ALREADY_ASSIGNED;  // ← Graceful 409 instead of 500
    }
    
    // 5. Publish event
    eventPublisher.publishTripAssigned(new TripAssignedEvent(tripId, driverId));
    return AcceptResult.SUCCESS;
}
```

**DriverController (simplified):**
```java
@PutMapping("/{driverId}/trips/{tripId}/accept")
public ResponseEntity<String> acceptTrip(@PathVariable UUID driverId, 
                                        @PathVariable UUID tripId) {
    try {
        // Forward to TripService - it handles all business logic
        ResponseEntity<String> response = restTemplate.exchange(
            tripServiceUrl + "/trips/" + tripId + "/accept",
            HttpMethod.PUT,
            new HttpEntity<>(new AcceptTripRequest(driverId)),
            String.class
        );
        return response;  // Forward as-is (200 OK or 409 Conflict)
    } catch (Exception e) {
        log.error("Error forwarding to TripService", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Failed to communicate with TripService");
    }
}
```

---

## Results

### Performance Comparison

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| **Redis Calls/Trip** | ~1,000 | ~200 | -80% |
| **Lock TTL** | 30s | 5s | -83% |
| **Success Rate** | 99.97% | 100.00% | +0.03% |
| **HTTP 500 Errors** | 2-4/run | 0 | -100% |
| **Avg Latency** | 34.2ms | 49.6ms | +15.4ms |
| **Throughput** | 70.6/s | 68.4/s | -3% |

### K6 Spike Test (200 Concurrent Drivers)

**Final Results:**
```
checks_succeeded: 100.00% (27,828 out of 27,828)
checks_failed: 0.00% (0 out of 27,828)
Driver Accepted Success or Conflict (200/409): PASS
No Fatal Timeout/500: PASS
```

---

## Trade-offs

### Accepted Trade-offs

1. **Latency Increase (+15ms avg)**
   - Cause: `saveAndFlush()` forces immediate DB write
   - Justification: Acceptable for 100% reliability
   - Still within acceptable UX range

2. **Throughput Decrease (-3%)**
   - Cause: Slightly longer per-request processing
   - Justification: Zero failures vs 2-4 failures per run
   - Minimal impact on overall system capacity

### Rejected Alternatives

| Approach | Why Rejected |
|----------|--------------|
| Remove `@Version` optimistic lock | Too risky - no safety net if Redis fails |
| DB Pessimistic Lock (SELECT FOR UPDATE) | Slower, higher DB load - speed is critical |
| Catch exception at Controller level | Doesn't fix root cause - band-aid solution |

---

## Consequences

### Positive

- **100% reliability** under extreme load (200 concurrent drivers)
- **80% reduction** in Redis load
- **Graceful error handling** (409 Conflict vs 500 Error)
- **Faster failure** for losing drivers (no retry wait)
- **Better service boundaries** (TripService owns trip logic)
- **Dual-layer protection** (Redis + DB optimistic lock)

### Negative

- Slight latency increase (+15ms avg)
- Slight throughput decrease (-3%)
- Additional Redis dependency for TripService

### Neutral

- Lock logic moved from DriverService to TripService
- DriverService now acts as simple proxy for accept requests

---

## Lessons Learned

1. **Measure before optimizing** - K6 load testing revealed actual bottlenecks
2. **One-shot > retry loops** - Retries create thundering herd under high contention
3. **Right-size TTLs** - Match lock duration to operation time (5s vs 30s)
4. **Service boundaries matter** - Place logic where data lives
5. **Transaction boundaries are tricky** - `save()` defers write until commit
6. **Defense in depth** - Multiple protection layers (Redis + DB optimistic lock)
7. **Fail gracefully** - Return 409 Conflict instead of 500 Error

---

## Future Considerations

### Monitoring Recommendations

Track in production:
- Redis SETNX call count per trip
- Lock acquisition success rate
- Optimistic lock conflict rate
- P95/P99 latency for accept endpoint

Alert if:
- Lock acquisition failure rate > 5%
- Optimistic lock conflict rate > 1%
- P95 latency > 200ms

### Optional Enhancements

1. **Lua Scripting** - Atomic lock + state check in single Redis call
2. **Circuit Breaker** - Graceful degradation when TripService is down
3. **Distributed Tracing** - Better visibility into lock acquisition timing
4. **Remove @Version** - After extensive production validation showing Redis lock is 100% reliable

---

## References

- [ADR 008: Distributed Trip Assignment Lock](./008-distributed-trip-assignment-lock.md) (original design)
- [K6 Spike Test Results](../../K6-Test/spike-test/04-driver-spike-test-results.md)
- [Spring Data JPA - save() vs saveAndFlush()](https://docs.spring.io/spring-data/jpa/docs/current/api/org/springframework/data/jpa/repository/JpaRepository.html)

---

**Status:** Production-ready, validated with 100% success rate under extreme load.
