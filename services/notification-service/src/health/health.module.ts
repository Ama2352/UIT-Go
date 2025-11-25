import { Module } from '@nestjs/common';
import { HealthController } from './health.controller';
import { WebsocketModule } from '../websocket/websocket.module';
import { RabbitMQModule } from '../rabbitmq/rabbitmq.module';

@Module({
  imports: [WebsocketModule, RabbitMQModule],
  controllers: [HealthController],
})
export class HealthModule {}

