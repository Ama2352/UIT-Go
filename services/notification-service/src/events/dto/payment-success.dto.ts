export class PaymentSuccessDto {
  paymentId: string;
  tripId: string;
  passengerId: string;
  amount: number;
  currency: string;
  paymentMethod: string;
  timestamp: Date;
}

