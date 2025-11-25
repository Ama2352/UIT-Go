import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../prisma/prisma.service';
import { JwtPayload } from './auth.service';
import { readFileSync } from 'fs';

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    private prisma: PrismaService,
    private configService: ConfigService,
  ) {
    const publicKeyPath = configService.get<string>('JWT_PUBLIC_KEY_PATH');
    if (!publicKeyPath) {
      throw new Error('JWT public key path must be configured');
    }
    const publicKey = readFileSync(publicKeyPath);
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: publicKey,
      algorithms: ['RS256'],
      passReqToCallback: true,
    });
  }

  async validate(req: any, payload: JwtPayload) {
    // Extract the raw token from the Authorization header
    const token = ExtractJwt.fromAuthHeaderAsBearerToken()(req);

    if (!token) {
      throw new UnauthorizedException('No token provided');
    }

    // Check if token exists in database and is not expired
    const accessToken = await this.prisma.accessToken.findUnique({
      where: { token },
      include: { user: true },
    });

    if (!accessToken) {
      throw new UnauthorizedException('Token not found in database');
    }

    if (accessToken.expiresAt < new Date()) {
      // Token expired, delete it
      await this.prisma.accessToken.delete({ where: { id: accessToken.id } });
      throw new UnauthorizedException('Token expired');
    }

    // Return user payload for @CurrentUser() decorator
    return {
      sub: payload.sub,
      email: payload.email,
      userType: payload.userType,
    };
  }
}

