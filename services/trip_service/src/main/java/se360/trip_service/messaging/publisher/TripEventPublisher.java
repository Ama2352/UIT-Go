package se360.trip_service.messaging.publisher;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import se360.trip_service.messaging.RabbitMQConfiguration;
import se360.trip_service.messaging.events.TripCancelledEvent;
import se360.trip_service.messaging.events.TripCompletedEvent;
import se360.trip_service.messaging.events.TripRequestedEvent;
import se360.trip_service.messaging.events.TripStartedEvent;

@Component
@RequiredArgsConstructor
public class TripEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishTripRequested(TripRequestedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                RabbitMQConfiguration.ROUTING_KEY,
                event
        );
    }
    public void publishTripStarted(TripStartedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                "trip.started",   // routing key — tạm hardcode
                event
        );
    }

    public void publishTripCompleted(TripCompletedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                "trip.completed",
                event
        );
    }

    public void publishTripCancelled(TripCancelledEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                "trip.cancelled",
                event
        );
    }

}

