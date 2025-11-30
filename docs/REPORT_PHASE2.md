# UIT-Go Final Report

### Module A - Scalability & Performance

**Team Members:**
- Nguyen Thanh Kiet (22520720)
- Huynh Chi Hen (23520455)
- Ho Nguyen Minh Sang (23521338)

**Date:** November 2025  
**Repository:** [https://github.com/Ama2352/UIT-Go](https://github.com/Ama2352/UIT-Go)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [What We Built](#3-what-we-built)
4. [Key Design Decisions](#4-key-design-decisions)
5. [Module A: Scalability & Performance](#5-module-a-scalability--performance)
6. [Challenges & Solutions](#6-challenges--solutions)
7. [Results & Demo](#7-results--demo)
8. [Conclusion](#8-conclusion)

---

## 1. Project Overview

UIT-Go is a **ride-hailing backend system** built with microservices architecture. The project demonstrates how to build a scalable, real-time application using modern technologies.

### 1.1 Project Goals

| Goal | Description | Status |
|------|-------------|--------|
| Microservices | Build separate services that communicate via APIs and events | Complete |
| Real-Time | Provide live updates to passengers and drivers | Complete |
| Geospatial | Find nearby drivers using location queries | Complete |
| Event-Driven | Use message queues for async communication | Complete |
| Infrastructure | Deploy to cloud with Infrastructure as Code | Complete |

### 1.2 Technology Stack

| Layer | Technology |
|-------|------------|
| Backend | NestJS (TypeScript), Spring Boot (Java) |
| Database | PostgreSQL, Redis |
| Message Queue | RabbitMQ |
| API Gateway | Kong |
| Real-Time | Socket.IO, WebSocket |
| Infrastructure | Docker, Terraform, Azure |

---

## 2. System Architecture

> For detailed diagrams and component descriptions, see [ARCHITECTURE.MD](./ARCHITECTURE.MD)

### 2.1 High-Level Architecture

![UIT-Go Architecture](./assets/UIT-Go%20Architecture.png)

### 2.2 Services Overview

| Service | Framework | Database | Responsibility |
|---------|-----------|----------|----------------|
| User Service | NestJS | PostgreSQL | Authentication, user profiles |
| Trip Service | Spring Boot | PostgreSQL | Trip lifecycle, fare calculation |
| Driver Service | Spring Boot | Redis only | Location tracking, availability |
| Notification Service | NestJS | - | WebSocket gateway, event consumers |

### 2.3 Communication Patterns

| Pattern | Technology | Use Case |
|---------|------------|----------|
| REST/HTTP | Kong Gateway | Client-to-service requests |
| RabbitMQ Events | Topic Exchange | Service-to-service async communication |
| WebSocket | Socket.IO | Real-time notifications to clients |
| WebSocket | Native WS | GPS streaming from drivers |

### 2.4 Event Flow

```
Trip Lifecycle Events:
  trip.requested  → Driver Service (find nearby drivers)
  trip.offered    → Notification Service (notify drivers)
  trip.assigned   → Trip Service + Notification Service
  trip.started    → Notification Service
  trip.completed  → Notification Service
  trip.cancelled  → Notification Service
```

---

## 3. What We Built

### 3.1 Completed Features

| Feature | Description |
|---------|-------------|
| User Registration/Login | JWT-based authentication with RS256 signing |
| Trip Booking | Passengers can book trips with pickup/dropoff locations |
| Fare Estimation | Calculate fare based on distance and vehicle type |
| Driver Matching | Find nearby drivers using Redis GEO radius search |
| Trip Assignment | Drivers can accept trips (with race condition prevention) |
| Real-Time Notifications | Passengers receive updates via WebSocket |
| GPS Tracking | Drivers stream location via WebSocket |
| Trip Lifecycle | Start, complete, cancel trips with status updates |

### 3.2 Implementation Highlights

**Distributed Lock for Trip Assignment**

Prevents two drivers from accepting the same trip:

```java
// Driver Service - TripAssignmentLockService
public boolean acquireLock(UUID tripId, String driverId) {
    String key = "lock:trip:assign:" + tripId;
    return redisTemplate.opsForValue()
        .setIfAbsent(key, driverId, Duration.ofSeconds(30));
}
```

**Real-Time Notification Delivery**

```typescript
// Notification Service - WebSocket Gateway
@WebSocketGateway({ namespace: '/notifications' })
export class WebSocketGateway {
    private userConnections = new Map<string, Set<string>>();
    
    notifyUser(userId: string, event: string, data: any) {
        const socketIds = this.userConnections.get(userId);
        socketIds?.forEach(id => this.server.to(id).emit(event, data));
    }
}
```

**Geospatial Driver Search**

```java
// Driver Service - Find drivers within 5km
public List<String> findNearbyDrivers(double lat, double lng) {
    return geoOps.radius(
        "driver:locations",
        new Circle(new Point(lng, lat), new Distance(5, Metrics.KILOMETERS))
    );
}
```

### 3.3 GPS Demo UI

A Leaflet-based map demo is included for testing GPS WebSocket:

- Location: `driver-service/src/test/gps-streaming/map.html`
- Features: Drag marker to send location updates, JWT authentication

---

## 4. Key Design Decisions

We documented 8 Architectural Decision Records (ADRs) in [docs/ADR/](./docs/ADR/):

| # | Decision | Choice | Why |
|---|----------|--------|-----|
| 1 | Cache/GEO Store | Redis | Sub-10ms location queries, built-in GEO commands |
| 2 | API Style | RESTful | Simple, well-understood, good tooling |
| 3 | Message Broker | RabbitMQ | Easy to set up, topic routing, sufficient throughput |
| 4 | API Gateway | Kong | DB-less mode, declarative config, JWT plugin |
| 5 | Database | PostgreSQL | ACID compliance, mature ecosystem |
| 6 | Real-Time | WebSocket | Bidirectional, low latency, Socket.IO ecosystem |
| 7 | Infrastructure | Terraform | Multi-cloud support, version-controlled |
| 8 | Race Condition | Redis Lock | Simple SETNX, automatic TTL expiry |

### 4.1 Trade-offs We Accepted

> For complete trade-off analysis, see [docs/DESIGN_DECISIONS.md](./docs/DESIGN_DECISIONS.md)

| Trade-off | What We Chose | What We Sacrificed |
|-----------|---------------|-------------------|
| Simplicity over Scale | Docker Compose | Kubernetes auto-scaling |
| Speed over Features | Core trip flow | Payment, ratings frontend |
| Free over Managed | Self-hosted DBs | Azure managed services |

---

## 5. Module A: Scalability & Performance

> **Note**: Detailed performance analysis will be provided by the teammate responsible for Module A.

### 5.1 Scalability Approach

Our architecture supports scalability through:

1. **Stateless Services**: All services can be horizontally scaled
2. **Async Communication**: RabbitMQ decouples services
3. **In-Memory Caching**: Redis for fast reads
4. **Database per Service**: Independent scaling of data stores

### 5.2 Performance Characteristics

| Component | Expected Performance |
|-----------|---------------------|
| Redis GEO Query | < 10ms for radius search |
| API Response (p95) | < 200ms |
| WebSocket Latency | < 100ms |
| RabbitMQ Throughput | ~5,000 msg/s |

### 5.3 Module A Detailed Analysis

*[To be completed by Module A teammate - include load testing results, bottleneck analysis, and optimization recommendations]*

---

## 6. Challenges & Solutions

> For detailed technical solutions and code examples, see [docs/CHALLENGES.md](./docs/CHALLENGES.md)

### 6.1 Technical Challenges

| Challenge | Problem | Solution |
|-----------|---------|----------|
| Race Condition | Two drivers accepting same trip | Redis distributed lock with TTL |
| Service Discovery | Services finding each other | Docker Compose networking + Kong |
| Event Routing | Messages reaching wrong consumers | Topic exchange with routing keys |
| WebSocket Auth | Securing real-time connections | JWT validation in handshake |
| Data Consistency | Driver status across requests | Redis as single source of truth |

### 6.2 Integration Challenges

| Challenge | Problem | Solution |
|-----------|---------|----------|
| RabbitMQ Setup | Exchange/queue binding complexity | Standardized naming convention |
| Cross-Service Events | Schema consistency | Shared event documentation |
| JWT Validation | Different frameworks | Same RSA key pair across services |

---

## 7. Results & Demo

### 7.1 Working Demo Flow

```
1. Passenger registers/logs in
2. Passenger enters pickup & dropoff locations
3. System calculates fare estimate
4. Passenger confirms booking
5. System finds nearby drivers (Redis GEO)
6. Nearby drivers receive trip offer (WebSocket)
7. Driver accepts trip (with lock)
8. Passenger notified of assignment
9. Driver streams location (WebSocket GPS)
10. Driver starts/completes trip
```

### 7.2 What Works

- Complete user authentication flow
- Trip booking and fare calculation
- Real-time driver notifications
- GPS location streaming with demo UI
- Trip lifecycle (request → assign → start → complete)

### 7.3 Known Limitations

| Limitation | Reason | Production Solution |
|------------|--------|---------------------|
| Single VM | Demo scale sufficient | Kubernetes cluster |
| No payment | Requires provider account | Stripe/VNPay integration |
| Basic auth | Meets requirements | OAuth2, refresh tokens |
| Monitoring | Planned via Docker | Prometheus + Grafana in docker-compose |

---

## 8. Conclusion

### 8.1 What We Achieved

UIT-Go demonstrates a **working microservices-based ride-hailing backend** with:

- 4 independently deployable services
- Event-driven architecture with RabbitMQ
- Real-time capabilities via WebSocket
- Geospatial queries with Redis GEO
- Infrastructure as Code with Terraform
- Cloud deployment on Azure

### 8.2 Skills Learned

| Area | Skills |
|------|--------|
| Architecture | Microservices design, API design, event-driven patterns |
| Backend | NestJS, Spring Boot, REST APIs, WebSocket |
| Data | PostgreSQL, Redis, message queues |
| DevOps | Docker, Terraform, cloud deployment |
| Problem Solving | Distributed locks, async communication, real-time systems |

### 8.3 Future Improvements

> For detailed future directions, see [docs/FUTURE.md](./docs/FUTURE.md)

If we had more time:
- Add Swagger API documentation
- Implement trip rating frontend
- Add comprehensive unit tests
- Set up monitoring dashboard

---

**Repository:** [https://github.com/Ama2352/UIT-Go](https://github.com/Ama2352/UIT-Go)  
**Last Updated:** November 2025
