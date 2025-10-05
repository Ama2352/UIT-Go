package se360.driver_service.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverService {

    private static final String DRIVER_LOCATION_KEY = "driver:locations";
    private static final String DRIVER_STATUS_KEY = "driver:status";

    private final StringRedisTemplate redisTemplate;
    private final GeoOperations<String, String> geoOps;

    public DriverService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.geoOps = redisTemplate.opsForGeo();
    }

    public void setDriverOnline(String driverId) {
        redisTemplate.opsForHash().put(DRIVER_STATUS_KEY, driverId, "ONLINE");
    }

    public void setDriverOffline(String driverId) {
        redisTemplate.opsForHash().put(DRIVER_STATUS_KEY, driverId, "OFFLINE");
        geoOps.remove(DRIVER_LOCATION_KEY, driverId);
    }

    public void updateDriverLocation(String driverId, double latitude, double longitude) {
        geoOps.add(DRIVER_LOCATION_KEY, new Point(longitude, latitude), driverId);
    }

    public List<String> findNearbyDrivers(double latitude, double longitude, double radiusInKm) {
        Circle area = new Circle(new Point(longitude, latitude), new Distance(radiusInKm, Metrics.KILOMETERS));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                geoOps.radius(DRIVER_LOCATION_KEY, area);

        if(results == null) return List.of();

        return results.getContent().stream()
                .map(res -> res.getContent().getName())
                .filter(driverId -> "ONLINE".equals(redisTemplate.opsForHash().get(DRIVER_STATUS_KEY, driverId)))
                .toList();
    }

}
