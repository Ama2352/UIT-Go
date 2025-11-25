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
    public Queue queue() {
       return new Queue(QUEUE, true);
    }
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue assignedQueue() {
        return new Queue(ASSIGNED_QUEUE, true);
    }

    @Bean
    public Binding assignedBinding(TopicExchange exchange) {
        return BindingBuilder.bind(assignedQueue())
                .to(exchange)
                .with(ASSIGNED_ROUTING);
    }

    @Bean
    public Queue tripStartedQueue() {
        return new Queue("trip.started.queue", true);
    }

    @Bean
    public Binding bindTripStartedQueue(Queue tripStartedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(tripStartedQueue)
                .to(exchange)
                .with("trip.started");
    }

    @Bean
    public Queue tripCompletedQueue() {
        return new Queue("trip.completed.queue", true);
    }

    @Bean
    public Binding bindTripCompletedQueue(Queue tripCompletedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(tripCompletedQueue)
                .to(exchange)
                .with("trip.completed");
    }

    @Bean
    public Queue tripCancelledQueue() {
        return new Queue("trip.cancelled.queue", true);
    }

    @Bean
    public Binding bindTripCancelledQueue(Queue tripCancelledQueue, TopicExchange exchange) {
        return BindingBuilder.bind(tripCancelledQueue)
                .to(exchange)
                .with("trip.cancelled");
    }

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
