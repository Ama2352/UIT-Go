package se360.driver_service.messaging.events;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripOfferedEvent {
    private UUID tripId;
    private UUID driverId;
    private UUID passengerId;
    private double pickupLat;
    private double pickupLng;
    private double dropoffLat;
    private double dropoffLng;
    private String vehicleType;
}
