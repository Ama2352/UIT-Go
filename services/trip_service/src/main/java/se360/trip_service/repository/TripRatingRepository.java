package se360.trip_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se360.trip_service.model.entities.TripRating;

import java.util.UUID;

public interface TripRatingRepository extends JpaRepository<TripRating, UUID> {
    boolean existsByTripId(UUID tripId);
}
