export class TripCompletedDto {
    tripId: string;
    passengerId: string;
    driverId: string;
    dropoffAddress?: string;
    fare: number;
    endTime: string;     // ISO
    distanceKm?: number;
}
