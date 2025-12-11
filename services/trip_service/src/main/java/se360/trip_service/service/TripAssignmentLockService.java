package se360.trip_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripAssignmentLockService {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "lock:trip:assign:";

    /**
     * Attempt to acquire a lock for trip assignment.
     * Uses SETNX (SET if Not eXists) with TTL for atomic lock acquisition.
     *
     * @param tripId     The trip to lock
     * @param driverId   The driver attempting to acquire the lock
     * @param ttlSeconds Time-to-live for the lock
     * @return true if lock acquired, false if already held by another driver
     */
    public boolean tryAcquire(UUID tripId, UUID driverId, long ttlSeconds) {
        String key = KEY_PREFIX + tripId;
        String value = driverId.toString();

        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

        return Boolean.TRUE.equals(ok);
    }

    /**
     * Release a lock for trip assignment.
     * Only releases if the current holder matches the driverId.
     *
     * @param tripId   The trip to unlock
     * @param driverId The driver releasing the lock
     */
    public void release(UUID tripId, UUID driverId) {
        String key = KEY_PREFIX + tripId;
        String current = redisTemplate.opsForValue().get(key);
        if (driverId.toString().equals(current)) {
            redisTemplate.delete(key);
        }
    }
}
