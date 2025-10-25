package se360.trip_service.model.dtos;

import lombok.Data;

import java.util.UUID;

@Data
public class RateTripRequest {
    private UUID passengerId;
    private UUID driverId;
    private int rating; // 1–5
    private String feedback;
}
