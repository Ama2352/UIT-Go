package se360.driver_service.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se360.driver_service.services.DriverService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/drivers")
public class DriverController {

    private final DriverService driverService;

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
        @RequestParam double lng
    ) {
        driverService.updateDriverLocation(driverId, lat, lng);
        return ResponseEntity.ok("Location updated for driver " + driverId);
    }

    @GetMapping("/search")
    public ResponseEntity<List<String>> searchNearbyDrivers(
        @RequestParam double lat,
        @RequestParam double lng,
        @RequestParam(defaultValue = "3") double radiusInKm
    ) {
        List<String> drivers = driverService.findNearbyDrivers(lat, lng, radiusInKm);
        return ResponseEntity.ok(drivers);
    }
}
