package se360.driver_service.controllers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import se360.driver_service.models.AcceptTripRequest;
import se360.driver_service.services.DriverService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/drivers")
public class DriverController {

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);

    private final DriverService driverService;
    private final RestTemplate restTemplate;

    @Value("${trip.service.url}")
    private String tripServiceUrl;

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

    /**
     * Forward accept trip request to TripService.
     * 
     * TripService now owns: early state check + SETNX lock + DB update + event
     * publishing.
     * DriverService is just a simple forwarder for the accept action.
     */
    @PutMapping("/{driverId}/trips/{tripId}/accept")
    public ResponseEntity<String> acceptTrip(
            @PathVariable UUID driverId,
            @PathVariable UUID tripId) {
        String url = tripServiceUrl + "/trips/" + tripId + "/accept";
        AcceptTripRequest request = new AcceptTripRequest(driverId);

        try {
            // Forward request to TripService - it handles all business logic and error
            // cases
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    new HttpEntity<>(request),
                    String.class);

            return response; // Forward response as-is (200 OK or 409 Conflict)
        } catch (Exception e) {
            // Only catch network/communication errors
            log.error("Error forwarding accept request to TripService", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to communicate with TripService: " + e.getMessage());
        }
    }

    @GetMapping("/ping")
    public String ping() {
        return "Welcome to Driver Service!";
    }
}
