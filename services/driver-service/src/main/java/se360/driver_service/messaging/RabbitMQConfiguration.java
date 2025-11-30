package se360.driver_service.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfiguration {

    public static final String EXCHANGE = "trip.events";

    public static final String REQUESTED_QUEUE = "trip.requested.queue";
    public static final String REQUESTED_ROUTING_KEY = "trip.requested";

    public static final String ASSIGNED_QUEUE = "trip.assigned.queue";
    public static final String ASSIGNED_ROUTING_KEY = "trip.assigned";

    public static final String OFFERED_QUEUE = "trip.offered.queue";
    public static final String OFFERED_ROUTING = "trip.offered";

    @Bean
    public TopicExchange tripExchange() {

        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue tripRequestedQueue() {
        return QueueBuilder.durable(REQUESTED_QUEUE).build();
    }

    @Bean
    public Queue tripAssignedQueue() {
        return QueueBuilder.durable(ASSIGNED_QUEUE).build();
    }

    @Bean
    public Binding bindingTripRequested(Queue tripRequestedQueue, TopicExchange tripExchange) {
        return BindingBuilder
                .bind(tripRequestedQueue)
                .to(tripExchange)
                .with(REQUESTED_ROUTING_KEY);
    }

    @Bean
    public Binding bindingTripAssigned(Queue tripAssignedQueue, TopicExchange tripExchange) {
        return BindingBuilder
                .bind(tripAssignedQueue)
                .to(tripExchange)
                .with(ASSIGNED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }


    @Bean
    public Queue tripOfferedQueue() {
        return QueueBuilder.durable(OFFERED_QUEUE).build();
    }

    @Bean
    public Binding bindOffered() {
        return BindingBuilder.bind(tripOfferedQueue())
                .to(tripExchange())
                .with(OFFERED_ROUTING);
    }

}
