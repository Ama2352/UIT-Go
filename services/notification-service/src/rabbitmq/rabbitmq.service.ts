import { Injectable, OnModuleInit, OnModuleDestroy, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as amqp from 'amqplib';

@Injectable()
export class RabbitMQService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(RabbitMQService.name);
  private connection: amqp.Connection;
  private channel: amqp.Channel;

  constructor(private readonly configService: ConfigService) {}

  async onModuleInit() {
    await this.connect();
  }

  async onModuleDestroy() {
    await this.disconnect();
  }

  private async connect() {
    try {
      const url = this.configService.get<string>('RABBITMQ_URL');
      this.connection = await amqp.connect(url);
      this.channel = await this.connection.createChannel();
      
      // Setup exchanges and DLQ
      await this.setupExchangesAndQueues();
      
      this.logger.log('Successfully connected to RabbitMQ');
    } catch (error) {
      this.logger.error(`Failed to connect to RabbitMQ: ${error.message}`);
      // Retry logic
      setTimeout(() => this.connect(), 5000);
    }
  }

  private async disconnect() {
    try {
      if (this.channel) {
        await this.channel.close();
      }
      if (this.connection) {
        await this.connection.close();
      }
      this.logger.log('RabbitMQ connection closed');
    } catch (error) {
      this.logger.error(`Error closing RabbitMQ connection: ${error.message}`);
    }
  }

  private async setupExchangesAndQueues() {
    const exchange = 'trip_events';
    const dlxExchange = 'trip_events_dlx';
    const queue = 'notification_queue';
    const dlq = 'notification_queue_dlq';

    // Dead Letter Exchange
    await this.channel.assertExchange(dlxExchange, 'topic', { durable: true });
    await this.channel.assertQueue(dlq, { durable: true });
    await this.channel.bindQueue(dlq, dlxExchange, '#');

    // Main Exchange and Queue
    await this.channel.assertExchange(exchange, 'topic', { durable: true });
    await this.channel.assertQueue(queue, {
      durable: true,
      arguments: {
        'x-dead-letter-exchange': dlxExchange,
        'x-dead-letter-routing-key': 'notification_dead_letter',
      },
    });

    // Bindings
    const routingKeys = [
      'trip.started',
      'trip.completed',
      'trip.cancelled',
      'trip.assigned',
      'payment.success'
    ];

    for (const key of routingKeys) {
      await this.channel.bindQueue(queue, exchange, key);
    }
  }

  async getChannel(): Promise<amqp.Channel> {
    if (!this.channel) {
      await this.connect();
    }
    return this.channel;
  }
}
