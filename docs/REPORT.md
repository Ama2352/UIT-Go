# UIT-Go Final Report

**Module A - Scalability & Performance**

**Team Members:**
- Nguyễn Thanh Kiệt (22520720)
- Huỳnh Chí Hên (23520455)
- Hồ Nguyễn Minh Sang (23521338)

**Date:** November 2025  
**Repository:** [https://github.com/Ama2352/UIT-Go](https://github.com/Ama2352/UIT-Go)

---

## 1. System Architecture Overview

### 1.1 Architecture Diagram

![UIT-Go Architecture](../assets/UIT-Go%20Architecture.png)

### 1.2 Architecture Description

UIT-Go is a **ride-hailing backend system** built with microservices architecture, designed for scalability and real-time communication.

**Core Services:**

| Service | Technology | Database | Responsibility |
|---------|------------|----------|----------------|
| User Service | NestJS | PostgreSQL | Authentication, user profiles, JWT token generation |
| Trip Service | Spring Boot | PostgreSQL | Trip lifecycle, fare calculation, booking management |
| Driver Service | Spring Boot | Redis | Location tracking, driver availability, geospatial queries |
| Notification Service | NestJS | - | WebSocket gateway, real-time event delivery |

**Infrastructure Components:**

| Component | Technology | Purpose |
|-----------|------------|---------|
| API Gateway | Kong (DB-less) | Request routing, JWT validation, rate limiting |
| Message Broker | RabbitMQ | Async event-driven communication between services |
| Cache/GEO Store | Redis | Driver locations (GEO), distributed locks (SETNX) |
| Container Orchestration | Docker Compose | Local development and deployment |
| Infrastructure as Code | Terraform | Azure VM provisioning |

**Communication Patterns:**

```
Synchronous:  Client → Kong Gateway → Services (REST/HTTP)
Asynchronous: Services → RabbitMQ → Services (Topic Exchange)
Real-time:    Services → WebSocket → Clients (Socket.IO)
```

---

## 2. Module A Analysis: Scalability & Performance

### 2.1 Approach

Module A focuses on building a **scalable, event-driven backend** that can handle concurrent trip requests while maintaining data consistency. Our approach emphasizes:

1. **Stateless Services** - All business logic services store no in-memory state, enabling horizontal scaling
2. **Event-Driven Architecture** - RabbitMQ decouples services for async processing
3. **Geospatial Indexing** - Redis GEO for sub-10ms driver location queries
4. **Distributed Locking** - Redis SETNX prevents race conditions in trip assignment

### 2.2 Key Implementation

**Trip Assignment Flow:**
```
1. Passenger creates trip → TripService saves to PostgreSQL
2. TripService publishes "trip.requested" → RabbitMQ
3. DriverService consumes event → Redis GEO finds nearby drivers
4. Driver accepts → Redis SETNX acquires lock (prevents double-assignment)
5. DriverService publishes "trip.assigned" → NotificationService notifies passenger
```

**Distributed Lock Implementation:**
```java
public boolean acquireLock(UUID tripId, String driverId) {
    String key = "lock:trip:assign:" + tripId;
    return redisTemplate.opsForValue()
        .setIfAbsent(key, driverId, Duration.ofSeconds(30));
}
```

### 2.3 Load Testing Results

We conducted 5 test scenarios using **k6** with metrics exported to **Prometheus/Grafana**:

| Scenario | Virtual Users | p95 Latency | Error Rate | Key Finding |
|----------|---------------|-------------|------------|-------------|
| Baseline (Trip creation) | 50 | 12.2 ms | 0% | PostgreSQL handles writes efficiently |
| End-to-End Workflow | 20 | 13.01 ms | 0% | Full pipeline works correctly |
| Trip Spike | 200 | 42.32 ms | 0% | System absorbs traffic spikes |
| Real Driver Pool | 200 | 130.23 ms | 38.87%* | Lock contention is expected |
| Stress Test | 3000 | 6.81 s | 42.73%* | Graceful degradation, no crashes |

*\*High error rates in Scenarios 4-5 are due to **intentional lock conflicts** (HTTP 409) when multiple drivers attempt to accept the same trip - this is correct behavior.*

**Key Performance Findings:**
- Trip creation maintains ~99% success rate even at 3000 VUs
- No HTTP 500 errors or service crashes observed
- System degrades gracefully by increasing latency, not failing catastrophically
- Redis SETNX correctly prevents duplicate trip assignments in all scenarios

---

## 3. Design Decisions & Trade-offs

This section summarizes our 8 Architectural Decision Records (ADRs). Full details available in [docs/ADR/](./ADR/).

### 3.1 Redis over DynamoDB for Driver Locations

| Aspect | Our Choice | Alternative | Trade-off |
|--------|------------|-------------|-----------|
| **Technology** | Redis (self-hosted) | AWS DynamoDB | |
| **Why Chosen** | Built-in GEO commands, sub-10ms latency, simple setup | Managed service, auto-scaling | |
| **Sacrificed** | Managed infrastructure, built-in replication | Lower latency, simpler operations | |
| **Cost** | Free (Docker container) | Pay-per-request pricing | |

**Decision Rationale:** For a student project with budget constraints, Redis provides the geospatial capabilities we need (GEOADD, GEORADIUS) without cloud costs. The trade-off is manual scaling and no built-in persistence, acceptable for our demo scale.

### 3.2 RESTful API over gRPC

| Aspect | Our Choice | Alternative | Trade-off |
|--------|------------|-------------|-----------|
| **Technology** | REST + JSON | gRPC + Protobuf | |
| **Why Chosen** | Universal tooling, easy debugging, team familiarity | Higher performance, type safety | |
| **Sacrificed** | ~30% bandwidth overhead, no streaming | Simpler development, browser compatibility | |

**Decision Rationale:** REST's simplicity and tooling (Postman, curl, browser) accelerated development. For internal service-to-service calls, we use RabbitMQ events instead, so gRPC's performance benefits weren't critical.

### 3.3 RabbitMQ over Kafka

| Aspect | Our Choice | Alternative | Trade-off |
|--------|------------|-------------|-----------|
| **Technology** | RabbitMQ | Apache Kafka | |
| **Why Chosen** | Simple setup, topic routing, sufficient throughput | Higher throughput, event replay | |
| **Sacrificed** | Event sourcing, unlimited retention | Operational simplicity | |

**Decision Rationale:** RabbitMQ's topic exchange perfectly fits our routing needs (trip.requested, trip.assigned, etc.). Kafka's complexity wasn't justified for our ~200 req/s throughput requirement.

### 3.4 Kong DB-less over Ambassador/Traefik

| Aspect | Our Choice | Alternative | Trade-off |
|--------|------------|-------------|-----------|
| **Technology** | Kong (DB-less mode) | Ambassador, Traefik | |
| **Why Chosen** | Declarative YAML config, built-in JWT plugin, no database needed | Dynamic config, Kubernetes-native | |
| **Sacrificed** | Dynamic updates (requires restart) | Simple file-based configuration | |

### 3.5 PostgreSQL as Primary Database

| Aspect | Our Choice | Alternative | Trade-off |
|--------|------------|-------------|-----------|
| **Technology** | PostgreSQL | MongoDB, MySQL | |
| **Why Chosen** | ACID compliance, mature ecosystem, Prisma/JPA support | Document flexibility | |
| **Sacrificed** | Schema flexibility | Strong consistency, relational integrity | |

### 3.6 WebSocket for Real-Time Communication

| Aspect | Our Choice | Alternative | Trade-off |
|--------|------------|-------------|-----------|
| **Technology** | Socket.IO + Native WebSocket | Server-Sent Events, Long Polling | |
| **Why Chosen** | Bidirectional, low latency, auto-reconnection | Simpler implementation | |
| **Sacrificed** | HTTP caching, simpler debugging | Full-duplex communication | |

### 3.7 Terraform for Infrastructure

| Aspect | Our Choice | Alternative | Trade-off |
|--------|------------|-------------|-----------|
| **Technology** | Terraform | Azure ARM, Pulumi | |
| **Why Chosen** | Multi-cloud support, declarative, version-controlled | Azure-native features | |
| **Sacrificed** | Azure-specific optimizations | Cloud-agnostic IaC | |

### 3.8 Redis SETNX for Race Condition Prevention

| Aspect | Our Choice | Alternative | Trade-off |
|--------|------------|-------------|-----------|
| **Technology** | Redis SETNX with TTL | Database optimistic locking, Redlock | |
| **Why Chosen** | Simple, fast, automatic expiry | Stronger consistency guarantees | |
| **Sacrificed** | Multi-node consistency (Redlock) | Implementation simplicity | |

**Decision Rationale:** Single Redis instance with SETNX is sufficient for our scale. The 30-second TTL auto-releases locks if a service crashes, preventing deadlocks.

### 3.9 Summary: Simplicity over Enterprise Scale

| Trade-off Category | What We Chose | What We Sacrificed |
|--------------------|---------------|-------------------|
| **Infrastructure** | Docker Compose | Kubernetes auto-scaling |
| **Database** | Self-hosted PostgreSQL | Azure Database managed service |
| **Caching** | Single Redis instance | Redis Cluster |
| **Deployment** | Single Azure VM | Multi-region, load-balanced |
| **Features** | Core trip flow | Payment integration, ratings |

**Overall Rationale:** As a student project with Azure for Students $100 credit, we prioritized:
- **Learning** over production-ready infrastructure
- **Simplicity** over enterprise patterns
- **Cost-efficiency** over managed services

---

## 4. Challenges & Lessons Learned

### 4.1 Technical Challenges

| Challenge | Problem | Solution | Lesson Learned |
|-----------|---------|----------|----------------|
| **Race Condition** | Two drivers accepting same trip simultaneously | Redis SETNX distributed lock with 30s TTL | Distributed systems need explicit coordination |
| **JWT Across Frameworks** | NestJS and Spring Boot handling same tokens | Shared RSA key pair, Kong validates at gateway | Centralize auth at API Gateway level |
| **RabbitMQ Routing** | Messages reaching wrong consumers | Topic exchange with specific routing keys (trip.*) | Design event schema before implementation |
| **WebSocket Authentication** | Securing real-time connections | JWT validation during handshake, not per-message | Auth should happen at connection time |
| **Service Discovery** | Services finding each other in Docker | Docker Compose networking + service names as hostnames | Container networking simplifies discovery |

### 4.2 Integration Challenges

| Challenge | Problem | Solution |
|-----------|---------|----------|
| **Event Schema Consistency** | Services expecting different JSON structures | Documented shared event contracts in ADR |
| **Database Migrations** | Coordinating schema changes across services | Flyway (Java) + Prisma Migrate (Node.js) |
| **Environment Variables** | Different config formats per framework | Unified .env file at project root |

### 4.3 Key Lessons

1. **Start with Event Contracts** - Define message schemas before coding consumers
2. **Centralize Cross-Cutting Concerns** - Auth, logging, rate-limiting belong at the gateway
3. **Design for Failure** - Every external call can fail; use timeouts and retries
4. **Test Concurrency Early** - Race conditions are hard to debug in production
5. **Keep It Simple** - Docker Compose is sufficient for learning; skip Kubernetes until needed

---

## 5. Results & Future Development

### 5.1 Achieved Results

**Functional Achievements:**
- Complete user registration and JWT authentication flow
- Trip booking with fare estimation based on distance
- Real-time driver matching using Redis GEO (5km radius search)
- Race condition prevention with distributed locking
- WebSocket notifications for trip status updates
- GPS location streaming from drivers

**Performance Achievements:**
- p95 latency < 150ms under normal load (200 VUs)
- 99% trip creation success rate even at 3000 VUs
- Zero duplicate trip assignments across all test scenarios
- Graceful degradation without service crashes

**Infrastructure Achievements:**
- Fully containerized deployment with Docker Compose
- Infrastructure as Code with Terraform for Azure
- CI/CD pipeline with GitHub Actions

### 5.2 Current Limitations

| Limitation | Reason | Impact |
|------------|--------|--------|
| Single VM deployment | Budget constraint ($100 credit) | No high availability |
| No payment integration | Requires payment provider account | Demo-only booking flow |
| Basic monitoring | Time constraint | Manual log inspection |
| No frontend | Backend-focused project | API testing via Postman |

### 5.3 Future Improvements

**Short-term (with more time):**
- Add Swagger/OpenAPI documentation for all endpoints
- Implement Prometheus + Grafana monitoring stack
- Add comprehensive unit and integration tests
- Create simple web frontend for demo

**Long-term (production-ready):**
- Migrate to Kubernetes for auto-scaling
- Implement Redis Cluster for high availability
- Add payment gateway integration (Stripe/VNPay)
- Implement driver rating and review system
- Add surge pricing based on demand

### 5.4 Conclusion

UIT-Go demonstrates a **working microservices architecture** for ride-hailing, successfully implementing:

- **Scalability**: Stateless services ready for horizontal scaling
- **Real-time**: WebSocket notifications and GPS streaming
- **Consistency**: Distributed locking prevents race conditions
- **Event-driven**: RabbitMQ decouples services effectively

The project provided hands-on experience with distributed systems challenges that cannot be learned from textbooks alone: coordinating concurrent access, designing event schemas, and making pragmatic trade-offs between ideal architecture and practical constraints.

---

**Repository:** [https://github.com/Ama2352/UIT-Go](https://github.com/Ama2352/UIT-Go)  
**Last Updated:** November 2025
