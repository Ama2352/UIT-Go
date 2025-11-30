package se360.driver_service.services;

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


    public boolean tryAcquire(UUID tripId, UUID driverId, long ttlSeconds) {
        String key = KEY_PREFIX + tripId;
        String value = driverId.toString();

        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

        return Boolean.TRUE.equals(ok);
    }


    public void release(UUID tripId, UUID driverId) {
        String key = KEY_PREFIX + tripId;
        String current = redisTemplate.opsForValue().get(key);
        if (driverId.toString().equals(current)) {
            redisTemplate.delete(key);
        }
    }
}
