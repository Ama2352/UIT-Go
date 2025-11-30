# ADR 002: Choosing RESTful API over gRPC

## Status

**Accepted** — November 2025

---

## Context

UIT-Go requires an API communication protocol for:

1. **Client-to-Gateway**: Mobile apps and web frontend
2. **Gateway-to-Services**: Kong to backend microservices
3. **Service-to-Service**: Internal synchronous calls (limited)

### Requirements

| Requirement | Priority | Notes |
|-------------|----------|-------|
| Mobile compatibility | Critical | iOS/Android native apps |
| Web browser support | Critical | React/Vue frontend |
| Developer experience | High | Easy debugging and testing |
| Tooling ecosystem | High | Postman, curl, OpenAPI |
| Performance | Medium | Acceptable latency for ride-hailing |

### Options Evaluated

1. **RESTful HTTP/JSON**: Industry standard, ubiquitous support
2. **gRPC**: High-performance, typed contracts
3. **GraphQL**: Flexible queries, single endpoint

---

## Decision

We chose **RESTful HTTP/JSON** for all external APIs and **RabbitMQ** for asynchronous internal communication.

---

## Rationale

### 1. Universal Client Compatibility

REST works natively with:

| Platform | REST Support | gRPC Support |
|----------|--------------|--------------|
| Web browsers | Native fetch/axios | Requires grpc-web |
| iOS Swift | URLSession | Requires protobuf |
| Android Kotlin | Retrofit | Requires protobuf |
| Postman/curl | Native | Limited |

### 2. Debugging and Development Velocity

REST APIs are immediately testable:

```bash
# Test with curl
curl -X POST http://localhost:8000/trips \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pickupLat": 10.82, "pickupLng": 106.62}'

# Response is human-readable JSON
{
  "id": "trip-123",
  "status": "REQUESTED",
  "fare": 45000
}
```

### 3. Mature Ecosystem

| Tooling | REST | gRPC |
|---------|------|------|
| API Documentation | OpenAPI/Swagger | Protobuf files |
| Testing | Postman, Insomnia | BloomRPC, grpcurl |
| Mocking | MockServer, WireMock | Limited options |
| Caching | HTTP Cache-Control | Custom implementation |
| Kong Gateway | Native support | Plugin required |

### 4. HTTP Caching Benefits

GET requests can leverage HTTP caching:

```http
GET /trips/123 HTTP/1.1
If-None-Match: "abc123"

HTTP/1.1 304 Not Modified
```

### 5. OpenAPI for Documentation

Auto-generated, interactive API docs:

```yaml
openapi: 3.0.0
paths:
  /trips:
    post:
      summary: Create a new trip
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateTripRequest'
      responses:
        201:
          description: Trip created
```

---

## API Design Standards

### URL Structure

```
/users              # User management
/sessions           # Authentication
/trips              # Trip lifecycle
/trips/{id}/accept  # Action on resource
/drivers            # Driver operations
/drivers/search     # Query endpoint
```

### HTTP Methods

| Method | Usage | Example |
|--------|-------|---------|
| GET | Read resource | `GET /trips/123` |
| POST | Create resource | `POST /trips` |
| PUT | Update resource | `PUT /users/me` |
| DELETE | Remove resource | `DELETE /sessions` (logout) |

### Response Format

```json
{
  "data": {
    "id": "trip-123",
    "status": "REQUESTED",
    "passenger": { "id": "user-456", "name": "John" },
    "fare": 45000
  },
  "meta": {
    "timestamp": "2025-11-15T10:30:00Z"
  }
}
```

### Error Format

```json
{
  "error": {
    "code": "TRIP_NOT_FOUND",
    "message": "Trip with ID xyz not found",
    "details": {
      "tripId": "xyz"
    }
  }
}
```

---

## Trade-offs Accepted

| Trade-off | Impact | Mitigation |
|-----------|--------|------------|
| **Lower performance than gRPC** | ~10-20% overhead | Acceptable for ride-hailing latency |
| **Larger payload size** | JSON vs Protobuf | Gzip compression |
| **No streaming** | Cannot push updates | Use WebSocket for real-time |
| **No type safety** | Runtime validation | TypeScript types + validation |

### Performance Comparison

| Metric | REST/JSON | gRPC/Protobuf |
|--------|-----------|---------------|
| Latency (p50) | ~15ms | ~10ms |
| Payload size | 100% | ~60% |
| CPU for serialization | Higher | Lower |
| Browser support | Native | Requires proxy |

---

## Alternatives Considered

### gRPC

**Pros**:
- Strongly typed contracts
- Efficient binary serialization
- Built-in streaming

**Cons**:
- Browser requires grpc-web proxy
- Steeper learning curve
- Less tooling for debugging

**When to consider**: High-throughput internal service communication

### GraphQL

**Pros**:
- Flexible queries
- Single endpoint
- Strong typing

**Cons**:
- Complexity for simple CRUD
- Caching challenges
- N+1 query risks

**When to consider**: Complex frontend data requirements

---

## Consequences

### Positive

- Rapid development with familiar patterns
- Easy debugging with standard HTTP tools
- Universal client compatibility
- Kong Gateway native support
- Comprehensive documentation with OpenAPI

### Negative

- Manual type synchronization between services
- No built-in streaming (use WebSocket instead)
- Slightly higher bandwidth than binary protocols

---

## Implementation Examples

### NestJS Controller (User Service)

```typescript
@Controller('users')
export class UserController {
  
  @Post()
  async createUser(@Body() dto: CreateUserDto): Promise<User> {
    return this.userService.create(dto);
  }
  
  @Get('me')
  @UseGuards(JwtAuthGuard)
  async getProfile(@CurrentUser() user: User): Promise<User> {
    return user;
  }
}
```

### Spring Boot Controller (Trip Service)

```java
@RestController
@RequestMapping("/trips")
public class TripController {
    
    @PostMapping
    public ResponseEntity<Trip> createTrip(
            @RequestBody CreateTripRequest request,
            @RequestHeader("X-User-Id") String userId) {
        Trip trip = tripService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(trip);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Trip> getTrip(@PathVariable UUID id) {
        return tripService.findById(id)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new TripNotFoundException(id));
    }
}
```

---

## Future Considerations

1. **gRPC for Internal Services**: If service-to-service latency becomes critical
2. **GraphQL Gateway**: For complex frontend aggregation needs
3. **API Versioning**: URL-based (`/v2/trips`) when breaking changes needed

---

## References

- [REST API Design Guidelines](https://restfulapi.net/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Kong REST vs gRPC](https://konghq.com/blog/engineering/grpc-gateway)
