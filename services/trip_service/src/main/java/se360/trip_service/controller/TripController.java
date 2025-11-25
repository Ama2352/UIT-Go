package se360.trip_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se360.trip_service.model.dtos.CreateTripRequest;
import se360.trip_service.model.dtos.TripResponse;
import se360.trip_service.model.dtos.EstimateFareResponse;
import se360.trip_service.model.dtos.EstimateFareRequest;
import se360.trip_service.model.dtos.RateTripRequest;
import se360.trip_service.model.dtos.TripRatingResponse;
import se360.trip_service.model.enums.VehicleType;
import se360.trip_service.service.TripService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping("/estimate")
    public ResponseEntity<EstimateFareResponse> estimateFare(@RequestBody EstimateFareRequest request) {
        EstimateFareResponse response = tripService.estimateFare(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(@RequestBody CreateTripRequest request) {
        TripResponse created = tripService.createTrip(request);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<TripResponse>> getAllTrips() {
        List<TripResponse> trips = tripService.getAllTrips();
        return ResponseEntity.ok(trips);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTripById(@PathVariable UUID id) {
        return tripService.getTripById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/fare")
    public ResponseEntity<BigDecimal> calculateFare(
            @RequestParam BigDecimal distanceKm,
            @RequestParam VehicleType vehicleType,
            @RequestParam(defaultValue = "false") boolean isPeakHour) {
        BigDecimal fare = tripService.calculateFare(distanceKm, vehicleType, isPeakHour);
        return ResponseEntity.ok(fare);
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<TripResponse> acceptTrip(
            @PathVariable UUID id,
            @RequestBody Map<String, UUID> body
    ) {
        UUID driverId = body.get("driverId");

        return tripService.acceptTrip(id, driverId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


    @PostMapping("/{id}/start")
    public ResponseEntity<TripResponse> startTrip(@PathVariable UUID id) {
        return tripService.startTrip(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TripResponse> completeTrip(@PathVariable UUID id) {
        return tripService.completeTrip(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TripResponse> cancelTrip(
            @PathVariable UUID id,
            @RequestParam String cancelledBy) {
        return tripService.cancelTrip(id, cancelledBy)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<TripRatingResponse> rateTrip(
            @PathVariable UUID id,
            @RequestBody RateTripRequest request) {
        TripRatingResponse response = tripService.rateTrip(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public String ping() {
        return "Welcome to Trip Service!";
    }
}
