# UIT-Go Backend - Postman Testing Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Environment Setup](#environment-setup)
4. [Service Endpoints Summary](#service-endpoints-summary)
5. [Complete Testing Flow](#complete-testing-flow)
6. [Detailed Test Cases](#detailed-test-cases)

---

## Overview

This guide provides a step-by-step approach to manually test the UIT-Go ride-hailing backend using Postman. The system consists of:

| Service | Port | Technology | Purpose |
|---------|------|------------|---------|
| **Kong API Gateway** | 8000 | Kong | Central entry point, routing |
| **User Service** | 3000 | NestJS + Prisma | User registration, authentication |
| **Trip Service** | 8081 | Spring Boot | Trip creation, lifecycle management |
| **Driver Service** | 8082 | Spring Boot + Redis | Driver location, availability |
| **Notification Service** | 3001 | NestJS + RabbitMQ | Real-time notifications via WebSocket |
| **RabbitMQ** | 15672 | RabbitMQ | Message broker (management UI) |

---

## Prerequisites

1. **Docker & Docker Compose** - All services running via `docker-compose up`
2. **Postman** - Latest version installed
3. **WebSocket client** (optional) - For testing real-time notifications (e.g., Postman WebSocket or browser console)

### Verify Services Are Running

```bash
# Check all containers are healthy
docker ps

# Expected running containers:
# - kong
# - user-service
# - trip-service
# - driver-service
# - notification-service
# - rabbitmq
# - user-postgres
# - trip-postgres
# - driver-redis
```

---

## Environment Setup

### Create Postman Environment Variables

Create a new environment in Postman with these variables:

| Variable | Initial Value | Description |
|----------|--------------|-------------|
| `base_url` | `http://localhost:8000` | Kong Gateway URL |
| `user_service_url` | `http://localhost:3000` | Direct User Service URL |
| `trip_service_url` | `http://localhost:8081` | Direct Trip Service URL |
| `driver_service_url` | `http://localhost:8082` | Direct Driver Service URL |
| `notification_service_url` | `http://localhost:3001` | Direct Notification Service URL |
| `passenger_token` | *(empty)* | JWT token for passenger |
| `driver_token` | *(empty)* | JWT token for driver |
| `passenger_id` | *(empty)* | Registered passenger UUID |
| `driver_id` | *(empty)* | Registered driver UUID |
| `trip_id` | *(empty)* | Created trip UUID |

---

## Service Endpoints Summary

### User Service Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/users/ping` | ❌ | Health check |
| `POST` | `/users` | ❌ | Register new user |
| `POST` | `/sessions` | ❌ | Login (get JWT) |
| `POST` | `/sessions/logout` | ✅ | Logout (revoke token) |
| `GET` | `/users/me` | ✅ | Get current user profile |
| `PUT` | `/users/me` | ✅ | Update current user profile |
| `GET` | `/users/:id` | ✅ | Get user by ID |
| `POST` | `/sessions/forgot-password` | ❌ | Request password reset |
| `POST` | `/sessions/reset-password` | ❌ | Reset password with token |

### Trip Service Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/trips/ping` | ❌ | Health check |
| `POST` | `/trips/estimate` | ❌ | Estimate fare before booking |
| `POST` | `/trips` | ✅ | Create new trip request |
| `GET` | `/trips` | ✅ | Get all trips |
| `GET` | `/trips/:id` | ✅ | Get trip by ID |
| `GET` | `/trips/fare` | ❌ | Calculate fare (utility) |
| `PUT` | `/trips/:id/accept` | ✅ | Driver accepts trip |
| `POST` | `/trips/:id/start` | ✅ | Start trip |
| `POST` | `/trips/:id/complete` | ✅ | Complete trip |
| `POST` | `/trips/:id/cancel` | ✅ | Cancel trip |
| `POST` | `/trips/:id/rating` | ✅ | Rate completed trip |

### Driver Service Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/drivers/ping` | ❌ | Health check |
| `PUT` | `/drivers/:driverId/online` | ✅ | Set driver status to ONLINE |
| `PUT` | `/drivers/:driverId/offline` | ✅ | Set driver status to OFFLINE |
| `PUT` | `/drivers/:driverId/location` | ✅ | Update driver GPS location |
| `GET` | `/drivers/search` | ✅ | Search nearby drivers |
| `PUT` | `/drivers/:driverId/trips/:tripId/accept` | ✅ | Driver accepts trip (publishes event) |

### Notification Service Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/health` | ❌ | Health check with RabbitMQ status |

---

## Complete Testing Flow

### 🎯 Business Flow Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        COMPLETE RIDE-HAILING FLOW                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Phase 1: User Setup                                                        │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ 1.1 Register Passenger  ──►  1.2 Login Passenger  ──►  Save Token    │  │
│  │ 1.3 Register Driver     ──►  1.4 Login Driver     ──►  Save Token    │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                     │                                       │
│                                     ▼                                       │
│  Phase 2: Driver Preparation                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ 2.1 Driver Goes Online  ──►  2.2 Driver Updates Location             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                     │                                       │
│                                     ▼                                       │
│  Phase 3: Trip Creation                                                     │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ 3.1 Estimate Fare  ──►  3.2 Create Trip (SEARCHING)                  │  │
│  │                                     │                                │  │
│  │                         [Event: trip.requested published]            │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                     │                                       │
│                                     ▼                                       │
│  Phase 4: Trip Assignment                                                   │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ 4.1 Search Nearby Drivers  ──►  4.2 Driver Accepts Trip (ASSIGNED)  │  │
│  │                                     │                                │  │
│  │                         [Event: trip.assigned published]             │  │
│  │                         [Notification sent to passenger]             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                     │                                       │
│                                     ▼                                       │
│  Phase 5: Trip Execution                                                    │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ 5.1 Start Trip (IN_PROGRESS)  ──►  5.2 Complete Trip (COMPLETED)    │  │
│  │          │                                  │                        │  │
│  │  [Event: trip.started]            [Event: trip.completed]            │  │
│  │  [Notification to passenger]      [Notification to both]             │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                     │                                       │
│                                     ▼                                       │
│  Phase 6: Post-Trip                                                         │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ 6.1 Passenger Rates Trip  ──►  6.2 Driver Goes Offline               │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Detailed Test Cases

### Phase 0: Health Checks

Verify all services are running before testing.

---

#### 0.1 Check User Service

**Request:**
```
GET {{user_service_url}}/users/ping
```

**Expected Response:**
```
200 OK
"Welcome to User Service!"
```

---

#### 0.2 Check Trip Service

**Request:**
```
GET {{trip_service_url}}/trips/ping
```

**Expected Response:**
```
200 OK
"Welcome to Trip Service!"
```

---

#### 0.3 Check Driver Service

**Request:**
```
GET {{driver_service_url}}/drivers/ping
```

**Expected Response:**
```
200 OK
"Welcome to Driver Service!"
```

---

#### 0.4 Check Notification Service

**Request:**
```
GET {{notification_service_url}}/health
```

**Expected Response:**
```json
{
  "status": "ok",
  "timestamp": "2025-11-28T...",
  "activeConnections": 0,
  "rabbitMQ": "connected"
}
```

---

### Phase 1: User Registration & Authentication

---

#### 1.1 Register Passenger

**Request:**
```
POST {{base_url}}/users
Content-Type: application/json

{
  "email": "passenger@test.com",
  "password": "password123",
  "fullName": "John Passenger",
  "phoneNumber": "+84901234567",
  "userType": "PASSENGER"
}
```

**Expected Response (201 Created):**
```json
{
  "id": "uuid-passenger-id",
  "email": "passenger@test.com",
  "fullName": "John Passenger",
  "phoneNumber": "+84901234567",
  "userType": "PASSENGER",
  "createdAt": "..."
}
```

**Post-request Script:**
```javascript
var jsonData = pm.response.json();
pm.environment.set("passenger_id", jsonData.id);
```

---

#### 1.2 Register Driver

**Request:**
```
POST {{base_url}}/users
Content-Type: application/json

{
  "email": "driver@test.com",
  "password": "password123",
  "fullName": "Mike Driver",
  "phoneNumber": "+84909876543",
  "userType": "DRIVER"
}
```

**Expected Response (201 Created):**
```json
{
  "id": "uuid-driver-id",
  "email": "driver@test.com",
  "fullName": "Mike Driver",
  "phoneNumber": "+84909876543",
  "userType": "DRIVER",
  "createdAt": "..."
}
```

**Post-request Script:**
```javascript
var jsonData = pm.response.json();
pm.environment.set("driver_id", jsonData.id);
```

---

#### 1.3 Login Passenger

**Request:**
```
POST {{base_url}}/sessions
Content-Type: application/json

{
  "email": "passenger@test.com",
  "password": "password123"
}
```

**Expected Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "uuid-passenger-id",
    "email": "passenger@test.com",
    "fullName": "John Passenger",
    "userType": "PASSENGER"
  }
}
```

**Post-request Script:**
```javascript
var jsonData = pm.response.json();
pm.environment.set("passenger_token", jsonData.accessToken);
```

---

#### 1.4 Login Driver

**Request:**
```
POST {{base_url}}/sessions
Content-Type: application/json

{
  "email": "driver@test.com",
  "password": "password123"
}
```

**Expected Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "uuid-driver-id",
    "email": "driver@test.com",
    "fullName": "Mike Driver",
    "userType": "DRIVER"
  }
}
```

**Post-request Script:**
```javascript
var jsonData = pm.response.json();
pm.environment.set("driver_token", jsonData.accessToken);
```

---

#### 1.5 Get Current User Profile (Optional Verification)

**Request:**
```
GET {{base_url}}/users/me
Authorization: Bearer {{passenger_token}}
```

**Expected Response (200 OK):**
```json
{
  "id": "uuid-passenger-id",
  "email": "passenger@test.com",
  "fullName": "John Passenger",
  "phoneNumber": "+84901234567",
  "userType": "PASSENGER"
}
```

---

### Phase 2: Driver Preparation

Before a trip can be assigned, drivers must be online and have their location updated.

---

#### 2.1 Driver Goes Online

**Request:**
```
PUT {{base_url}}/drivers/{{driver_id}}/online
Authorization: Bearer {{driver_token}}
```

**Expected Response (200 OK):**
```
"Driver {{driver_id}} is now ONLINE"
```

---

#### 2.2 Update Driver Location

This simulates the driver's GPS location update. The driver must be near the pickup point to be found.

**Request:**
```
PUT {{base_url}}/drivers/{{driver_id}}/location?lat=10.8231&lng=106.6297
Authorization: Bearer {{driver_token}}
```

**Location Reference:**
- `10.8231, 106.6297` = Ho Chi Minh City center (example)

**Expected Response (200 OK):**
```
"Location updated for driver {{driver_id}}"
```

---

#### 2.3 Verify Driver is Searchable

**Request:**
```
GET {{base_url}}/drivers/search?lat=10.8231&lng=106.6297&radiusInKm=5
Authorization: Bearer {{driver_token}}
```

**Expected Response (200 OK):**
```json
["uuid-driver-id"]
```

---

### Phase 3: Trip Creation

---

#### 3.1 Estimate Fare (Before Booking)

Passenger can estimate the fare before confirming the trip.

**Request:**
```
POST {{base_url}}/trips/estimate
Content-Type: application/json
Authorization: Bearer {{passenger_token}}

{
  "pickupAddress": "123 Nguyen Hue, District 1",
  "dropoffAddress": "456 Le Van Sy, District 3",
  "pickupLat": 10.7769,
  "pickupLng": 106.7009,
  "dropoffLat": 10.7915,
  "dropoffLng": 106.6786,
  "vehicleType": "CAR_4_SEAT"
}
```

**Expected Response (200 OK):**
```json
{
  "distanceKm": 3.52,
  "estimatedPrice": 48700
}
```

**Vehicle Types Available:**
- `BIKE`
- `BIKE_ECONOMY`
- `CAR_4_SEAT`
- `CAR_7_SEAT`
- `CAR_ECONOMY`
- `CAR_ELECTRIC`
- `CAR_PREMIUM`

---

#### 3.2 Create Trip

This creates a trip and publishes a `trip.requested` event to RabbitMQ.

**Request:**
```
POST {{base_url}}/trips
Content-Type: application/json
Authorization: Bearer {{passenger_token}}

{
  "passengerId": "{{passenger_id}}",
  "pickupAddress": "123 Nguyen Hue, District 1",
  "dropoffAddress": "456 Le Van Sy, District 3",
  "pickupLat": 10.7769,
  "pickupLng": 106.7009,
  "dropoffLat": 10.7915,
  "dropoffLng": 106.6786,
  "vehicleType": "CAR_4_SEAT"
}
```

**Expected Response (200 OK):**
```json
{
  "id": "uuid-trip-id",
  "passengerId": "uuid-passenger-id",
  "driverId": null,
  "pickupAddress": "123 Nguyen Hue, District 1",
  "dropoffAddress": "456 Le Van Sy, District 3",
  "pickupLat": 10.7769,
  "pickupLng": 106.7009,
  "dropoffLat": 10.7915,
  "dropoffLng": 106.6786,
  "vehicleType": "CAR_4_SEAT",
  "distanceKm": 3.52,
  "estimatedPrice": 48700,
  "tripStatus": "SEARCHING",
  "createdAt": "..."
}
```

**Post-request Script:**
```javascript
var jsonData = pm.response.json();
pm.environment.set("trip_id", jsonData.id);
```

**🔔 Event Published:** `trip.requested` → Driver Service receives this event

---

#### 3.3 Get Trip Details

**Request:**
```
GET {{base_url}}/trips/{{trip_id}}
Authorization: Bearer {{passenger_token}}
```

**Expected Response (200 OK):**
```json
{
  "id": "uuid-trip-id",
  "tripStatus": "SEARCHING",
  ...
}
```

---

### Phase 4: Trip Assignment

---

#### 4.1 Search Nearby Drivers (System/Admin Action)

In production, this would be done automatically by the system.

**Request:**
```
GET {{base_url}}/drivers/search?lat=10.7769&lng=106.7009&radiusInKm=5
Authorization: Bearer {{passenger_token}}
```

**Expected Response (200 OK):**
```json
["uuid-driver-id"]
```

---

#### 4.2 Driver Accepts Trip

Driver sees the trip request and accepts it. This publishes a `trip.assigned` event.

**Request:**
```
PUT {{base_url}}/drivers/{{driver_id}}/trips/{{trip_id}}/accept
Authorization: Bearer {{driver_token}}
```

**Expected Response (200 OK):**
```
"Driver accepted trip & event published!"
```

**🔔 Events:**
1. `trip.assigned` → Trip Service updates trip status to `ASSIGNED`
2. `trip.assigned` → Notification Service sends WebSocket notification to passenger

---

#### 4.3 Verify Trip Status Updated

**Request:**
```
GET {{base_url}}/trips/{{trip_id}}
Authorization: Bearer {{passenger_token}}
```

**Expected Response (200 OK):**
```json
{
  "id": "uuid-trip-id",
  "passengerId": "uuid-passenger-id",
  "driverId": "uuid-driver-id",
  "tripStatus": "ASSIGNED",
  "acceptedAt": "...",
  ...
}
```

---

### Phase 5: Trip Execution

---

#### 5.1 Start Trip (Driver Action)

Driver has arrived at pickup location and starts the trip.

**Request:**
```
POST {{base_url}}/trips/{{trip_id}}/start
Authorization: Bearer {{driver_token}}
```

**Expected Response (200 OK):**
```json
{
  "id": "uuid-trip-id",
  "tripStatus": "IN_PROGRESS",
  ...
}
```

**🔔 Event Published:** `trip.started` → Notification to passenger

---

#### 5.2 Complete Trip (Driver Action)

Driver completes the trip after reaching destination.

**Request:**
```
POST {{base_url}}/trips/{{trip_id}}/complete
Authorization: Bearer {{driver_token}}
```

**Expected Response (200 OK):**
```json
{
  "id": "uuid-trip-id",
  "tripStatus": "COMPLETED",
  "finalPrice": 48700,
  "completedAt": "...",
  ...
}
```

**🔔 Event Published:** `trip.completed` → Notifications to both passenger and driver

---

### Phase 6: Post-Trip Actions

---

#### 6.1 Rate the Trip (Passenger Action)

**Request:**
```
POST {{base_url}}/trips/{{trip_id}}/rating
Content-Type: application/json
Authorization: Bearer {{passenger_token}}

{
  "passengerId": "{{passenger_id}}",
  "driverId": "{{driver_id}}",
  "rating": 5,
  "feedback": "Great service! Very professional driver."
}
```

**Expected Response (200 OK):**
```json
{
  "id": "uuid-rating-id",
  "tripId": "uuid-trip-id",
  "passengerId": "uuid-passenger-id",
  "driverId": "uuid-driver-id",
  "rating": 5,
  "feedback": "Great service! Very professional driver.",
  "createdAt": "..."
}
```

---

#### 6.2 Driver Goes Offline

**Request:**
```
PUT {{base_url}}/drivers/{{driver_id}}/offline
Authorization: Bearer {{driver_token}}
```

**Expected Response (200 OK):**
```
"Driver {{driver_id}} is now OFFLINE"
```

---

#### 6.3 Logout (Optional)

**Request:**
```
POST {{base_url}}/sessions/logout
Authorization: Bearer {{passenger_token}}
```

**Expected Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

---

### Alternative Flow: Trip Cancellation

Test cancellation before trip starts.

---

#### A.1 Create a New Trip

Follow steps 3.2 to create a new trip.

---

#### A.2 Cancel Trip (Before Assignment)

**Request:**
```
POST {{base_url}}/trips/{{trip_id}}/cancel?cancelledBy=PASSENGER
Authorization: Bearer {{passenger_token}}
```

**Expected Response (200 OK):**
```json
{
  "id": "uuid-trip-id",
  "tripStatus": "CANCELLED",
  "cancelledBy": "PASSENGER",
  "cancelledAt": "...",
  ...
}
```

**🔔 Event Published:** `trip.cancelled` → Notification to relevant parties

---

## WebSocket Testing (Notifications)

### Connect to WebSocket

**Connection URL:**
```
ws://localhost:3001/notifications
```

**Headers:**
```
Authorization: Bearer {{passenger_token}}
```

**Or Query Parameter:**
```
ws://localhost:3001/notifications?token={{passenger_token}}
```

### Expected Events

| Event | Trigger | Payload |
|-------|---------|---------|
| `notification` (TRIP_ASSIGNED) | Driver accepts trip | `{ type: 'TRIP_ASSIGNED', title: 'Driver Assigned', ... }` |
| `notification` (TRIP_STARTED) | Trip starts | `{ type: 'TRIP_STARTED', title: 'Trip Started', ... }` |
| `notification` (TRIP_COMPLETED) | Trip completes | `{ type: 'TRIP_COMPLETED', title: 'Trip Completed', ... }` |
| `notification` (TRIP_CANCELLED) | Trip cancelled | `{ type: 'TRIP_CANCELLED', title: 'Trip Cancelled', ... }` |

---

## RabbitMQ Monitoring

### Access Management UI

**URL:** `http://localhost:15672`  
**Username:** `guest` (or from `.env`)  
**Password:** `guest` (or from `.env`)

### Queues to Monitor

| Queue | Publisher | Consumer |
|-------|-----------|----------|
| `trip.requested.queue` | Trip Service | Driver Service |
| `trip.assigned.queue` | Driver Service | Trip Service, Notification Service |
| `trip.started.queue` | Trip Service | Notification Service |
| `trip.completed.queue` | Trip Service | Notification Service |
| `trip.cancelled.queue` | Trip Service | Notification Service |
| `notification_queue` | Various | Notification Service |

---

## Error Scenarios to Test

### 1. Invalid Authentication

**Request:**
```
GET {{base_url}}/users/me
Authorization: Bearer invalid_token
```

**Expected:** `401 Unauthorized`

---

### 2. Trip Not Found

**Request:**
```
GET {{base_url}}/trips/00000000-0000-0000-0000-000000000000
Authorization: Bearer {{passenger_token}}
```

**Expected:** `404 Not Found`

---

### 3. Invalid Trip State Transition

Try to start a trip that is still in `SEARCHING` status (not yet assigned).

**Request:**
```
POST {{base_url}}/trips/{{trip_id}}/start
Authorization: Bearer {{driver_token}}
```

**Expected:** `400 Bad Request` or `500 Internal Server Error`
```json
{
  "message": "Trip must be accepted before starting."
}
```

---

### 4. Rate Incomplete Trip

**Request:**
```
POST {{base_url}}/trips/{{trip_id}}/rating
Content-Type: application/json
Authorization: Bearer {{passenger_token}}

{
  "passengerId": "{{passenger_id}}",
  "driverId": "{{driver_id}}",
  "rating": 5,
  "feedback": "Great!"
}
```

**Expected (if trip not completed):** `400 Bad Request`
```json
{
  "message": "Trip must be completed before rating"
}
```

---

### 5. Duplicate Registration

**Request:**
```
POST {{base_url}}/users
Content-Type: application/json

{
  "email": "passenger@test.com",
  "password": "password123",
  "fullName": "Another User"
}
```

**Expected:** `409 Conflict` or appropriate error
```json
{
  "message": "Email already exists"
}
```

---

## Test Execution Checklist

### ✅ Complete Happy Path

- [ ] 0.1 - User Service ping
- [ ] 0.2 - Trip Service ping
- [ ] 0.3 - Driver Service ping
- [ ] 0.4 - Notification Service health check
- [ ] 1.1 - Register passenger
- [ ] 1.2 - Register driver
- [ ] 1.3 - Login passenger
- [ ] 1.4 - Login driver
- [ ] 1.5 - Get profile (verification)
- [ ] 2.1 - Driver goes online
- [ ] 2.2 - Update driver location
- [ ] 2.3 - Verify driver is searchable
- [ ] 3.1 - Estimate fare
- [ ] 3.2 - Create trip
- [ ] 3.3 - Get trip details
- [ ] 4.1 - Search nearby drivers
- [ ] 4.2 - Driver accepts trip
- [ ] 4.3 - Verify trip status (ASSIGNED)
- [ ] 5.1 - Start trip
- [ ] 5.2 - Complete trip
- [ ] 6.1 - Rate trip
- [ ] 6.2 - Driver goes offline
- [ ] 6.3 - Logout

### ✅ Alternative Flows

- [ ] A.1 - Create trip for cancellation
- [ ] A.2 - Cancel trip

### ✅ Error Scenarios

- [ ] Invalid authentication
- [ ] Trip not found
- [ ] Invalid state transition
- [ ] Rate incomplete trip
- [ ] Duplicate registration

---

## Postman Collection Import

You can export this test plan as a Postman Collection. Create folders for each phase and add requests according to this guide.

### Suggested Collection Structure

```
UIT-Go API Tests/
├── 0. Health Checks/
│   ├── User Service Ping
│   ├── Trip Service Ping
│   ├── Driver Service Ping
│   └── Notification Service Health
├── 1. User Management/
│   ├── Register Passenger
│   ├── Register Driver
│   ├── Login Passenger
│   ├── Login Driver
│   ├── Get Profile
│   ├── Update Profile
│   └── Logout
├── 2. Driver Operations/
│   ├── Go Online
│   ├── Update Location
│   ├── Go Offline
│   └── Search Nearby Drivers
├── 3. Trip Lifecycle/
│   ├── Estimate Fare
│   ├── Create Trip
│   ├── Get Trip
│   ├── Get All Trips
│   ├── Accept Trip
│   ├── Start Trip
│   ├── Complete Trip
│   ├── Cancel Trip
│   └── Rate Trip
└── 4. Error Scenarios/
    ├── Invalid Token
    ├── Trip Not Found
    ├── Invalid State Transition
    └── Duplicate Registration
```

---

## Tips for Effective Testing

1. **Use Postman Variables** - Set `passenger_id`, `driver_id`, `trip_id` automatically from responses
2. **Run in Order** - Execute tests sequentially to maintain proper state
3. **Check RabbitMQ** - Monitor queues to verify events are published correctly
4. **WebSocket Testing** - Connect before creating trips to see real-time notifications
5. **Reset State** - For clean testing, restart Docker containers to reset databases

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `Connection refused` | Ensure Docker containers are running |
| `401 Unauthorized` | Token expired - login again |
| `Trip not updating` | Check RabbitMQ is connected |
| `Driver not found` | Ensure driver is ONLINE and location is updated |
| `Events not received` | Check notification-service logs |

---

*Last Updated: November 28, 2025*
