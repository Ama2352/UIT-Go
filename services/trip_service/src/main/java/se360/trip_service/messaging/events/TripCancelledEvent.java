package se360.trip_service.messaging.events;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCancelledEvent {
    private UUID tripId;
    private UUID passengerId;
    private UUID driverId;
    private String cancelledBy;
}
