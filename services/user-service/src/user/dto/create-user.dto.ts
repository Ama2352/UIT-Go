import { IsEmail, IsString, MinLength, IsOptional, IsEnum } from 'class-validator';

export enum UserType {
  PASSENGER = 'PASSENGER',
  DRIVER = 'DRIVER',
}

export class CreateUserDto {
  @IsEmail()
  email: string;

  @IsString()
  @MinLength(8, { message: 'Password must be at least 8 characters long' })
  password: string;

  @IsString()
  @MinLength(1, { message: 'Full name cannot be empty' })
  fullName: string;

  @IsOptional()
  @IsString()
  phoneNumber?: string;

  @IsOptional()
  @IsEnum(UserType)
  userType?: UserType;
}

