# UIT-Go Design Decisions & Trade-offs

> **Document Type**: Technical Summary  
> **Module**: A — Scalability & Performance  
> **Last Updated**: November 2025  
> **Team**: Nguyen Thanh Kiet, Huynh Chi Hen, Ho Nguyen Minh Sang

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Decisions Matrix](#2-architecture-decisions-matrix)
3. [Technology Stack Rationale](#3-technology-stack-rationale)
4. [Trade-offs Analysis](#4-trade-offs-analysis)
5. [Decision Impact Assessment](#5-decision-impact-assessment)
6. [Cross-Cutting Concerns](#6-cross-cutting-concerns)

---

## 1. Executive Summary

UIT-Go's architecture is designed around **three core principles**:

1. **Simplicity First**: Choose technologies that enable rapid MVP delivery
2. **Scale Later**: Ensure clear upgrade paths without complete rewrites
3. **Cost Efficiency**: Optimize for a small team with limited resources

This document consolidates all architectural decisions, their rationale, and accepted trade-offs.

---

## 2. Architecture Decisions Matrix

| # | Decision | Choice | Alternatives | ADR |
|---|----------|--------|--------------|-----|
| 1 | In-Memory Data Store | Redis 7 | DynamoDB, Memcached | [ADR-001](./ADR/001-choose-redis-over-dynamodb.md) |
| 2 | API Communication | RESTful HTTP | gRPC, GraphQL | [ADR-002](./ADR/002-choose-RESTful-over-gRPC.md) |
| 3 | Message Broker | RabbitMQ | Apache Kafka, AWS SQS | [ADR-003](./ADR/003-choose-rabbitmq-over-kafka.md) |
| 4 | API Gateway | Kong (DB-less) | NGINX, AWS API Gateway | [ADR-004](./ADR/004-choose-kong-api-gateway.md) |
| 5 | Primary Database | PostgreSQL | MongoDB, MySQL | [ADR-005](./ADR/005-choose-postgresql-over-mongodb.md) |
| 6 | Real-Time Protocol | WebSocket (Socket.IO) | SSE, gRPC Streaming | [ADR-006](./ADR/006-choose-websocket-for-realtime-notifications.md) |
| 7 | Infrastructure | Terraform (Multi-cloud) | CloudFormation, ARM | [ADR-007](./ADR/007-choose-terraform-multi-cloud.md) |
| 8 | Trip Assignment | Redis Distributed Lock | DB Optimistic Lock, Saga | [ADR-008](./ADR/008-distributed-trip-assignment-lock.md) |

---

## 3. Technology Stack Rationale

### 3.1 Backend Services

| Service | Framework | Language | Rationale |
|---------|-----------|----------|-----------|
| User Service | NestJS | TypeScript | Modern, decorator-based, excellent Prisma integration |
| Trip Service | Spring Boot | Java | Enterprise-grade, mature ecosystem |
| Driver Service | Spring Boot | Java | Consistent with Trip Service, Redis integration |
| Notification Service | NestJS | TypeScript | Native Socket.IO support, async patterns |

**Why Two Frameworks?**

The polyglot approach leverages each framework's strengths:

- **NestJS**: Better for real-time (WebSocket), rapid API development
- **Spring Boot**: Better for complex domain logic, strong typing, enterprise patterns

### 3.2 Data Layer

```
+---------------------------------------------------------------+
|                      Data Architecture                         |
+---------------+-----------------+-----------------------------+
|  PostgreSQL   |     Redis       |        RabbitMQ             |
|  (Persistent) |   (Real-time)   |       (Async)               |
+---------------+-----------------+-----------------------------+
| - User profiles| - Driver locs   | - trip.requested            |
| - Trip records | - Driver status | - trip.assigned             |
| - Trip ratings | - Trip locks    | - trip.started/completed    |
| - Access tokens| - Session cache | - trip.offered              |
+---------------+-----------------+-----------------------------+
```

Note: Driver Service uses **Redis only** (no PostgreSQL database).

### 3.3 Infrastructure

| Component | Technology | Purpose |
|-----------|------------|----------|
| API Gateway | Kong 3.6 (DB-less) | Routing, JWT auth, rate limiting |
| Container Runtime | Docker | Consistent environments |
| Orchestration | Docker Compose | Service dependencies |
| IaC | Terraform | Multi-cloud provisioning |
| CI/CD | GitHub Actions | Automated deployment |
| Cloud Provider | Azure | VM hosting (free student credits) |

---

## 4. Trade-offs Analysis

### 4.1 Simplicity vs. Scalability

| Decision | Simplicity Gained | Scalability Sacrificed | Upgrade Path |
|----------|-------------------|------------------------|--------------|
| Docker Compose over K8s | Single-file config, easy debugging | No auto-scaling | Migrate to AKS |
| Single VM | Simple deployment | Single point of failure | Add VMs + LB |
| RabbitMQ over Kafka | Lower ops overhead | ~50k msg/s limit | AWS MSK |
| Redis Lock in Driver Svc | Reuses existing Redis | Cross-service coupling | Move to Trip Svc |

### 4.2 Performance vs. Consistency

| Scenario | Choice | Trade-off |
|----------|--------|-----------|
| Driver location | Redis (eventual) | ~100ms staleness acceptable |
| Trip booking | PostgreSQL (strong) | Higher latency, but ACID |
| Payment processing | PostgreSQL + TX | Full consistency required |

### 4.3 Cost vs. Capability

| Resource | Choice | Monthly Cost | Enterprise Alternative |
|----------|--------|--------------|------------------------|
| VM | Azure B2s | ~$30 (free via student credits) | AKS cluster (~$200+) |
| Database | Self-hosted PostgreSQL | $0 (on VM) | Azure Database ($50+) |
| Message Queue | Self-hosted RabbitMQ | $0 (on VM) | Azure Service Bus ($100+) |

**Note**: Azure was chosen because of **$100 one-time credit** from the Azure for Students program (education package via university email). This credit covers ~2 months of infrastructure costs for the MVP.

---

## 5. Decision Impact Assessment

### 5.1 Development Velocity

| Decision | Impact | Evidence |
|----------|--------|----------|
| NestJS for User/Notification | Positive | Rapid API scaffolding with decorators |
| Prisma ORM | Positive | Type-safe queries, auto-migrations |
| Kong DB-less | Positive | Version-controlled config |
| Docker Compose | Positive | One command to start all services |

### 5.2 Operational Complexity

| Decision | Complexity | Mitigation |
|----------|------------|------------|
| Polyglot (Java + TypeScript) | Medium | Clear service boundaries |
| Multiple PostgreSQL instances | Low | Database-per-service pattern |
| RabbitMQ management | Low | Simple topic exchange model |

### 5.3 Technical Debt

| Area | Current State | Future Work |
|------|--------------|-------------|
| Monitoring | Basic Docker logs | Implement Prometheus + Grafana |
| Secrets | Environment variables | HashiCorp Vault or Azure Key Vault |
| Testing | Unit tests only | Add integration + load tests |
| Documentation | ADRs complete | Add OpenAPI specs |

---

## 6. Cross-Cutting Concerns

### 6.1 Security

| Layer | Implementation |
|-------|----------------|
| Transport | HTTPS (TLS 1.3) |
| Authentication | JWT RS256 (asymmetric) |
| Authorization | Role-based (PASSENGER, DRIVER, ADMIN) |
| Token Revocation | Redis blacklist |
| API Protection | Kong rate limiting plugin |

### 6.2 Observability (Planned)

```
┌───────────────────────────────────────────────────────────────┐
│                   Observability Stack                          │
├───────────────────┬───────────────────┬───────────────────────┤
│     Metrics       │     Logging       │      Tracing          │
├───────────────────┼───────────────────┼───────────────────────┤
│ Prometheus        │ Loki              │ OpenTelemetry         │
│ Grafana           │ Docker logs       │ Jaeger                │
└───────────────────┴───────────────────┴───────────────────────┘
```

### 6.3 Resilience Patterns

| Pattern | Status | Implementation | Service |
|---------|--------|----------------|---------|
| Manual ACK | Implemented | `noAck: false`, `channel.ack()` | Notification Service |
| Connection Retry | Implemented | `setTimeout(connect, 5000)` | Notification Service |
| Idempotency | Partial | Status check before update | TripAssignedListener only |
| Distributed Lock | Implemented | Redis SETNX with 30s TTL | Driver Service |
| Timeout | Implemented | Kong upstream config | All via API Gateway |
| Circuit Breaker | Planned | Resilience4j | Trip Service |
| Exponential Backoff | Not implemented | - | - |
| Dead Letter Queue | Not implemented | - | RabbitMQ |

---

## 7. Conclusion

UIT-Go's architecture prioritizes **rapid MVP delivery** while maintaining **clear evolution paths**. The decisions documented here represent intentional trade-offs between:

- **Now vs. Later**: Simple solutions with upgrade paths
- **Cost vs. Features**: Self-hosted infrastructure with cloud alternatives
- **Speed vs. Safety**: Type-safe languages with runtime validation

Each ADR contains detailed rationale and should be consulted for implementation specifics.

---

## References

- [ADR Folder](./ADR/) - All architectural decision records
- [Architecture Overview](../ARCHITECTURE.MD) - System diagrams
- [Deployment Guide](./DEPLOYMENT.md) - Production setup
