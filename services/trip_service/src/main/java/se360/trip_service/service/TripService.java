package se360.trip_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
@RequiredArgsConstructor
public class TripService {

    private final TripEventPublisher eventPublisher;
    private final TripRepository tripRepository;
    private final TripMapper tripMapper;
    private final TripRatingRepository tripRatingRepository;

    // ░░░ ESTIMATE FARE ░░░
    public EstimateFareResponse estimateFare(EstimateFareRequest req) {
        BigDecimal distanceKm = DistanceUtil.calculateDistanceKm(
                req.getPickupLat(),
                req.getPickupLng(),
                req.getDropoffLat(),
                req.getDropoffLng()
        );

        BigDecimal estimatedPrice = calculateFare(distanceKm, req.getVehicleType(), false);

        return new EstimateFareResponse(
                distanceKm.setScale(2, RoundingMode.HALF_UP),
                estimatedPrice.setScale(0, RoundingMode.HALF_UP)
        );
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
                req.getDropoffLng()
        );

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

    // ░░░ ACCEPT TRIP ░░░
    public Optional<TripResponse> acceptTrip(UUID id, UUID driverId) {
        return tripRepository.findById(id).map(trip -> {
            trip.setDriverId(driverId);
            trip.setTripStatus(TripStatus.ACCEPTED);
            trip.setAcceptedAt(LocalDateTime.now());
            trip.setUpdatedAt(LocalDateTime.now());
            Trip saved = tripRepository.save(trip);
            return tripMapper.toResponse(saved);
        });
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
