# Notification Service

Just the notification stuff. Connects to RabbitMQ, pushes to WebSocket.

## techstack
- WebSocket (Socket.IO)
- RabbitMQ consumer
- JWT Auth (checks token on connect)
- Health check endpoint

## How to run
Set `.env`:
```
PORT=3000
RABBITMQ_URL=amqp://user:pass@localhost:5672
JWT_SECRET=secret
```

Run:
```bash
npm install
npm run start:dev
```

Or docker:
```bash
docker-compose up --build notification-service
```

## WebSocket
Endpoint: `/notifications`
Connect with `?token=YOUR_JWT`.

It sends events to `notification` channel.

### Example Events

**Trip Started** (`trip.started`) -> Sends to Passenger
```json
{
  "userId": "passenger-123",
  "type": "TRIP_STARTED",
  "title": "Trip Started",
  "message": "Your trip has started.",
  "payload": {
    "tripId": "trip-abc",
    "passengerId": "passenger-123",
    "driverId": "driver-456",
    "startTime": "2025-10-27T10:00:00Z"
  },
  "createdAt": "2025-10-27T10:00:00Z"
}
```

**Trip Completed** (`trip.completed`) -> Sends to Passenger & Driver
```json
{
  "userId": "passenger-123",
  "type": "TRIP_COMPLETED",
  "title": "Trip Completed",
  "message": "Your trip has been completed. Fare: 50000",
  "payload": {
    "tripId": "trip-abc",
    "fare": 50000,
    "distanceKm": 5.2
  },
  "createdAt": "2025-10-27T10:15:00Z"
}
```

**Trip Cancelled** (`trip.cancelled`) -> Sends to Passenger & Driver
```json
{
  "userId": "driver-456",
  "type": "TRIP_CANCELLED",
  "title": "Trip Cancelled",
  "message": "Trip was cancelled by PASSENGER. Reason: Changed mind",
  "payload": {
    "tripId": "trip-abc",
    "cancelledBy": "PASSENGER",
    "reason": "Changed mind"
  },
  "createdAt": "2025-10-27T10:05:00Z"
}
```

**Driver Assigned** (`trip.assigned`) -> Sends to Passenger
```json
{
  "userId": "passenger-123",
  "type": "TRIP_ASSIGNED",
  "title": "Driver Assigned",
  "message": "Driver John Doe (36-B1 04953) is on the way. ETA: 5 mins.",
  "payload": {
    "tripId": "trip-abc",
    "driverName": "John Doe",
    "vehiclePlate": "36-B1 04953",
    "etaMinutes": 5
  },
  "createdAt": "2025-10-27T09:55:00Z"
}
```

**Payment Success** (`payment.success`) -> Sends to Passenger
```json
{
  "userId": "passenger-123",
  "type": "PAYMENT_SUCCESS",
  "title": "Payment Successful",
  "message": "Payment of 50000 VND was successful.",
  "payload": {
    "paymentId": "pay-789",
    "amount": 50000,
    "currency": "VND"
  },
  "createdAt": "2025-10-27T10:16:00Z"
}
```

## Health
`GET /health`
```json
{"status":"ok","activeConnections":36,"rabbitMQ":"connected"}
```
