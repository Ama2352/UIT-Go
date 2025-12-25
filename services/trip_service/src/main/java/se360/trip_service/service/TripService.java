package se360.trip_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

import se360.trip_service.mapper.TripMapper;
import se360.trip_service.messaging.events.*;
import se360.trip_service.messaging.publisher.TripEventPublisher;
import se360.trip_service.model.dtos.CreateTripRequest;
import se360.trip_service.model.dtos.TripResponse;
import se360.trip_service.model.dtos.EstimateFareResponse;
import se360.trip_service.model.dtos.EstimateFareRequest;
import se360.trip_service.model.dtos.RateTripRequest;
import se360.trip_service.model.entities.Trip;
import se360.trip_service.model.entities.TripRating;
import se360.trip_service.model.enums.AcceptResult;
import se360.trip_service.model.enums.TripStatus;
import se360.trip_service.model.enums.VehicleType;
import se360.trip_service.repository.TripRepository;
import se360.trip_service.repository.TripRatingRepository;
import se360.trip_service.util.DistanceUtil;
import se360.trip_service.model.dtos.TripRatingResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TripService {

    private final TripEventPublisher eventPublisher;
    private final TripRepository tripRepository;
    private final TripMapper tripMapper;
    private final TripRatingRepository tripRatingRepository;
    private final TripAssignmentLockService lockService;
    private final TransactionTemplate transactionTemplate;

    // ░░░ ESTIMATE FARE ░░░
    public EstimateFareResponse estimateFare(EstimateFareRequest req) {
        BigDecimal distanceKm = DistanceUtil.calculateDistanceKm(
                req.getPickupLat(),
                req.getPickupLng(),
                req.getDropoffLat(),
                req.getDropoffLng());

        BigDecimal estimatedPrice = calculateFare(distanceKm, req.getVehicleType(), false);

        return new EstimateFareResponse(
                distanceKm.setScale(2, RoundingMode.HALF_UP),
                estimatedPrice.setScale(0, RoundingMode.HALF_UP));
    }

    // ░░░ CREATE TRIP + publish trip.requested ░░░
    public TripResponse createTrip(CreateTripRequest req) {
        Trip trip = tripMapper.toEntity(req);

        trip.setTripStatus(TripStatus.SEARCHING);
        trip.setCreatedAt(LocalDateTime.now());
        trip.setUpdatedAt(LocalDateTime.now());

        BigDecimal distanceKm = DistanceUtil.calculateDistanceKm(
                req.getPickupLat(),
                req.getPickupLng(),
                req.getDropoffLat(),
                req.getDropoffLng());

        trip.setDistanceKm(distanceKm);
        trip.setEstimatedPrice(calculateFare(distanceKm, req.getVehicleType(), false));

        Trip savedTrip = tripRepository.save(trip);

        TripRequestedEvent event = TripRequestedEvent.builder()
                .tripId(savedTrip.getId())
                .passengerId(savedTrip.getPassengerId())
                .pickupLat(savedTrip.getPickupLat().doubleValue())
                .pickupLng(savedTrip.getPickupLng().doubleValue())
                .dropoffLat(savedTrip.getDropoffLat().doubleValue())
                .dropoffLng(savedTrip.getDropoffLng().doubleValue())
                .vehicleType(savedTrip.getVehicleType().name())
                .build();

        eventPublisher.publishTripRequested(event);

        return tripMapper.toResponse(savedTrip);
    }

    public List<TripResponse> getAllTrips() {
        return tripRepository.findAll()
                .stream()
                .map(tripMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TripResponse> getTripById(UUID id) {
        return tripRepository.findById(id)
                .map(tripMapper::toResponse);
    }

    // ░░░ CANCEL TRIP + publish trip.cancelled ░░░
    public Optional<TripResponse> cancelTrip(UUID id, String cancelledBy) {
        return tripRepository.findById(id).map(trip -> {

            if (trip.getTripStatus() != TripStatus.SEARCHING &&
                    trip.getTripStatus() != TripStatus.ASSIGNED) {
                throw new IllegalStateException("Trip can only be cancelled before started.");
            }

            trip.setTripStatus(TripStatus.CANCELLED);
            trip.setCancelledBy(cancelledBy);
            trip.setCancelledAt(LocalDateTime.now());
            trip.setUpdatedAt(LocalDateTime.now());

            Trip saved = tripRepository.save(trip);

            TripCancelledEvent event = TripCancelledEvent.builder()
                    .tripId(saved.getId())
                    .passengerId(saved.getPassengerId())
                    .cancelledBy(saved.getCancelledBy())
                    .build();

            eventPublisher.publishTripCancelled(event);

            return tripMapper.toResponse(saved);
        });
    }

    // ░░░ ACCEPT TRIP WITH LOCK (NEW - replaces event-based accept) ░░░
    /**
     * Accept a trip with distributed lock and early state validation.
     * 
     * Flow:
     * 1. Try to acquire SETNX lock with 5s TTL (atomic, prevents race)
     * 2. Fetch trip and check if status is SEARCHING
     * 3. Update DB to ASSIGNED status
     * 4. Publish trip.assigned event
     * 
     * IMPORTANT: Lock MUST be acquired BEFORE reading trip to prevent
     * stale read race conditions.
     * 
     * @param tripId   The trip to accept
     * @param driverId The driver accepting the trip
     * @return AcceptResult indicating success or failure reason
     */

    // @Transactional -- REMOVED to narrow scope
    public AcceptResult acceptTripWithLock(UUID tripId, UUID driverId) {
        // 1. Try to acquire lock FIRST (one-shot, no retry)
        // This is pure Redis I/O - executed OUTSIDE DB transaction.
        boolean acquired = lockService.tryAcquire(tripId, driverId, 5);
        if (!acquired) {
            return AcceptResult.ALREADY_ASSIGNED;
        }

        // 2. Initial Read & Status Check (Read-Replica friendly)
        // Uses tripRepository.findById() which is @Transactional(readOnly=true) by
        // default
        Optional<Trip> tripOpt = tripRepository.findById(tripId);
        if (tripOpt.isEmpty()) {
            return AcceptResult.TRIP_NOT_FOUND;
        }

        Trip trip = tripOpt.get();
        if (trip.getTripStatus() != TripStatus.SEARCHING) {
            return AcceptResult.ALREADY_ASSIGNED;
        }

        // 3. EXECUTE UPDATE IN SHORT TRANSACTION
        // Only open transaction for the critical compare-and-swap DB operation
        AcceptResult txResult = transactionTemplate.execute(status -> {
            // Direct Update Query - No Read required on Primary!
            // "UPDATE trip SET ... WHERE id = ? AND status = ?"
            int updatedRows = tripRepository.assignDriver(
                    tripId,
                    driverId,
                    TripStatus.ASSIGNED,
                    TripStatus.SEARCHING,
                    LocalDateTime.now(),
                    LocalDateTime.now());

            if (updatedRows == 0) {
                // If 0 rows updated, it means the trip was not found OR status was not
                // SEARCHING
                // We already checked uniqueness via Redis, so most likely another driver stole
                // it
                // just before we got here (race condition), or the status changed.
                log.warn("Atomic update failed for trip {}: status changed or not found", tripId);
                return AcceptResult.ALREADY_ASSIGNED;
            }

            return AcceptResult.SUCCESS;
        });

        // 4. Publish event (RabbitMQ I/O) - OUTSIDE transaction
        if (txResult == AcceptResult.SUCCESS) {
            TripAssignedEvent event = new TripAssignedEvent();
            event.setTripId(tripId);
            event.setDriverId(driverId);
            eventPublisher.publishTripAssigned(event);
        }

        return txResult;
    }

    // ░░░ START TRIP + publish trip.started ░░░
    public Optional<TripResponse> startTrip(UUID id) {
        return tripRepository.findById(id).map(trip -> {

            if (trip.getTripStatus() != TripStatus.ASSIGNED) {
                throw new IllegalStateException("Trip must be accepted before starting.");
            }

            trip.setTripStatus(TripStatus.IN_PROGRESS);
            trip.setUpdatedAt(LocalDateTime.now());

            Trip saved = tripRepository.save(trip);

            TripStartedEvent event = TripStartedEvent.builder()
                    .tripId(saved.getId())
                    .driverId(saved.getDriverId())
                    .passengerId(saved.getPassengerId())
                    .build();

            eventPublisher.publishTripStarted(event);

            return tripMapper.toResponse(saved);
        });
    }

    // ░░░ COMPLETE TRIP + publish trip.completed ░░░
    public Optional<TripResponse> completeTrip(UUID id) {
        return tripRepository.findById(id).map(trip -> {

            if (trip.getTripStatus() != TripStatus.IN_PROGRESS) {
                throw new IllegalStateException("Trip must be in-progress to complete.");
            }

            trip.setTripStatus(TripStatus.COMPLETED);
            trip.setCompletedAt(LocalDateTime.now());
            trip.setUpdatedAt(LocalDateTime.now());
            trip.setFinalPrice(trip.getEstimatedPrice());

            Trip saved = tripRepository.save(trip);

            TripCompletedEvent event = TripCompletedEvent.builder()
                    .tripId(saved.getId())
                    .driverId(saved.getDriverId())
                    .passengerId(saved.getPassengerId())
                    .finalPrice(saved.getFinalPrice())
                    .build();

            eventPublisher.publishTripCompleted(event);

            return tripMapper.toResponse(saved);
        });
    }

    // Utility update
    public Optional<TripResponse> updateStatus(UUID id, TripStatus status) {
        return tripRepository.findById(id).map(trip -> {
            trip.setTripStatus(status);
            trip.setUpdatedAt(LocalDateTime.now());
            Trip saved = tripRepository.save(trip);
            return tripMapper.toResponse(saved);
        });
    }

    public TripRatingResponse rateTrip(UUID tripId, RateTripRequest request) {

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));

        if (trip.getTripStatus() != TripStatus.COMPLETED) {
            throw new IllegalStateException("Trip must be completed before rating");
        }

        TripRating rating = TripRating.builder()
                .tripId(tripId)
                .passengerId(request.getPassengerId())
                .driverId(request.getDriverId())
                .rating(request.getRating())
                .feedback(request.getFeedback())
                .build();

        TripRating saved = tripRatingRepository.save(rating);

        return tripMapper.toRatingResponse(saved);
    }

    // FARE CALCULATION
    public BigDecimal calculateFare(BigDecimal distanceKm, VehicleType type, boolean isPeakHour) {
        BigDecimal baseFare;
        BigDecimal perKmRate;

        switch (type) {
            case BIKE -> {
                baseFare = BigDecimal.valueOf(5000);
                perKmRate = BigDecimal.valueOf(7000);
            }
            case BIKE_ECONOMY -> {
                baseFare = BigDecimal.valueOf(4000);
                perKmRate = BigDecimal.valueOf(6000);
            }
            case CAR_4_SEAT -> {
                baseFare = BigDecimal.valueOf(10000);
                perKmRate = BigDecimal.valueOf(11000);
            }
            case CAR_7_SEAT -> {
                baseFare = BigDecimal.valueOf(15000);
                perKmRate = BigDecimal.valueOf(13000);
            }
            case CAR_ECONOMY -> {
                baseFare = BigDecimal.valueOf(8000);
                perKmRate = BigDecimal.valueOf(9500);
            }
            case CAR_ELECTRIC -> {
                baseFare = BigDecimal.valueOf(9000);
                perKmRate = BigDecimal.valueOf(10500);
            }
            case CAR_PREMIUM -> {
                baseFare = BigDecimal.valueOf(20000);
                perKmRate = BigDecimal.valueOf(16000);
            }
            default -> {
                baseFare = BigDecimal.valueOf(8000);
                perKmRate = BigDecimal.valueOf(9000);
            }
        }

        BigDecimal fare = baseFare.add(perKmRate.multiply(distanceKm));

        if (isPeakHour) {
            fare = fare.multiply(BigDecimal.valueOf(1.25));
        }

        BigDecimal minimumFare = BigDecimal.valueOf(12000);
        if (fare.compareTo(minimumFare) < 0) {
            fare = minimumFare;
        }

        return fare.setScale(-2, RoundingMode.HALF_UP);
    }
}
