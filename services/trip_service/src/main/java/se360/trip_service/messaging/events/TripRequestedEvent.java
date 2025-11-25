package se360.trip_service.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TripRequestedEvent {

    private UUID tripId;
    private UUID passengerId;

    private double pickupLat;
    private double pickupLng;

    private double dropoffLat;
    private double dropoffLng;

    private String vehicleType;
}
