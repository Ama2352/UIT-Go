import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as amqp from 'amqplib';

@Injectable()
export class RabbitMQService implements OnModuleInit {
    private readonly logger = new Logger(RabbitMQService.name);

    private connection: amqp.Connection;
    private channel: amqp.Channel;

    constructor(private readonly configService: ConfigService) {}

    async onModuleInit() {
        await this.connect();
    }

    async connect() {
        try {
            const url = this.configService.get<string>('RABBITMQ_URL');

            this.connection = await amqp.connect(url);
            this.channel = await this.connection.createChannel();

            const exchange = 'trip.events';
            const queue = 'notification_queue';

            // Create exchange
            await this.channel.assertExchange(exchange, 'topic', { durable: true });

            // Create queue
            await this.channel.assertQueue(queue, {
                durable: true,
            });

            // Bind only events necessary for notification
            const routingKeys = [
                'trip.assigned',
                'trip.started',
                'trip.completed',
                'trip.cancelled'
            ];

            for (const key of routingKeys) {
                await this.channel.bindQueue(queue, exchange, key);
            }

            this.logger.log(`Notification queue '${queue}' bound to trip.events`);

        } catch (e) {
            this.logger.error('RabbitMQ connection failed:', e);
            setTimeout(() => this.connect(), 5000);
        }
    }

    async getChannel(): Promise<amqp.Channel> {
        if (!this.channel) {
            await this.connect();
        }
        return this.channel;
    }
}
