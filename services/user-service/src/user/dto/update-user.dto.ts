import { IsString, MinLength, IsOptional } from 'class-validator';

export class UpdateUserDto {
  @IsOptional()
  @IsString()
  @MinLength(1, { message: 'Full name cannot be empty' })
  fullName?: string;

  @IsOptional()
  @IsString()
  phoneNumber?: string;
}

