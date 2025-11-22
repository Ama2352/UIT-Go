package se360.driver_service.models;

import java.time.Instant;

public record DriverLocationMessage(
        String driverId,
        double lat,
        double lng,
        Double heading,
        Double speed,
        Instant timestamp) {
}
