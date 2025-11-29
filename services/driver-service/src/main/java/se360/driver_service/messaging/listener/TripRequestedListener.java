package se360.driver_service.messaging.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import se360.driver_service.messaging.RabbitMQConfiguration;
import se360.driver_service.messaging.events.TripRequestedEvent;

@Component
@RequiredArgsConstructor
public class TripRequestedListener {

    @RabbitListener(queues = RabbitMQConfiguration.REQUESTED_QUEUE)
    public void handleTripRequested(TripRequestedEvent event) {

        System.out.println("\n=== DRIVER SERVICE RECEIVED TRIP REQUESTED ===");
        System.out.println("TripId      : " + event.getTripId());
        System.out.println("PassengerId : " + event.getPassengerId());
        System.out.println("Pickup      : " + event.getPickupLat() + "," + event.getPickupLng());
        System.out.println("Dropoff     : " + event.getDropoffLat() + "," + event.getDropoffLng());
        System.out.println("VehicleType : " + event.getVehicleType());
        System.out.println("==============================================\n");

        // TODO: ở Step 3 → tìm tài xế phù hợp
        // TODO: Step 4 → publish trip.assigned
    }
}
