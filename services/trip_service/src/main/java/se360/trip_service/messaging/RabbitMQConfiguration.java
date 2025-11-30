package se360.trip_service.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;

@Configuration
public class RabbitMQConfiguration {

    public static final String EXCHANGE = "trip.events";

    public static final String QUEUE = "trip.requested.queue";
    public static final String ROUTING_KEY = "trip.requested";

    public static final String ASSIGNED_QUEUE = "trip.assigned.queue";
    public static final String ASSIGNED_ROUTING = "trip.assigned";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue requestedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding bindingRequested(Queue requestedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(requestedQueue)
                .to(exchange)
                .with(ROUTING_KEY);
    }


    @Bean
    public Queue assignedQueue() {
        return new Queue(ASSIGNED_QUEUE, true);
    }

    @Bean
    public Binding bindingAssigned(TopicExchange exchange) {
        return BindingBuilder.bind(assignedQueue())
                .to(exchange)
                .with(ASSIGNED_ROUTING);
    }

    // ========== SHARED MESSAGE CONVERTER ==========
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
