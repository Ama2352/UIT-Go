import { Injectable, OnModuleInit, Logger } from '@nestjs/common';
import { RabbitMQService } from '../../rabbitmq/rabbitmq.service';
import { NotificationService } from '../../notifications/notification.service';
import { ConsumeMessage } from 'amqplib';

@Injectable()
export class EventConsumer implements OnModuleInit {
    private readonly logger = new Logger(EventConsumer.name);

    constructor(
        private readonly rabbitMQ: RabbitMQService,
        private readonly notificationService: NotificationService,
    ) {}

    async onModuleInit() {
        const channel = await this.rabbitMQ.getChannel();
        const queue = 'notification_queue';

        this.logger.log(`Listening on queue "${queue}" for trip events...`);

        await channel.consume(queue, async (msg: ConsumeMessage | null) => {
            if (!msg) return;

            try {
                const content = JSON.parse(msg.content.toString());
                const routingKey = msg.fields.routingKey;

                this.logger.log(`Received event - routingKey=${routingKey}, content=${JSON.stringify(content)}`);

                switch (routingKey) {
                    case 'trip.assigned':
                        await this.notificationService.notifyTripAssigned(content);
                        break;

                    case 'trip.started':
                        await this.notificationService.notifyTripStarted(content);
                        break;

                    case 'trip.completed':
                        await this.notificationService.notifyTripCompleted(content);
                        break;

                    case 'trip.cancelled':
                        await this.notificationService.notifyTripCancelled(content);
                        break;

                    case 'trip.offered':
                        await this.notificationService.notifyTripOffered(content);
                        break;

                    default:
                        this.logger.warn(`Unknown routing key: ${routingKey}`);
                }

                channel.ack(msg);

            } catch (err) {
                this.logger.error(`Error handling message: ${err.message}`, err.stack);


                channel.nack(msg, false, false);
            }
        });
    }
}
