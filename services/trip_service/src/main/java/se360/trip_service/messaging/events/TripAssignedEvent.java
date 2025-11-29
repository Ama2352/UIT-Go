package se360.trip_service.messaging.events;

import lombok.Data;
import java.util.UUID;

@Data
public class TripAssignedEvent {
    private UUID tripId;
    private UUID driverId;
}
