package se360.trip_service.mapper;

import org.springframework.stereotype.Component;
import se360.trip_service.model.dtos.CreateTripRequest;
import se360.trip_service.model.dtos.TripResponse;
import se360.trip_service.model.dtos.TripRatingResponse;
import se360.trip_service.model.entities.Trip;
import se360.trip_service.model.entities.TripRating;
@Component
public class TripMapper {


    public Trip toEntity(CreateTripRequest req) {
        Trip trip = new Trip();
        trip.setPassengerId(req.getPassengerId());
        trip.setPickupAddress(req.getPickupAddress());
        trip.setDropoffAddress(req.getDropoffAddress());
        trip.setPickupLat(req.getPickupLat());
        trip.setPickupLng(req.getPickupLng());
        trip.setDropoffLat(req.getDropoffLat());
        trip.setDropoffLng(req.getDropoffLng());
        trip.setVehicleType(req.getVehicleType());
        trip.setDistanceKm(req.getDistanceKm());
        return trip;
    }


    public TripResponse toResponse(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .passengerId(trip.getPassengerId())
                .driverId(trip.getDriverId())
                .pickupAddress(trip.getPickupAddress())
                .dropoffAddress(trip.getDropoffAddress())
                .vehicleType(trip.getVehicleType())
                .tripStatus(trip.getTripStatus())
                .distanceKm(trip.getDistanceKm())
                .estimatedPrice(trip.getEstimatedPrice())
                .finalPrice(trip.getFinalPrice())
                .cancelledBy(trip.getCancelledBy())
                .cancelledAt(trip.getCancelledAt())
                .acceptedAt(trip.getAcceptedAt())
                .completedAt(trip.getCompletedAt())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }

    public TripRatingResponse toRatingResponse(TripRating rating) {
        return new TripRatingResponse(
                rating.getRating(),
                rating.getFeedback(),
                rating.getCreatedAt()
        );
    }
}
