export class TripCompletedDto {
  tripId: string;
  passengerId: string;
  driverId: string;
  dropoffAddress: string;
  fare: number;
  endTime: Date;
  distanceKm: number;
}

