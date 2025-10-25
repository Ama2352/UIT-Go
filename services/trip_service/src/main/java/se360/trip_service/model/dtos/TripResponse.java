package se360.trip_service.model.dtos;

import lombok.Builder;
import lombok.Data;
import se360.trip_service.model.enums.TripStatus;
import se360.trip_service.model.enums.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TripResponse {
    private UUID id;
    private UUID passengerId;
    private UUID driverId;

    private String pickupAddress;
    private String dropoffAddress;
    private VehicleType vehicleType;

    private TripStatus tripStatus;
    private BigDecimal distanceKm;
    private BigDecimal estimatedPrice;
    private BigDecimal finalPrice;

    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
