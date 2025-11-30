package se360.driver_service.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se360.driver_service.messaging.LocationUpdatePublisher;
import se360.driver_service.models.DriverLocationMessage;

import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private static final String DRIVER_LOCATION_KEY = "driver:locations";
    private static final String DRIVER_STATUS_KEY = "driver:status";
    private static final String DRIVER_META_PREFIX = "driver:meta:";

    private final StringRedisTemplate redisTemplate;
    private final LocationUpdatePublisher locationUpdatePublisher;
    private GeoOperations<String, String> geoOps;
    private HashOperations<String, Object, Object> hashOps;

    @PostConstruct
    void init() {
        this.geoOps = redisTemplate.opsForGeo();
        this.hashOps = redisTemplate.opsForHash();
    }

    public void setDriverOnline(String driverId) {
        log.debug("setDriverOnline called with driverId={}", driverId);
        redisTemplate.opsForHash().put(DRIVER_STATUS_KEY, driverId, "ONLINE");
    }

    public void setDriverOffline(String driverId) {
        log.debug("setDriverOffline called with driverId={}", driverId);
        redisTemplate.opsForHash().put(DRIVER_STATUS_KEY, driverId, "OFFLINE");
        geoOps.remove(DRIVER_LOCATION_KEY, driverId);
    }

    public void updateDriverLocation(String driverId, double latitude, double longitude) {
        log.debug("updateDriverLocation called with driverId={}, latitude={}, longitude={}", driverId, latitude,
                longitude);
        geoOps.add(DRIVER_LOCATION_KEY, new Point(longitude, latitude), driverId);
    }

    public List<String> findNearbyDrivers(double latitude, double longitude, double radiusInKm) {
        log.debug("findNearbyDrivers called with latitude={}, longitude={}, radiusInKm={}", latitude, longitude,
                radiusInKm);
        Circle area = new Circle(new Point(longitude, latitude), new Distance(radiusInKm, Metrics.KILOMETERS));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = geoOps.radius(DRIVER_LOCATION_KEY, area);

        if (results == null)
            return List.of();

        return results.getContent().stream()
                .map(res -> res.getContent().getName())
                .filter(driverId -> "ONLINE".equals(redisTemplate.opsForHash().get(DRIVER_STATUS_KEY, driverId)))
                .toList();
    }

    public void cacheTripPassenger(UUID tripId, UUID passengerId) {
        redisTemplate.opsForValue().set(
                "trip:passenger:" + tripId.toString(),
                passengerId.toString(),
                Duration.ofMinutes(5) // TTL để trip không tồn tại mãi
        );
    }

    public UUID getPassengerIdForTrip(UUID tripId) {
        String value = redisTemplate.opsForValue()
                .get("trip:passenger:" + tripId.toString());

        return value != null ? UUID.fromString(value) : null;
    }



    // === Called by WebSocket handler on every GPS tick ===
    public void handleStreamingLocation(DriverLocationMessage msg) {
        log.debug("handleStreamingLocation called: driverId={}, lat={}, lng={}, heading={}, speed={}, timestamp={}",
                msg.driverId(), msg.lat(), msg.lng(), msg.heading(), msg.speed(), msg.timestamp());

        // 1) Update Redis Geo
        Point position = new Point(msg.lng(), msg.lat());
        log.debug("Updating Redis Geo: key={}, position={}, driverId={}", DRIVER_LOCATION_KEY, position,
                msg.driverId());
        geoOps.add(DRIVER_LOCATION_KEY, position, msg.driverId());

        // 2) Store extra metadata (optional)
        String metaKey = DRIVER_META_PREFIX + msg.driverId();
        log.debug("Storing metadata in Redis Hash: metaKey={}", metaKey);
        if (msg.heading() != null) {
            log.debug("Storing heading: {}", msg.heading());
            hashOps.put(metaKey, "heading", msg.heading().toString());
        }
        if (msg.speed() != null) {
            log.debug("Storing speed: {}", msg.speed());
            hashOps.put(metaKey, "speed", msg.speed().toString());
        }
        String updatedAt = msg.timestamp() != null ? msg.timestamp().toString() : Instant.now().toString();
        log.debug("Storing updatedAt: {}", updatedAt);
        hashOps.put(metaKey, "updatedAt", updatedAt);

        // 3) Publish integration event to RabbitMQ
        log.debug("Publishing location update event to RabbitMQ: {}", msg);
        locationUpdatePublisher.publishLocationUpdate(msg);
    }
}
