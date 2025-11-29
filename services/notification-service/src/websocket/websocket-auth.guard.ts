import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { readFileSync } from 'fs';
import { WebSocketClient } from './interfaces/websocket-client.interface';

@Injectable()
export class WebSocketAuthGuard implements CanActivate {
  constructor(
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const client = context.switchToWs().getClient<WebSocketClient>();
    const token = this.extractToken(client);

    if (!token) {
      throw new UnauthorizedException('No token provided');
    }

    try {
      const publicKeyPath = this.configService.get<string>('JWT_PUBLIC_KEY_PATH');
      if (!publicKeyPath) {
        throw new Error('JWT public key path must be configured');
      }
      const publicKey = readFileSync(publicKeyPath);
      const payload = await this.jwtService.verifyAsync(token, {
        publicKey,
        algorithms: ['RS256'],
      });

      client.user = {
        userId: payload.sub,
        email: payload.email,
        role: payload.role,
      };
      return true;
    } catch (error) {
      throw new UnauthorizedException('Invalid token');
    }
  }

  private extractToken(client: any): string | undefined {
    const authHeader = client.handshake?.headers?.authorization;
    if (authHeader && authHeader.split(' ')[0] === 'Bearer') {
      return authHeader.split(' ')[1];
    }
    // Fallback to query param if needed
    return client.handshake?.query?.token as string;
  }
}

