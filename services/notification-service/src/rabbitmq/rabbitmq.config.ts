import { ClientsModuleOptions, Transport } from '@nestjs/microservices';
import { ConfigService } from '@nestjs/config';

export const getRabbitMQConfig = (configService: ConfigService): ClientsModuleOptions => [
  {
    name: 'TRIP_EVENT',
    transport: Transport.RMQ,
    options: {
      urls: [configService.get<string>('RABBITMQ_URL') || 'amqp://localhost:5672'],
      queue: 'notification_queue',
      queueOptions: {
        durable: true,
      },
      prefetchCount: 100,
      noAck: false,
    },
  },
];

