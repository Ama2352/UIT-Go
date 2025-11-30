import { IsUUID, IsNumber, IsString } from 'class-validator';

export class TripOfferedDto {
    @IsUUID()
    tripId: string;

    @IsUUID()
    driverId: string;

    @IsUUID()
    passengerId: string;

    @IsNumber()
    pickupLat: number;

    @IsNumber()
    pickupLng: number;

    @IsNumber()
    dropoffLat: number;

    @IsNumber()
    dropoffLng: number;

    @IsString()
    vehicleType: string;
}
