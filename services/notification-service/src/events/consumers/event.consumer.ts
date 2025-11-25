import { Injectable, OnModuleInit, Logger } from '@nestjs/common';
import { RabbitMQService } from '../../rabbitmq/rabbitmq.service';
import { NotificationService } from '../../notifications/notification.service';
import { TripStartedDto } from '../dto/trip-started.dto';
import { TripCompletedDto } from '../dto/trip-completed.dto';
import { TripCancelledDto } from '../dto/trip-cancelled.dto';
import { TripAssignedDto } from '../dto/trip-assigned.dto';
import { PaymentSuccessDto } from '../dto/payment-success.dto';
import { ConsumeMessage } from 'amqplib';

@Injectable()
export class EventConsumer implements OnModuleInit {
  private readonly logger = new Logger(EventConsumer.name);

  constructor(
    private readonly rabbitMQService: RabbitMQService,
    private readonly notificationService: NotificationService,
  ) {}

  async onModuleInit() {
    const channel = await this.rabbitMQService.getChannel();
    const queue = 'notification_queue';

    await channel.consume(queue, async (msg: ConsumeMessage | null) => {
      if (!msg) return;

      try {
        const content = JSON.parse(msg.content.toString());
        const routingKey = msg.fields.routingKey;

        this.logger.log(`Received message with routing key: ${routingKey}`);

        switch (routingKey) {
          case 'trip.started':
            await this.handleTripStarted(content as TripStartedDto);
            break;
          case 'trip.completed':
            await this.handleTripCompleted(content as TripCompletedDto);
            break;
          case 'trip.cancelled':
            await this.handleTripCancelled(content as TripCancelledDto);
            break;
          case 'trip.assigned':
            await this.handleTripAssigned(content as TripAssignedDto);
            break;
          case 'payment.success':
            await this.handlePaymentSuccess(content as PaymentSuccessDto);
            break;
          default:
            this.logger.warn(`Unknown routing key: ${routingKey}`);
        }

        channel.ack(msg);
      } catch (error) {
        this.logger.error(`Error processing message: ${error.message}`);
        // Reject and do not requeue (sends to DLQ if configured)
        channel.nack(msg, false, false);
      }
    });
  }

  private async handleTripStarted(payload: TripStartedDto) {
    this.logger.log(`Processing trip.started for trip ${payload.tripId}`);
    await this.notificationService.notifyTripStarted(payload);
  }

  private async handleTripCompleted(payload: TripCompletedDto) {
    this.logger.log(`Processing trip.completed for trip ${payload.tripId}`);
    await this.notificationService.notifyTripCompleted(payload);
  }

  private async handleTripCancelled(payload: TripCancelledDto) {
    this.logger.log(`Processing trip.cancelled for trip ${payload.tripId}`);
    await this.notificationService.notifyTripCancelled(payload);
  }

  private async handleTripAssigned(payload: TripAssignedDto) {
    this.logger.log(`Processing trip.assigned for trip ${payload.tripId}`);
    await this.notificationService.notifyTripAssigned(payload);
  }

  private async handlePaymentSuccess(payload: PaymentSuccessDto) {
    this.logger.log(`Processing payment.success for trip ${payload.tripId}`);
    await this.notificationService.notifyPaymentSuccess(payload);
  }
}
