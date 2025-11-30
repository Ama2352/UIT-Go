# ADR 003: Choosing RabbitMQ over Apache Kafka for Event-Driven Messaging

## Status

**Accepted** — November 2025

---

## Context

UIT-Go requires an asynchronous messaging system to decouple microservices and handle event-driven workflows such as:

- **Trip lifecycle events**: `trip.requested`, `trip.assigned`, `trip.started`, `trip.completed`, `trip.cancelled`, `trip.offered`
- **Driver location updates**: Real-time GPS streaming from drivers
- **Notification fan-out**: Pushing updates to passengers and drivers via WebSocket

Two leading solutions were evaluated:

| Criteria | RabbitMQ | Apache Kafka |
|----------|----------|--------------|
| **Message Pattern** | Traditional message broker (push-based) | Distributed log (pull-based) |
| **Throughput** | ~50,000 msg/s per node | 1M+ msg/s per cluster |
| **Complexity** | Simple setup, single node sufficient for MVP | Requires ZooKeeper/KRaft, multi-broker cluster |
| **Message Replay** | No (messages deleted after consumption) | Yes (configurable retention) |
| **Spring/NestJS Support** | Native, mature libraries | Available but more complex |
| **Operational Overhead** | Low (single binary) | High (cluster management) |

---

## Decision

We chose **RabbitMQ** as the messaging backbone for UIT-Go Phase 1.

---

## Rationale

### 1. Simplicity Over Scale

Our MVP targets hundreds of concurrent users, not millions. RabbitMQ's simpler operational model enables:
- Faster development velocity
- Easier debugging
- Lower infrastructure costs

### 2. Push-Based Delivery for Real-Time

RabbitMQ pushes messages to consumers immediately, reducing latency for time-sensitive notifications like:
- Trip assignment alerts
- Driver arrival notifications
- Payment confirmations

### 3. Flexible Routing with Topic Exchange

RabbitMQ's topic exchange pattern matches our event structure:

```
Exchange: trip.events (topic)
├── trip.requested      → Driver Service (driver matching)
├── trip.assigned       → Notification Service
├── trip.started        → Notification Service
├── trip.completed      → Notification Service
├── trip.cancelled      → Notification Service
├── trip.offered        → Notification Service
└── driver.location.*   → Location tracking
```

**Actual Exchange Configuration:**

```java
// Trip Service & Driver Service - RabbitMQConfiguration.java
public static final String EXCHANGE = "trip.events";

@Bean
public TopicExchange tripExchange() {
    return new TopicExchange(EXCHANGE, true, false);
}
```

**Service Roles:**
- **Trip Service**: Publishes `trip.requested`, `trip.started`, `trip.completed`, `trip.cancelled`
- **Driver Service**: Publishes `trip.assigned`, `trip.offered`, `driver.location.updated`
- **Notification Service**: Consumes all `trip.*` events for WebSocket notifications

### 4. Adequate Throughput for MVP

RabbitMQ handles ~50,000 messages/second on a single node, which exceeds our Phase 1 requirements:

| Flow | Expected Load | RabbitMQ Capacity |
|------|--------------|-------------------|
| Trip events | ~1,000/min peak | Sufficient |
| Location updates | ~10,000/min peak | Sufficient |
| Notifications | ~5,000/min peak | Sufficient |

### 5. Spring Boot & NestJS Native Support

Both frameworks have mature, well-documented RabbitMQ integrations:

**Spring Boot (Trip Service - Publisher):**
```java
@Component
@RequiredArgsConstructor
public class TripEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publishTripStarted(TripStartedEvent event) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfiguration.EXCHANGE,  // "trip.events"
            "trip.started",                   // routing key
            event
        );
    }
}
```

**NestJS (Notification Service - Consumer):**
```typescript
// rabbitmq.service.ts - Setup bindings
const exchange = 'trip.events';
const queue = 'notification_queue';

await channel.assertExchange(exchange, 'topic', { durable: true });
await channel.assertQueue(queue, { durable: true });

const routingKeys = [
    'trip.assigned', 'trip.started', 'trip.completed',
    'trip.cancelled', 'trip.offered'
];
for (const key of routingKeys) {
    await channel.bindQueue(queue, exchange, key);
}

// event.consumer.ts - Handle messages
await channel.consume(queue, async (msg) => {
    const routingKey = msg.fields.routingKey;
    switch (routingKey) {
        case 'trip.assigned':
            await this.notificationService.notifyTripAssigned(content);
            break;
        // ... other cases
    }
    channel.ack(msg);
});
```

---

## Trade-offs Accepted

| Trade-off | Impact | Mitigation |
|-----------|--------|------------|
| **Lower throughput than Kafka** | Limits to ~50k msg/s | Adequate for Phase 1; can migrate later |
| **No message replay** | Cannot reprocess historical events | Implement event sourcing if needed |
| **Less horizontal scaling** | Cluster setup more complex | Use federation for multi-region |
| **No built-in stream processing** | Need separate ETL pipeline | Use dedicated analytics service |

---

## Consequences

### Positive

- Faster time-to-market with simpler setup
- Lower operational overhead for small team
- Native Spring/NestJS integration
- Real-time push delivery for notifications
- Topic exchange matches our event routing model

### Negative

- May require migration to Kafka if throughput exceeds 50k msg/s
- No built-in event replay for debugging or recovery
- Limited stream processing capabilities

---

## Future Considerations

1. **Kafka Migration Path**: If scaling beyond 50k msg/s, evaluate Kafka or AWS MSK
2. **Event Sourcing**: Consider event store for audit trail and replay
3. **Dead Letter Queue (DLQ)**: Implement for failed message handling
4. **Monitoring**: Add Prometheus metrics for queue depth and consumer lag

---

## References

- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [Spring AMQP Reference](https://docs.spring.io/spring-amqp/reference/)
- [NestJS RabbitMQ](https://docs.nestjs.com/microservices/rabbitmq)
