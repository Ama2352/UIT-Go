package se360.trip_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se360.trip_service.model.entities.Trip;
import se360.trip_service.model.enums.TripStatus;

import java.util.UUID;
import java.util.List;
import java.time.LocalDateTime;



@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findByPassengerId(UUID passengerId);

    List<Trip> findByDriverId(UUID driverId);

    List<Trip> findByTripStatus(TripStatus status);


    List<Trip> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}