# ADR 004: Choosing Kong API Gateway

## Status

**Accepted** — November 2025

---

## Context

UIT-Go's microservices architecture requires an API Gateway to:

1. **Route requests** to appropriate backend services
2. **Authenticate** all incoming requests with JWT validation
3. **Provide** a single entry point for clients (mobile apps, web)
4. **Enable** future capabilities: rate limiting, logging, transformations

### Requirements

| Requirement | Priority | Notes |
|-------------|----------|-------|
| Request routing | Critical | Route by path prefix to services |
| JWT authentication | Critical | RS256 validation, extract claims |
| DB-less configuration | High | Version control, GitOps-friendly |
| Low latency | High | Sub-10ms overhead |
| WebSocket support | Medium | Real-time notifications |
| Rate limiting | Medium | Prevent abuse |
| Plugin ecosystem | Low | Future extensibility |

---

## Decision

We chose **Kong Gateway (Open Source)** in **DB-less mode** with declarative YAML configuration.

---

## Rationale

### 1. Declarative Configuration (GitOps)

Kong's DB-less mode enables:
- All routing rules in version-controlled `kong.yml`
- No database dependency
- Easy CI/CD integration
- Reproducible deployments

```yaml
# kong.yml - Declarative configuration
_format_version: "3.0"

services:
  - name: user-service
    url: http://user-service:3000
    routes:
      - name: user-routes
        paths:
          - /users
          - /sessions
        strip_path: false
```

### 2. Built-in JWT Plugin

Kong's JWT plugin validates RS256 tokens without custom code:

```yaml
plugins:
  - name: jwt
    config:
      claims_to_verify:
        - exp
      header_names:
        - Authorization
      key_claim_name: iss
```

**JWT Flow:**
```
Client → Kong (JWT validation) → Backend Service
         ↓
    Extract: sub, role, iat, exp
         ↓
    Forward: X-User-Id, X-User-Role headers
```

### 3. Service Discovery via Docker Compose

Kong uses Docker Compose's internal DNS for service discovery:

```yaml
services:
  - name: trip-service
    url: http://trip-service:8080  # Docker DNS resolution
  - name: driver-service
    url: http://driver-service:8081
  - name: user-service
    url: http://user-service:3000
```

### 4. Lightweight Footprint

Kong adds minimal overhead:
- Memory: ~50MB base
- Latency: ~1-2ms per request
- CPU: Nginx-based, efficient

### 5. Path-Based Routing

Current routing configuration:

| Path Prefix | Target Service | Auth Required |
|-------------|----------------|---------------|
| `/users`, `/sessions` | user-service:3000 | Partial |
| `/trips` | trip-service:8080 | Yes |
| `/drivers` | driver-service:8081 | Yes |
| `/health` | notification-service:3001 | No |

---

## Trade-offs Accepted

| Trade-off | Impact | Mitigation |
|-----------|--------|------------|
| **No native WebSocket routing** | WebSocket handled by services directly | Notification service exposes port 3001 |
| **Lua-based plugins** | Custom plugins need Lua | Use existing plugins first |

---

## Alternatives Considered

### NGINX
- Simple and fast
- JWT validation requires custom Lua/OpenResty
- No built-in plugin ecosystem

### AWS API Gateway
- Fully managed
- Vendor lock-in
- Higher cost at scale
- Cannot run locally

### Spring Cloud Gateway
- Java-native
- Tightly coupled to Spring ecosystem
- More code to maintain

---

## Consequences

### Positive

- Single entry point for all API traffic
- Centralized JWT authentication
- Declarative, version-controlled configuration
- No database dependency in DB-less mode
- Rich plugin ecosystem for future needs

### Negative

- WebSocket traffic bypasses Kong (handled by notification-service directly)
- Requires monitoring for availability
- Plugin customization requires Lua knowledge

---

## Implementation Details

### Current Kong Configuration

```yaml
# Service Discovery via Docker Compose networking
services:
  - name: user-service
    url: http://user-service:3000
    routes:
      - name: user-routes
        paths:
          - /users
          - /sessions
        strip_path: false

  - name: trip-service
    url: http://trip-service:8080
    routes:
      - name: trip-routes
        paths:
          - /trips
        strip_path: false

  - name: driver-service
    url: http://driver-service:8081
    routes:
      - name: driver-routes
        paths:
          - /drivers
        strip_path: false
```

### JWT Consumer Configuration

```yaml
consumers:
  - username: uit-go-frontend
    jwt_secrets:
      - algorithm: RS256
        key: uit-go-issuer
        rsa_public_key: |
          -----BEGIN PUBLIC KEY-----
          MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
          -----END PUBLIC KEY-----
```

---

## Future Considerations

1. **Rate Limiting**: Add Kong's rate-limiting plugin for API abuse prevention
2. **Request Logging**: Enable logging plugin for observability
3. **Load Balancing**: Configure upstream health checks
4. **Caching**: Add response caching for read-heavy endpoints

---

## References

- [Kong Gateway Documentation](https://docs.konghq.com/)
- [Kong DB-less Mode](https://docs.konghq.com/gateway/latest/production/deployment-topologies/db-less-and-declarative-config/)
- [Kong JWT Plugin](https://docs.konghq.com/hub/kong-inc/jwt/)
