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
    public void handleTripAssigned(TripAssignedEvent event) {
        Optional<Trip> optionalTrip = tripRepository.findById(event.getTripId());

        if (optionalTrip.isEmpty()) {

            return;
        }

        Trip trip = optionalTrip.get();


        if (trip.getTripStatus() == TripStatus.ASSIGNED) {
            return;
        }

        trip.setDriverId(event.getDriverId());
        trip.setTripStatus(TripStatus.ASSIGNED);
        trip.setAcceptedAt(LocalDateTime.now());
        trip.setUpdatedAt(LocalDateTime.now());

        tripRepository.save(trip);
    }
}
