import { Module } from '@nestjs/common';
import { EventConsumer } from './consumers/event.consumer';
import { RabbitMQModule } from '../rabbitmq/rabbitmq.module';
import { NotificationModule } from '../notifications/notification.module';

@Module({
    imports: [RabbitMQModule, NotificationModule],
    providers: [EventConsumer],
})
export class EventsModule {}
