package se360.trip_service.messaging.events;

import lombok.*;
import java.util.UUID;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCompletedEvent {
    private UUID tripId;
    private UUID passengerId;
    private UUID driverId;
    private BigDecimal finalPrice;
}
