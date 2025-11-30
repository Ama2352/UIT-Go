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


Module A focuses on designing, validating, and implementing a highly scalable, event-driven backend architecture for the UIT-Go mobility platform. This chapter presents the rationale behind architectural choices, the structure of each subsystem, and the implementation required to ensure low latency, high availability, and consistency under concurrent real-world workloads.

The design emphasizes loose coupling through messaging, stateless services for horizontal scaling, caching and geospatial indexing for driver matching, and strong consistency guarantees via idempotent logic and distributed locking.

# 5.1 High-Level Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT & ACCESS LAYER                              │
└──────────────────────────────────────────────────────────────────────────────┘
        └─ API Gateway → Authentication, rate limiting, routing

┌──────────────────────────────────────────────────────────────────────────────┐
│                         APPLICATION SERVICE LAYER                             │
└──────────────────────────────────────────────────────────────────────────────┘
        └─ TripService, DriverService, UserService (stateless microservices)

┌──────────────────────────────────────────────────────────────────────────────┐
│                          MESSAGING & ASYNC LAYER                              │
└──────────────────────────────────────────────────────────────────────────────┘
        └─ RabbitMQ events: trip.requested → trip.assigned

┌──────────────────────────────────────────────────────────────────────────────┐
│                         CACHING & GEOLOCATION LAYER                           │
└──────────────────────────────────────────────────────────────────────────────┘
        └─ Redis GEO, SETNX distributed locks

┌──────────────────────────────────────────────────────────────────────────────┐
│                           DATA STORAGE LAYER                                  │
└──────────────────────────────────────────────────────────────────────────────┘
        └─ PostgreSQL (database-per-service schema separation)

┌──────────────────────────────────────────────────────────────────────────────┐
│                     INFRASTRUCTURE & SCALING LAYER                            │
└──────────────────────────────────────────────────────────────────────────────┘
        └─ Docker Compose, containerized services, horizontal-scaling ready

┌──────────────────────────────────────────────────────────────────────────────┐
│                    OBSERVABILITY & CONTROL LAYER                              │
└──────────────────────────────────────────────────────────────────────────────┘
        └─ Logs, RabbitMQ UI, RedisInsight, Prometheus, Grafana, K6 integration
```

# 5.2 Detailed Architecture Design

## 5.2.1 Client & Access Layer

The Client & Access Layer provides the unified entry point for all external traffic.

### Responsibilities  
- Routing of incoming API requests  
- JWT authentication  
- Rate-limiting  
- Facilitates horizontal scaling  

### Implementation  
Kong API Gateway routes traffic to TripService, DriverService, and UserService.

## 5.2.2 Stateless Services & Idempotency

All microservices are stateless.

### Benefits  
- Safe horizontal scaling  
- No in-memory state  
- Fault tolerant  
- Clean separation of compute & state

### State Storage  
| State | Layer |
|-------|--------|
| Driver locations | Redis GEO |
| Trip lock | Redis SETNX |
| Trip persistence | PostgreSQL |
| Event flow | RabbitMQ |

### Idempotent Consumer  
Ensures messages are processed once even if RabbitMQ retries.

## 5.2.3 Messaging & Async Layer

RabbitMQ powers the event-driven communication pipeline.

### Workflow  
1. Passenger creates trip  
2. TripService persists the trip  
3. TripService publishes `trip.requested`  
4. DriverService consumes the event  
5. DriverService performs GEO search  
6. SETNX lock is acquired for the selected trip  
7. DriverService publishes `trip.assigned`  
8. TripService updates trip state in PostgreSQL  

## 5.2.4 Caching & Geolocation Layer

### Redis GEO  
Used for real-time driver lookup around a pickup location.

### SETNX Lock  
Guarantees exclusive assignment when multiple drivers attempt to accept the same trip concurrently.

## 5.2.5 Data Storage Layer

PostgreSQL stores persistent business data using the database-per-service approach.

### Tables  
| Service | Tables |
|---------|---------|
| UserService | users |
| DriverService | drivers |
| TripService | trips |

Indexes are applied on frequently queried columns such as `status`, `driver_id`, and `created_at` in the `trips` table.

## 5.2.6 Infrastructure & Scaling Layer

Docker Compose orchestrates all services.

### Characteristics  
- Fully containerized architecture  
- Stateless microservices  
- Internal Docker network between services  
- Architecture prepared for horizontal scaling in future deployments  

## 5.2.7 Observability & Control Layer

### Tools  
- Docker logs for each service  
- RabbitMQ Management UI  
- RedisInsight  
- PostgreSQL CLI/GUI  

### Prometheus Integration  
Prometheus scrapes metrics exposed by TripService and DriverService.  
It collects latency, throughput, system metrics, JVM/NodeJS metrics, queue depth, and Redis performance indicators.

### Grafana Integration  
Grafana visualizes Prometheus metrics via dashboards showing:  
- Trip creation latency  
- Driver assignment latency  
- RabbitMQ queue depth  
- Redis GEO command rate  
- Database connection activity  
- k6 load levels correlated with system metrics  

### K6 Integration  
K6 exports metrics to Prometheus via remote-write.  
This enables correlation between load generation and backend performance on Grafana dashboards.

---

# 6. Performance & Load Testing  
### Module A — Scalability Validation

## 6.1 Overview  

Performance and scalability validation is conducted with **k6**, with metrics exported to **Prometheus** and visualized through **Grafana**.  
This allows direct correlation between injected load (virtual users), HTTP response behavior, and backend resource utilization (CPU, memory, Redis, RabbitMQ, PostgreSQL).

Five scenarios are defined to cover both typical and extreme usage patterns of UIT-Go:

1. Baseline Load Test (TripService only)  
2. Passenger–Driver End-to-End Workflow  
3. Trip Spike Test  
4. Spike Test with Real Driver Pool (200 drivers)  
5. Ramp-Up Unlimited (0 → 3000 virtual users)

## 6.2 Test Environment  

| Component | Configuration |
|----------|---------------|
| Load tool | k6 (local CLI) |
| Visualization | Grafana |
| Metrics database | Prometheus |
| Runtime | Docker Compose |
| Services under test | TripService, DriverService |
| Message broker | RabbitMQ |
| Cache & GEO | Redis (GEOADD / GEORADIUS / SETNX) |
| Database | PostgreSQL (single instance) |
| Driver pool | 50–200 driver IDs (depending on scenario) |

## 6.3 Metrics  

The following metrics are collected and analyzed:

- **p(95) latency** – 95th percentile of response time for HTTP requests  
- **Throughput (req/s)** – number of successfully processed HTTP requests per second  
- **Error rate** – ratio of failed HTTP requests over total requests  
- **Trip creation success rate** – percentage of `/trips` requests resulting in a valid trip record  
- **Assignment correctness** – whether each trip is assigned to at most one driver  
- **Duplicate assignment count** – number of cases where two drivers attempt to accept the same trip  
- **RabbitMQ stability** – queue depth and consumer lag for `trip.requested` and `trip.assigned`  
- **Redis lock behavior** – SETNX success/failure patterns under concurrency  
- **System metrics** – CPU, memory, and I/O usage from Prometheus during tests  

## 6.4 Test Scenarios  

This section describes in detail the five k6-based scenarios used to validate Module A.

---

### 6.4.1 Scenario 1 – Baseline Load Test (TripService Only)  

**Objective**  
Establish a baseline for TripService performance under moderate load without any driver interaction or event-driven processing. This scenario isolates the HTTP layer, business logic, and PostgreSQL writes for trip creation.

**Workload Description**  
- Repeatedly sends `POST /trips` requests.  
- Each request contains a randomly generated passenger ID and randomized pickup/dropoff coordinates within the UIT-Go operating area.  
- No driver updates, no assignment logic, and no RabbitMQ involvement.  

**Load Profile (k6 stages)**  
```js
stages: [
  { duration: '30s', target: 20 },
  { duration: '1m',  target: 50 },
  { duration: '30s', target: 0 },
]
```

**Main Endpoints**  
- `POST /api/v1/trips`  

**Expected Outcomes**  
- p(95) latency remains below 2000 ms.  
- Error rate remains below 1%.  
- PostgreSQL handles sustained inserts without noticeable degradation.  
- No saturation or throttling is observed at the API Gateway or TripService level.

---

### 6.4.2 Scenario 2 – Passenger–Driver End-to-End Workflow  

**Objective**  
Validate the correctness and stability of the full Trip Assignment workflow, including:

- Driver presence and availability  
- Real-time location updates  
- Trip creation by passengers  
- Driver trip acceptance  
- Trip lifecycle transitions (start and complete)  

This scenario tests the integration between TripService, DriverService, RabbitMQ, Redis GEO, and Redis SETNX.

**Workload Description**  
Each virtual user simulates one passenger–driver pair:

1. Mark a driver as online:  
   - `PUT /drivers/{driverId}/online`  
2. Update driver location near the passenger pickup:  
   - `PUT /drivers/{driverId}/location?lat=...&lng=...`  
3. Passenger creates a trip:  
   - `POST /trips`  
4. Driver attempts to accept the assigned trip:  
   - `PUT /drivers/{driverId}/trips/{tripId}/accept`  
5. If accepted successfully, TripService is called to:  
   - Start trip: `POST /trips/{tripId}/start`  
   - Complete trip: `POST /trips/{tripId}/complete`  

A pool of 50 drivers is reused across all virtual users to emulate a realistic fleet.

**Load Profile**  
```js
stages: [
  { duration: '30s', target: 10 },
  { duration: '1m',  target: 20 },
  { duration: '30s', target: 0 },
]
```

**Main Endpoints**  
- `PUT /drivers/{id}/online`  
- `PUT /drivers/{id}/location`  
- `POST /trips`  
- `PUT /drivers/{id}/trips/{tripId}/accept`  
- `POST /trips/{tripId}/start`  
- `POST /trips/{tripId}/complete`  

**Expected Outcomes**  
- Majority of trips successfully progress from creation to completion.  
- No trip is assigned to more than one driver (no duplicate assignment).  
- Redis GEO lookups return nearby drivers consistently.  
- Redis SETNX ensures that only one driver acquires the trip lock.  
- Error rate remains under 5%, accepting some failures due to natural race conditions.

---

### 6.4.3 Scenario 3 – Trip Spike Test  

**Objective**  
Evaluate how TripService behaves under a sudden surge of trip creation traffic, simulating a “peak hour” where many passengers request rides simultaneously.

**Workload Description**  
- Focused solely on trip creation.  
- Virtual users continuously send `POST /trips` with random passenger IDs and coordinates.  
- No driver or assignment behavior is included in this scenario, which isolates trip creation throughput and database write performance.

**Load Profile**  
```js
stages: [
  { duration: '10s', target: 10 },
  { duration: '20s', target: 200 },
  { duration: '1m',  target: 200 },
  { duration: '10s', target: 0 },
]
```

**Main Endpoints**  
- `POST /trips`  

**Expected Outcomes**  
- TripService remains responsive during the spike.  
- Error rate remains below 5% even at 200 virtual users.  
- p(95) latency stays under 3000 ms during the peak phase.  
- No widespread timeouts or connection errors.  
- PostgreSQL and the API layer do not crash or become unrecoverable after the spike.

---

### 6.4.4 Scenario 4 – Spike Test with Real Driver Pool (200 Drivers)  

**Objective**  
Simulate a realistic high-load situation where a large pool of real drivers (imported from the database) are active while passengers are concurrently creating trips and drivers are attempting to accept them.

**Workload Description**  
- A list of ~200 real driver IDs is loaded from the database.  
- Each virtual user is mapped deterministically to one driver ID (based on VU index) to evenly distribute load across the driver pool.  
- For each iteration, the virtual user:  
  1. Marks the driver online and updates location.  
  2. Creates a trip as a passenger.  
  3. Attempts to accept the trip on behalf of that driver, with limited retry logic to handle transient Redis issues.  

- Redis GEO is used to keep driver locations close to passenger pickup points.  
- Redis SETNX is used as a guardrail to prevent multiple drivers from successfully accepting the same trip.

**Load Profile**  
Typically mirrors the spike profile:

```js
stages: [
  { duration: '10s', target: 10 },
  { duration: '20s', target: 200 },
  { duration: '1m',  target: 200 },
  { duration: '10s', target: 0 },
]
```

**Main Endpoints**  
Same as Scenario 2, but with a larger and more realistic driver pool.

**Expected Outcomes**  
- Assignment correctness is preserved: for each trip, at most one driver is accepted.  
- The system may return HTTP 409 (conflict) or 400 for certain race cases, which are considered acceptable outcomes.  
- Error rate below 15% under heavy contention.  
- No systemic failures in RabbitMQ, Redis, or PostgreSQL.  
- SETNX lock conflicts are handled gracefully by business logic.

---

### 6.4.5 Scenario 5 – Ramp-Up Unlimited (0 → 3000 VUs)  

**Objective**  
Stress-test the system to identify its breaking point and understand how it behaves when approaching or exceeding capacity. This scenario is not about maintaining a strict SLO, but about discovering bottlenecks and failure modes.

**Workload Description**  
- Similar workflow to Scenario 4 (online + location + create trip + accept).  
- k6 gradually increases the number of virtual users up to 3000 over 30 minutes.  
- The environment is intentionally pushed beyond normal operating limits to observe failure patterns.

**Load Profile**  
```js
stages: [
  { duration: '30m', target: 3000 },
  { duration: '2m',  target: 3000 },
  { duration: '1m',  target: 0 },
]
```

**Main Endpoints**  
Same as Scenario 4.

**Expected Outcomes**  
- System continues functioning up to a certain threshold of concurrent VUs (to be identified in the results).  
- Increased error rates and higher latency are expected beyond capacity.  
- No catastrophic failure such as unbounded queue growth or unrecoverable crashes.  
- RabbitMQ, Redis, and PostgreSQL exhibit predictable degradation behavior rather than abrupt failure.  

---

# 6.5 Results Summary

This section provides a scenario-by-scenario analysis of performance, correctness, consistency, and stability under different load conditions. The results highlight the architectural strengths of Module A and reveal the natural contention points of a geospatial ride-matching system.

---

## 6.5.1 Scenario 1 — Baseline Load Test (TripService Only)

**Objective**  
Measure TripService performance in isolation, without driver assignment, Redis, or RabbitMQ.

**Key Results**
- **p95 latency:** 12.2 ms
- **avg latency:** 6.2 ms
- **error rate:** 0%
- **throughput:** ~26 req/s

**Interpretation**  
Trip creation is highly optimized and is **not a bottleneck**. PostgreSQL handles sustained inserts without observable lock contention or slow queries. This scenario establishes a strong baseline for subsequent, more complex tests.

---

## 6.5.2 Scenario 2 — Passenger–Driver End-to-End Workflow

**Objective**  
Validate the full workflow across services:

1. Driver online
2. Driver location update
3. Passenger trip creation
4. Driver accept (Redis SETNX lock)
5. Trip start and complete

**Key Results**
- **p95 latency:** 13.01 ms
- **error rate:** 0%
- **iteration duration:** ~3.56 s (dominated by scripted `sleep()` calls)
- **assignment correctness:** 100% (no duplicate assignments observed)

**Interpretation**  
The event-driven pipeline (RabbitMQ), Redis GEO, and Redis SETNX locking all behave correctly under moderate load. The low latency shows that cross-service calls, geospatial lookup, and message publishing introduce minimal overhead.

---

## 6.5.3 Scenario 3 — Trip Spike Test (200 VUs)

**Objective**  
Evaluate how TripService behaves under a sudden, short-term spike in trip creation requests (peak-hour simulation).

**Key Results**
- **p95 latency:** 42.32 ms
- **avg latency:** 15.64 ms
- **error rate:** 0%
- **throughput:** ~197 req/s

**Interpretation**  
TripService gracefully absorbs the spike, maintaining low latency and zero errors. PostgreSQL continues to handle write traffic without saturation. This confirms that the synchronous write path is robust under bursty conditions.

---

## 6.5.4 Scenario 4 — Spike Test with Real Driver Pool (200 Drivers)

**Objective**  
Simulate realistic concurrency with 200 real drivers:

- Drivers go online
- Update location
- Passengers create trips
- Drivers attempt to accept trips concurrently

**Key Results**
- **p95 latency:** 130.23 ms
- **avg latency:** 24.1 ms
- **HTTP error rate:** 38.87%
- **Driver Online Success:** ~99%
- **Trip Creation Success:** 100%
- **Driver Accept Success or Conflict (200/409):** ~93%

**Interpretation**  
The higher error rate is largely due to **expected contention on Redis SETNX locks** when multiple drivers attempt to accept the same trip. These are logical conflicts (e.g., 409) rather than infrastructure failures. The system remains responsive and stable: no crashes, no timeouts, and no message broker failures.

---

## 6.5.5 Scenario 5 — Ramp-Up Unlimited (0 → 3000 VUs)

**Objective**  
Identify the breaking point of the system and observe its behavior under extreme overload.

**Key Results**
- **avg latency:** 1.42 s
- **p95 latency:** 6.81 s
- **HTTP error rate:** 42.73%
- **Checks succeeded:** 91.20%
- **Trip Creation Success:** ~99%
- **Driver Accept Success or Conflict:** ~75%
- **Fatal timeout / HTTP 500:** None observed

**Interpretation**  
Beyond a certain threshold, the system exceeds its capacity, and latency grows significantly. However, the system **degrades gracefully**: services remain alive, data consistency is preserved, and no component (Redis, PostgreSQL, RabbitMQ, or the microservices) crashes. Trip creation continues to succeed in 99% of cases, even under extreme load.

---

# 6.6 Findings

This section distills the empirical results into broader architectural insights.

---

## 6.6.1 TripService is Not the Primary Bottleneck

Across all scenarios, TripService consistently maintains low latency and high success rates, even at high concurrency levels. Trip creation remains stable and reliable, with:

- Near-zero error rates in Scenarios 1–4
- ~99% success even at 3000 VUs in Scenario 5

**Implication:**  
The trip creation path (API → business logic → PostgreSQL) is highly optimized and can safely support higher user traffic, especially when scaled horizontally.

---

## 6.6.2 Driver Accept Path is the Natural Bottleneck

The most constrained part of the system is the **driver accept** flow, which uses Redis SETNX to ensure that only one driver can successfully claim a trip. Under heavy contention:

- Many accept attempts are rejected by design
- Error rates increase in Scenarios 4 and 5
- However, no duplicate assignments are produced

**Implication:**  
The bottleneck is **logical and intentional**: the architecture prioritizes correctness and exclusivity of assignment over raw throughput, which aligns with real-world ride-hailing requirements.

---

## 6.6.3 Graceful Degradation Instead of Catastrophic Failure

Even in Scenario 5 with 3000 VUs:

- No HTTP 500 errors were observed
- No systemic timeouts or service crashes occurred
- RabbitMQ did not exhibit backlog collapse
- Redis, PostgreSQL, and the microservices remained operational

**Implication:**  
The system degrades by increasing latency and rejecting conflicting operations rather than by failing outright. This is a desirable property of a production-grade distributed system.

---

## 6.6.4 Redis Stability Under Heavy Lock Contention

Redis is heavily stressed in Scenarios 4 and 5 due to:

- High-frequency GEO queries
- Thousands of concurrent SETNX operations for trip locking

Despite this, Redis remained stable, and lock behavior was consistent with expectations:

- Successful locks guarantee single-driver assignment
- Failed locks simply signal that another driver has already taken the trip

**Implication:**  
Redis is an appropriate choice for geospatial indexing and distributed locking at the tested scale, with clear paths for future scaling (e.g., sharding or clustering).

---

## 6.6.5 RabbitMQ Pipeline Robustness

RabbitMQ is responsible for transporting `trip.requested` and `trip.assigned` events between services. Across all scenarios:

- No message backlog overflow was observed
- Consumers did not starve
- There were no message-loss symptoms

**Implication:**  
The asynchronous, event-driven architecture is sound, and RabbitMQ can reliably decouple TripService and DriverService at the current throughput level.

---

## 6.6.6 Stateless Microservices Enable Predictable Scaling

TripService and DriverService are deliberately stateless, with all persistent state stored in PostgreSQL and Redis. The performance results show:

- Throughput and latency scale predictably with added load
- Failure modes are tied to external constraints (CPU, Redis lock contention), not hidden in-memory state

**Implication:**  
Horizontal scaling (e.g., adding more service instances behind an API Gateway or ECS/EKS) is a straightforward and effective future improvement.

---

# 6.7 Conclusion

The performance evaluation of UIT-Go Module A demonstrates that the proposed architecture is:

---

## 6.7.1 Scalable Under Realistic Load

For realistic usage scenarios (Scenarios 1–3 and moderate parts of Scenario 4), the system:

- Maintains low p95 latency (typically < 150 ms)
- Achieves high throughput on commodity hardware
- Processes trip creation and assignment consistently and correctly

This indicates that Module A can comfortably support production-like traffic patterns with headroom for growth.

---

## 6.7.2 Resilient Under Extreme Stress

In the extreme stress scenario (5) with up to 3000 virtual users:

- Services remain responsive and do not crash
- Trip creation continues to succeed for ~99% of requests
- The system correctly enforces mutual exclusion on trip assignment, even when many requests are rejected

The system’s behavior under overload is **predictable and controlled**, favoring data integrity over availability of all operations.

---

## 6.7.3 Correct by Design

Thanks to Redis SETNX locking and clear separation of responsibilities:

- Each trip is assigned to at most one driver
- Conflicting accept attempts are rejected cleanly
- No inconsistent or duplicated trip assignments were observed in any scenario

This confirms that consistency guarantees are upheld even under high contention.

---

## 6.7.4 Predictable and Graceful Degradation

As load increases beyond the capacity of the underlying hardware:

- Latency rises
- Logical conflicts (e.g., failed accepts) increase
- But the system does **not** exhibit catastrophic failures such as database crashes, message broker downtime, or unbounded queue buildup

This pattern of degradation is aligned with best practices for distributed system design.

---

## 6.7.5 Production Readiness and Future Work

Overall, Module A provides:

- A strong and tested foundation for trip creation and assignment
- Clear behavior under both normal and extreme conditions
- Transparent observability via k6, Prometheus, and Grafana

Future improvements can focus on:

- Horizontal scaling of TripService and DriverService
- Redis clustering or sharding to reduce lock contention hot spots
- Database read replicas and connection-pool tuning
- Fine-tuning retry policies and backoff strategies for accept logic

These enhancements would further increase the maximum sustainable throughput and reduce tail latency, but the current architecture already demonstrates production-grade robustness and correctness.

## 6.8 Tooling Architecture Diagram

```
┌──────────────┐        Prometheus Remote Write        ┌────────────────┐
│      k6       │ ------------------------------------> │  Prometheus     │
│ (Load Tests)  │                                       │  (Metrics DB)   │
└──────────────┘                                       └────────────────┘
           │                                                   │
           │  HTTP traffic                                     │
           ▼                                                   ▼
┌──────────────────┐                               ┌───────────────────────────┐
│ UIT-Go Services  │                               │         Grafana           │
│ Trip / Driver    │ <---------------------------- │  Visualization Layer       │
└──────────────────┘        metrics export         └───────────────────────────┘
```

## 7. Challenges & Solutions

> For detailed technical solutions and code examples, see [docs/CHALLENGES.md](./docs/CHALLENGES.md)

### 7.1 Technical Challenges

| Challenge | Problem | Solution |
|-----------|---------|----------|
| Race Condition | Two drivers accepting same trip | Redis distributed lock with TTL |
| Service Discovery | Services finding each other | Docker Compose networking + Kong |
| Event Routing | Messages reaching wrong consumers | Topic exchange with routing keys |
| WebSocket Auth | Securing real-time connections | JWT validation in handshake |
| Data Consistency | Driver status across requests | Redis as single source of truth |

### 7.2 Integration Challenges

| Challenge | Problem | Solution |
|-----------|---------|----------|
| RabbitMQ Setup | Exchange/queue binding complexity | Standardized naming convention |
| Cross-Service Events | Schema consistency | Shared event documentation |
| JWT Validation | Different frameworks | Same RSA key pair across services |

---

## 8. Results & Demo

### 8.1 Working Demo Flow

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

### 8.2 What Works

- Complete user authentication flow
- Trip booking and fare calculation
- Real-time driver notifications
- GPS location streaming with demo UI
- Trip lifecycle (request → assign → start → complete)

### 8.3 Known Limitations

| Limitation | Reason | Production Solution |
|------------|--------|---------------------|
| Single VM | Demo scale sufficient | Kubernetes cluster |
| No payment | Requires provider account | Stripe/VNPay integration |
| Basic auth | Meets requirements | OAuth2, refresh tokens |
| Monitoring | Planned via Docker | Prometheus + Grafana in docker-compose |

---

## 9. Conclusion

### 9.1 What We Achieved

UIT-Go demonstrates a **working microservices-based ride-hailing backend** with:

- 4 independently deployable services
- Event-driven architecture with RabbitMQ
- Real-time capabilities via WebSocket
- Geospatial queries with Redis GEO
- Infrastructure as Code with Terraform
- Cloud deployment on Azure

### 9.2 Skills Learned

| Area | Skills |
|------|--------|
| Architecture | Microservices design, API design, event-driven patterns |
| Backend | NestJS, Spring Boot, REST APIs, WebSocket |
| Data | PostgreSQL, Redis, message queues |
| DevOps | Docker, Terraform, cloud deployment |
| Problem Solving | Distributed locks, async communication, real-time systems |

### 9.3 Future Improvements

> For detailed future directions, see [docs/FUTURE.md](./docs/FUTURE.md)

If we had more time:
- Add Swagger API documentation
- Implement trip rating frontend
- Add comprehensive unit tests
- Set up monitoring dashboard

---

**Repository:** [https://github.com/Ama2352/UIT-Go](https://github.com/Ama2352/UIT-Go)  
**Last Updated:** November 2025
