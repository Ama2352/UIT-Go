import { Injectable, Logger } from '@nestjs/common';
import { NotificationGateway } from '../websocket/websocket.gateway';
import { TripStartedDto } from '../events/dto/trip-started.dto';
import { TripCompletedDto } from '../events/dto/trip-completed.dto';
import { TripCancelledDto } from '../events/dto/trip-cancelled.dto';
import { TripAssignedDto } from '../events/dto/trip-assigned.dto';
import { PaymentSuccessDto } from '../events/dto/payment-success.dto';
import { Notification } from './interfaces/notification.interface';

@Injectable()
export class NotificationService {
  private readonly logger = new Logger(NotificationService.name);

  constructor(private readonly notificationGateway: NotificationGateway) {}

  async notifyTripStarted(payload: TripStartedDto) {
    const message = `Your trip has started.`;
    
    // Notify passenger
    await this.sendNotification(payload.passengerId, {
      userId: payload.passengerId,
      type: 'TRIP_STARTED',
      title: 'Trip Started',
      message,
      payload,
      createdAt: new Date(),
    });
  }

  async notifyTripCompleted(payload: TripCompletedDto) {
    const message = `Your trip has been completed. Fare: ${payload.fare}`;
    
    // Notify passenger
    await this.sendNotification(payload.passengerId, {
      userId: payload.passengerId,
      type: 'TRIP_COMPLETED',
      title: 'Trip Completed',
      message,
      payload,
      createdAt: new Date(),
    });

    // Notify driver
    await this.sendNotification(payload.driverId, {
      userId: payload.driverId,
      type: 'TRIP_COMPLETED',
      title: 'Trip Completed',
      message: `Trip completed. Earned: ${payload.fare}`,
      payload,
      createdAt: new Date(),
    });
  }

  async notifyTripCancelled(payload: TripCancelledDto) {
    const message = `Trip was cancelled by ${payload.cancelledBy}. Reason: ${payload.reason || 'N/A'}`;
    
    // Notify passenger
    await this.sendNotification(payload.passengerId, {
      userId: payload.passengerId,
      type: 'TRIP_CANCELLED',
      title: 'Trip Cancelled',
      message,
      payload,
      createdAt: new Date(),
    });

    // Notify driver if assigned
    if (payload.driverId) {
      await this.sendNotification(payload.driverId, {
        userId: payload.driverId,
        type: 'TRIP_CANCELLED',
        title: 'Trip Cancelled',
        message,
        payload,
        createdAt: new Date(),
      });
    }
  }

  async notifyTripAssigned(payload: TripAssignedDto) {
    const message = `Driver ${payload.driverName} (${payload.vehiclePlate}) is on the way. ETA: ${payload.etaMinutes} mins.`;
    
    // Notify passenger
    await this.sendNotification(payload.passengerId, {
      userId: payload.passengerId,
      type: 'TRIP_ASSIGNED',
      title: 'Driver Assigned',
      message,
      payload,
      createdAt: new Date(),
    });
  }

  async notifyPaymentSuccess(payload: PaymentSuccessDto) {
    const message = `Payment of ${payload.amount} ${payload.currency} was successful.`;
    
    // Notify passenger
    await this.sendNotification(payload.passengerId, {
      userId: payload.passengerId,
      type: 'PAYMENT_SUCCESS',
      title: 'Payment Successful',
      message,
      payload,
      createdAt: new Date(),
    });
  }

  private async sendNotification(userId: string, notification: Notification) {
    try {
      const sent = this.notificationGateway.sendToUser(userId, 'notification', notification);
      if (sent) {
        this.logger.log(`Notification sent to user ${userId}: ${notification.type}`);
      } else {
        this.logger.log(`User ${userId} offline, notification dropped (or queued if persistence enabled): ${notification.type}`);
        // Here we could implement offline storage/queueing if needed
      }
    } catch (error) {
      this.logger.error(`Failed to send notification to user ${userId}: ${error.message}`);
    }
  }
}
