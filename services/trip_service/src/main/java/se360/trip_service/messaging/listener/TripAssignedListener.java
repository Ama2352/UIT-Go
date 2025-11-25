package se360.trip_service.messaging.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import se360.trip_service.messaging.RabbitMQConfiguration;
import se360.trip_service.messaging.events.TripAssignedEvent;
import se360.trip_service.model.entities.Trip;
import se360.trip_service.model.enums.TripStatus;
import se360.trip_service.repository.TripRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TripAssignedListener {

    private final TripRepository tripRepository;

    @RabbitListener(queues = RabbitMQConfiguration.ASSIGNED_QUEUE)
    public void handleAssigned(TripAssignedEvent event) {

        System.out.println("Received trip assigned event: " + event);

        Optional<Trip> opt = tripRepository.findById(event.getTripId());
        if (opt.isEmpty()) {
            System.out.println("❌ Trip not found: " + event.getTripId());
            return;
        }

        Trip trip = opt.get();
        trip.setDriverId(event.getDriverId());
        trip.setTripStatus(TripStatus.ASSIGNED);
        trip.setAcceptedAt(LocalDateTime.now());
        trip.setUpdatedAt(LocalDateTime.now());

        tripRepository.save(trip);
        System.out.println("Trip updated");
    }
}
