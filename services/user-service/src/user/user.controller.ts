import { Controller, Post, Get, Put, Body, UseGuards, Req, Param, ValidationPipe } from '@nestjs/common';
import { UserService } from './user.service';
import { CreateUserDto } from './dto/create-user.dto';
import { LoginDto } from './dto/login.dto';
import { UpdateUserDto } from './dto/update-user.dto';
import { ForgotPasswordDto } from './dto/forgot-password.dto';
import { ResetPasswordDto } from './dto/reset-password.dto';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { CurrentUser, UserPayload } from '../common/decorators/current-user.decorator';
import { Request } from 'express';

@Controller()
export class UserController {
  constructor(private readonly userService: UserService) {}

  @Post('users')
  async register(@Body(ValidationPipe) createUserDto: CreateUserDto) {
    return this.userService.register(createUserDto);
  }

  @Post('sessions')
  async login(@Body(ValidationPipe) loginDto: LoginDto, @Req() req: Request) {
    const ipAddress = req.ip || req.socket.remoteAddress;
    return this.userService.login(loginDto, ipAddress);
  }

  @Post('sessions/logout')
  @UseGuards(JwtAuthGuard)
  async logout(@Req() req: Request) {
    const token = req.headers.authorization?.replace('Bearer ', '');
    if (!token) {
      throw new Error('No token provided');
    }
    return this.userService.logout(token);
  }

  @Get('users/me')
  @UseGuards(JwtAuthGuard)
  async getProfile(@CurrentUser() user: UserPayload) {
    return this.userService.getProfile(user.sub);
  }

  @Put('users/me')
  @UseGuards(JwtAuthGuard)
  async updateProfile(
    @CurrentUser() user: UserPayload,
    @Body(ValidationPipe) updateUserDto: UpdateUserDto,
  ) {
    return this.userService.updateProfile(user.sub, updateUserDto);
  }

  @Get('users/ping')
  ping() {
    return 'Welcome to User Service!';
  }

  @Get('users/:id')
  @UseGuards(JwtAuthGuard)
  async getUserById(@Param('id') id: string) {
    return this.userService.getUserById(id);
  }

  @Post('sessions/forgot-password')
  async forgotPassword(@Body(ValidationPipe) forgotPasswordDto: ForgotPasswordDto) {
    return this.userService.forgotPassword(forgotPasswordDto);
  }

  @Post('sessions/reset-password')
  async resetPassword(@Body(ValidationPipe) resetPasswordDto: ResetPasswordDto) {
    return this.userService.resetPassword(resetPasswordDto);
  }
}

