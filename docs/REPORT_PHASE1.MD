# 🧾 UIT-Go Midterm Report  
### Module A – Scalability & Performance  
**Team Members:**  
- Nguyễn Thanh Kiệt (22520720)  
- Huỳnh Chí Hên (23520455)  
- Hồ Nguyễn Minh Sang (23521338)  

**Date:** November 2025  
**Repository:** [https://github.com/Ama2352/UIT-Go](https://github.com/Ama2352/UIT-Go)


---

## 📑 Table of Contents  
1. [System Architecture Overview](#1-system-architecture-overview)  
2. [Deep Dive: Module A – Scalability & Performance](#2-deep-dive-module-a--scalability--performance)  
3. [Key Design Decisions & Trade-offs](#3-key-design-decisions--trade-offs)  
4. [Challenges & Lessons Learned](#4-challenges--lessons-learned)  
5. [Results & Future Directions](#5-results--future-directions)  

---

## 1. System Architecture Overview  

UIT-Go is a **distributed backend system** designed to simulate a production-grade ride-hailing platform.  
The architecture follows a **domain-driven microservices** model, focusing on **high scalability, low latency, and fault tolerance under heavy traffic**.

> For a detailed breakdown of the system components, microservices, and communication flows, please refer to  
> 🔗 [**Section II – Core Architecture in `ARCHITECTURE.MD`**](./ARCHITECTURE.MD#ii-core-architecture).

### 🔧 Core Architecture Summary
- **API Gateway:** Entry point for clients, handles routing and JWT authentication.  
- **Trip Service:** Manages trip lifecycle and driver matching.  
- **Driver Service:** Tracks driver location in real time using Redis GEO.  
- **User Service:** Maintains passenger and driver data.  
- **Payment Service:** Computes fares and manages transactions.  
- **Notification Service:** Publishes real-time events via RabbitMQ + WebSocket.  

### 📊 High-Level Architecture Diagram
![Event-Driven Architecture](./assets/event-driven-architecture.png)

> *Figure 1 – Event-driven architecture enabling independent, decoupled communication through RabbitMQ and Redis.*

> For a full explanation of each service’s role, scaling pattern, and communication type (REST, MQ, WebSocket), see  
> 🔗 [**System Context and Communication Patterns in `ARCHITECTURE.MD`**](./ARCHITECTURE.MD#i-system-context).

---

## 2. Deep Dive: Module A – Scalability & Performance  

Module A focuses on **evaluating and optimizing the system’s ability to handle extreme concurrency** using asynchronous communication, caching, and auto-scaling strategies.

### 🎯 Approach  

#### a. Scalable Architecture Stack  
Module A is structured into **seven layers** ensuring vertical scalability and fault tolerance:  

```text
CLIENT & ACCESS → APPLICATION SERVICE → MESSAGING & ASYNC
→ CACHING & GEO → DATA STORAGE → INFRASTRUCTURE & SCALING → OBSERVABILITY
```

> For detailed description of each layer, refer to  
> 🔗 [**Section 4.2 – Scalable Architecture Stack for Module A in `ARCHITECTURE.MD`**](./ARCHITECTURE.MD#42-scalable-architecture-stack-for-module-a).

#### b. Testing Methodology  
| Objective | Tool | Description |
|------------|------|-------------|
| Load & Stress Test | **k6 / JMeter** | Generate workloads (5k–20k RPS). |
| Monitoring | **Grafana + CloudWatch** | Track CPU, latency, queue depth. |
| Tracing | **AWS X-Ray / OpenTelemetry** | Trace request flow across microservices. |

#### c. Early Results  (To Be Validated)


---

## 3. Key Design Decisions & Trade-offs  

This section consolidates the **main Architectural Decision Records (ADRs)** for Module A.

| **Decision** | **Alternatives** | **Reason for Choice** | **Trade-off** |
|---------------|------------------|------------------------|----------------|
| **RabbitMQ over Kafka** | Kafka (complex cluster) | Easier to operate, fits async model | Lower throughput (~50k msg/s) |
| **Redis GEO over DynamoDB** | DynamoDB Geo Library for spatial indexing | Sub-10 ms in-memory lookup, ideal for real-time driver matching | Higher RAM usage, requires TTL cleanup |
| **Read Replicas for PostgreSQL** | Single instance | Scales read-heavy workloads | Extra cost |
| **Auto-scale by Queue Depth & Latency** | CPU-based scaling | Reflects real load | Needs cooldown tuning |
| **Idempotent Consumers** | No duplication control | Prevent duplicate trip assignment | Complex logic |
| **Async MQ over Pure REST** | REST-only | Decouples services | Slightly higher latency |

> Full ADR discussions, context, and rationale are documented in  
> 🔗 [**ADR Folder (Architectural Decision Records)**](./ADR/)

---

## 4. Current Development Challenges  

At the current stage, the team has implemented the core microservices and is integrating **RabbitMQ**, **Redis**, and **API Gateway with WebSocket**.  
The following challenges are being encountered during this phase of development:

1. **RabbitMQ Integration Complexity**  
   - Managing message routing between TripService → DriverService → NotificationService.  
   - Need to design a consistent exchange–queue–binding pattern for event-driven flows.

2. **Redis GEO Setup and Data Sync**  
   - Ensuring accurate real-time driver location updates between Redis and PostgreSQL.  
   - Defining TTL and cleanup policies without impacting data freshness.

3. **API Gateway with WebSocket**  
   - Handling bidirectional communication for real-time trip status and driver updates.  
   - Managing concurrent WebSocket connections and ensuring proper authentication.

4. **Service Communication Consistency**  
   - Maintaining consistent message schemas and event naming across multiple services.  
   - Avoiding duplication between REST and MQ events during hybrid testing.

> These challenges are currently under active development and testing.  
> Future iterations will include load testing and architectural validation once the integration layer is stable.

## 5. Current Progress & Future Directions  

#### ⚙️ Current Progress
At this stage, the core microservices of **UIT-Go** (Trip, Driver, User) have been developed, containerized, and integrated via Docker Compose.  
The team is currently working on:
- Implementing **RabbitMQ** for asynchronous communication between services.  
- Setting up **Redis GEO** for real-time driver location tracking.  
- Integrating **API Gateway + WebSocket** for live trip updates.  

These components form the foundation of Module A’s scalability layer.  
Performance validation and load testing will be conducted after completing the integration and configuration phases.

#### 🚀 Planned Next Steps
1. **Complete RabbitMQ and Redis integration** for asynchronous trip–driver matching.  
2. **Implement WebSocket notification gateway** for real-time updates.  
3. **Run baseline load tests** (via k6 / JMeter) to collect p95 latency and queue-depth data.  
4. **Set up Grafana + CloudWatch dashboards** for observability.  
5. **Document ADR results** and finalize architectural tuning for submission.

> Detailed optimization objectives and performance targets are documented in  
> 🔗 [**Module A Optimization & Validation Plan in `ARCHITECTURE.MD`**](./ARCHITECTURE.MD#42-implementation-plan-for-module-a).


---

> **Conclusion:**  
> Module A validates the system’s scalability and resilience through an event-driven, asynchronous architecture.  
> It establishes a foundation for large-scale performance while balancing **cost, complexity, and reliability**, paving the way for subsequent modules such as Security and FinOps.

---

**📂 File:** `REPORT.MD`  
**Last Updated:** November 2025  
