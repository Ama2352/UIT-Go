import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcryptjs';
import { PrismaService } from '../prisma/prisma.service';
import { User } from '@prisma/client';

export interface JwtPayload {
  sub: string;
  email: string;
  userType: string;
  iss: string;
}

@Injectable()
export class AuthService {
  constructor(
    private prisma: PrismaService,
    private jwtService: JwtService,
  ) {}

  async hashPassword(password: string): Promise<string> {
    const saltRounds = 10;
    return bcrypt.hash(password, saltRounds);
  }

  async comparePasswords(password: string, hashedPassword: string): Promise<boolean> {
    return bcrypt.compare(password, hashedPassword);
  }

  async generateAccessToken(user: User, deviceInfo?: string, ipAddress?: string): Promise<string> {
    const payload: JwtPayload = {
      sub: user.id,
      email: user.email,
      userType: user.userType,
      iss: 'uit-go',  // Issuer claim for Kong JWT validation
    };

    const token = this.jwtService.sign(payload);
    
    // Calculate expiration date (1 day from now)
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 1);

    // Store token in database
    await this.prisma.accessToken.create({
      data: {
        token,
        userId: user.id,
        expiresAt,
        deviceInfo,
        ipAddress,
      },
    });

    return token;
  }

  async validateToken(token: string): Promise<User | null> {
    try {
      // First, verify JWT signature and decode
      const payload = this.jwtService.verify(token);

      // Then check if token exists in database and is not expired
      const accessToken = await this.prisma.accessToken.findUnique({
        where: { token },
        include: { user: true },
      });

      if (!accessToken) {
        throw new UnauthorizedException('Token not found');
      }

      if (accessToken.expiresAt < new Date()) {
        // Token expired, delete it
        await this.prisma.accessToken.delete({ where: { id: accessToken.id } });
        throw new UnauthorizedException('Token expired');
      }

      return accessToken.user;
    } catch (error) {
      throw new UnauthorizedException('Invalid token');
    }
  }

  async revokeToken(token: string): Promise<void> {
    await this.prisma.accessToken.delete({
      where: { token },
    });
  }

  async revokeAllUserTokens(userId: string): Promise<void> {
    await this.prisma.accessToken.deleteMany({
      where: { userId },
    });
  }
}

