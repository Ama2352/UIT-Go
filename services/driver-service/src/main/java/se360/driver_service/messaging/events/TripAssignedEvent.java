package se360.driver_service.messaging.events;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripAssignedEvent {
    private UUID tripId;
    private UUID driverId;
}
