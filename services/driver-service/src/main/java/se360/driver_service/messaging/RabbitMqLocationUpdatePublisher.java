package se360.driver_service.messaging;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import se360.driver_service.models.DriverLocationMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqLocationUpdatePublisher implements LocationUpdatePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${messaging.location.exchange}")
    private String exchange;

    @Value("${messaging.location.routing-key}")
    private String routingKey;

    @Override
    public void publishLocationUpdate(DriverLocationMessage message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.debug("Published driver.location.updated for {}", message.driverId());
        } catch (AmqpException ex) {
            log.error("Failed to publish location update for {}", message.driverId(), ex);
        }
    }

}
