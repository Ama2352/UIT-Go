import { Controller, Get } from '@nestjs/common';
import { NotificationGateway } from '../websocket/websocket.gateway';
import { RabbitMQService } from '../rabbitmq/rabbitmq.service';

@Controller('health')
export class HealthController {
  constructor(
    private readonly notificationGateway: NotificationGateway,
    private readonly rabbitMQService: RabbitMQService,
  ) {}

  @Get()
  async check() {
    const activeConnections = this.notificationGateway.getActiveConnectionsCount();
    // We could add more detailed checks here
    
    let rabbitStatus = 'unknown';
    try {
        const channel = await this.rabbitMQService.getChannel();
        // Simple check if channel is alive
        rabbitStatus = 'connected';
    } catch (e) {
        rabbitStatus = 'disconnected';
    }

    return {
      status: 'ok',
      timestamp: new Date(),
      activeConnections,
      rabbitMQ: rabbitStatus,
    };
  }
}

