import { Module } from '@nestjs/common';
import { NotificationService } from './notification.service';
import { WebsocketModule } from '../websocket/websocket.module';

@Module({
  imports: [WebsocketModule],
  providers: [NotificationService],
  exports: [NotificationService],
})
export class NotificationModule {}

