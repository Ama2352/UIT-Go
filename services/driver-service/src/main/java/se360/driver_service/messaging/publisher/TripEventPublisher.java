package se360.driver_service.messaging.publisher;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import se360.driver_service.messaging.RabbitMQConfiguration;
import se360.driver_service.messaging.events.TripAssignedEvent;

@Component
@RequiredArgsConstructor
public class TripEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishTripAssigned(TripAssignedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                RabbitMQConfiguration.ASSIGNED_ROUTING_KEY,
                event
        );
    }
}
