export class TripCancelledDto {
  tripId: string;
  passengerId: string;
  driverId?: string;
  cancelledBy: 'PASSENGER' | 'DRIVER';
  reason?: string;
  cancelledAt: Date;
}

