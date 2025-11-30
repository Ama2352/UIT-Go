package se360.driver_service.messaging.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import se360.driver_service.messaging.RabbitMQConfiguration;
import se360.driver_service.messaging.events.TripOfferedEvent;
import se360.driver_service.messaging.events.TripRequestedEvent;

import se360.driver_service.messaging.publisher.TripEventPublisher;
import se360.driver_service.services.DriverService;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TripRequestedListener {

    private final DriverService driverService;
    private final TripEventPublisher eventPublisher;

    @RabbitListener(queues = RabbitMQConfiguration.REQUESTED_QUEUE)
    public void handleTripRequested(TripRequestedEvent event) {

        System.out.println("\n=== DRIVER SERVICE RECEIVED TRIP REQUESTED ===");
        System.out.println("TripId      : " + event.getTripId());
        System.out.println("PassengerId : " + event.getPassengerId());
        System.out.println("Pickup      : " + event.getPickupLat() + "," + event.getPickupLng());
        System.out.println("Dropoff     : " + event.getDropoffLat() + "," + event.getDropoffLng());
        System.out.println("VehicleType : " + event.getVehicleType());
        System.out.println("==============================================");

        driverService.cacheTripPassenger(event.getTripId(), event.getPassengerId());

        List<String> drivers = driverService.findNearbyDrivers(
                event.getPickupLat(),
                event.getPickupLng(),
                3.0
        );

        if (drivers.isEmpty()) {
            System.out.println("❌ No available drivers within 3km");
            return;
        }

        System.out.println("✅ Candidate drivers: " + drivers);

        for (String driverId: drivers) {
            TripOfferedEvent offer = TripOfferedEvent.builder()
                    .tripId(event.getTripId())
                    .driverId(UUID.fromString(driverId))
                    .pickupLat(event.getPickupLat())
                    .pickupLng(event.getPickupLng())
                    .build();

            eventPublisher.publishTripOffered(offer);
        }
    }
}
