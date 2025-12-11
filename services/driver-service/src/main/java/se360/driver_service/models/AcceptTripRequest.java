package se360.driver_service.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for forwarding accept trip request to TripService.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptTripRequest {
    private UUID driverId;
}
