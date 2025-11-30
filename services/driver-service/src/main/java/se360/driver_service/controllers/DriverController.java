package se360.driver_service.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import se360.driver_service.messaging.publisher.TripEventPublisher;
import se360.driver_service.messaging.events.TripAssignedEvent;
import se360.driver_service.services.DriverService;
import se360.driver_service.services.TripAssignmentLockService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/drivers")
public class DriverController {

    private final DriverService driverService;
    private final TripEventPublisher eventPublisher;
    private final TripAssignmentLockService tripAssignmentLockService;

    @PutMapping("/{driverId}/online")
    public ResponseEntity<String> goOnline(@PathVariable String driverId) {
        driverService.setDriverOnline(driverId);
        return ResponseEntity.ok("Driver " + driverId + " is now ONLINE");
    }

    @PutMapping("/{driverId}/offline")
    public ResponseEntity<String> goOffline(@PathVariable String driverId) {
        driverService.setDriverOffline(driverId);
        return ResponseEntity.ok("Driver " + driverId + " is now OFFLINE");
    }

    @PutMapping("/{driverId}/location")
    public ResponseEntity<String> updateLocation(
            @PathVariable String driverId,
            @RequestParam double lat,
            @RequestParam double lng) {
        driverService.updateDriverLocation(driverId, lat, lng);
        return ResponseEntity.ok("Location updated for driver " + driverId);
    }

    @GetMapping("/search")
    public ResponseEntity<List<String>> searchNearbyDrivers(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "3") double radiusInKm) {
        List<String> drivers = driverService.findNearbyDrivers(lat, lng, radiusInKm);
        return ResponseEntity.ok(drivers);
    }

    @PutMapping("/{driverId}/trips/{tripId}/accept")
    public ResponseEntity<String> acceptTrip(
            @PathVariable UUID driverId,
            @PathVariable UUID tripId
    ) {
        boolean acquired = tripAssignmentLockService.tryAcquire(tripId, driverId, 30);
        if (!acquired) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Trip already assigned to another driver.");
        }

        UUID passengerId = driverService.getPassengerIdForTrip(tripId);
        if (passengerId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Trip passengerId not found in Redis cache.");
        }

        TripAssignedEvent event = TripAssignedEvent.builder()
                .tripId(tripId)
                .driverId(driverId)
                .passengerId(passengerId)
                .build();

        eventPublisher.publishTripAssigned(event);

        return ResponseEntity.ok("Driver accepted trip & trip.assigned published!");
    }



    @GetMapping("/ping")
    public String ping() {
        return "Welcome to Driver Service!";
    }
}
