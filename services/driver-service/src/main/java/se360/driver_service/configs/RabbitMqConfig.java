package se360.driver_service.configs;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange driverLocationExchange(@Value("${messaging.location.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue driverLocationQueue(@Value("${messaging.location.queue}") String queueName) {
        // queue mainly for local testing; notification-service can declare its own
        // later
        return new Queue(queueName, true);
    }

    @Bean
    public Binding driverLocationBinding(
            Queue driverLocationQueue,
            TopicExchange driverLocationExchange,
            @Value("${messaging.location.routing-key}") String routingKey) {
        return BindingBuilder.bind(driverLocationQueue).to(driverLocationExchange).with(routingKey);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        return rabbitTemplate;
    }
}
