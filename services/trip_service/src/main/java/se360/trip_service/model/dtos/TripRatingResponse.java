package se360.trip_service.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TripRatingResponse {
    private int rating;
    private String feedback;
    private LocalDateTime createdAt;
}
