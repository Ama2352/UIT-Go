package se360.trip_service.model.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_ratings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripRating {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID tripId;

    @Column(nullable = false)
    private UUID passengerId;

    @Column(nullable = false)
    private UUID driverId;

    @Column(nullable = false)
    private int rating; // 1–5

    private String feedback;

    private LocalDateTime createdAt = LocalDateTime.now();
}
