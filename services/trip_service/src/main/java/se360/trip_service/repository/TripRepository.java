package se360.trip_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se360.trip_service.model.entities.Trip;
import se360.trip_service.model.enums.TripStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findByPassengerId(UUID passengerId);

    List<Trip> findByDriverId(UUID driverId);

    List<Trip> findByTripStatus(TripStatus status);

    List<Trip> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Modifying
    @Query("UPDATE Trip t SET t.driverId = :driverId, t.tripStatus = :newStatus, t.acceptedAt = :acceptedAt, t.updatedAt = :updatedAt WHERE t.id = :tripId AND t.tripStatus = :oldStatus")
    int assignDriver(
            @Param("tripId") UUID tripId,
            @Param("driverId") UUID driverId,
            @Param("newStatus") TripStatus newStatus,
            @Param("oldStatus") TripStatus oldStatus,
            @Param("acceptedAt") LocalDateTime acceptedAt,
            @Param("updatedAt") LocalDateTime updatedAt);
}