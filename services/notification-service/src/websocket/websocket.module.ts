import { Module, Global } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { readFileSync } from 'fs';
import { NotificationGateway } from './websocket.gateway';
import { WebSocketAuthGuard } from './websocket-auth.guard';

@Global()
@Module({
  imports: [
    ConfigModule,
    JwtModule.registerAsync({
      imports: [ConfigModule],
      useFactory: async (configService: ConfigService) => {
        const publicKeyPath = configService.get<string>('JWT_PUBLIC_KEY_PATH');
        if (!publicKeyPath) {
          throw new Error('JWT public key path must be configured');
        }
        const publicKey = readFileSync(publicKeyPath);
        return {
          publicKey,
          verifyOptions: {
            algorithms: ['RS256'],
          },
        };
      },
      inject: [ConfigService],
    }),
  ],
  providers: [NotificationGateway, WebSocketAuthGuard],
  exports: [NotificationGateway],
})
export class WebsocketModule {}

