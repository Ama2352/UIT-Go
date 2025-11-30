# ADR 006: Choosing WebSocket (Socket.IO) for Real-Time Notifications

## Status

**Accepted** — November 2025

---

## Context

UIT-Go requires real-time communication for:

1. **Trip status updates**: Passenger sees driver assignment, arrival, trip progress
2. **Driver notifications**: New trip requests, passenger cancellations
3. **Location streaming**: Live driver position during trip

### Latency Requirements

| Event | Max Latency | Criticality |
|-------|-------------|-------------|
| Trip assignment | < 500ms | Critical |
| Driver arrival | < 1s | High |
| Location update | < 2s | Medium |
| Payment confirmation | < 1s | High |

### Technical Constraints

- Mobile clients (iOS/Android) with varying network conditions
- Web dashboard for admin monitoring
- Must work behind corporate firewalls/proxies

---

## Decision

We chose **Socket.IO** (WebSocket with fallback) implemented in the **Notification Service** using NestJS.

---

## Rationale

### 1. Bidirectional Real-Time Communication

Socket.IO provides persistent connections for instant updates:

```
┌─────────────┐     WebSocket      ┌─────────────────────┐
│   Client    │◄──────────────────►│ Notification Service │
│  (Mobile)   │     Full-duplex    │     (Socket.IO)     │
└─────────────┘                    └─────────────────────┘
```

### 2. Automatic Fallback

Socket.IO handles degraded network conditions:
1. WebSocket (primary)
2. HTTP Long-Polling (fallback)
3. Automatic reconnection

### 3. User Connection Management

Efficient message routing using a connection map:

```typescript
// userId -> Set<socketId>
private readonly connectedUsers = new Map<string, Set<string>>();

private addUserConnection(userId: string, socketId: string) {
  if (!this.connectedUsers.has(userId)) {
    this.connectedUsers.set(userId, new Set());
  }
  this.connectedUsers.get(userId)?.add(socketId);
}

// Send notification to specific user (all their connected devices)
sendToUser(userId: string, event: string, payload: any) {
  const sockets = this.connectedUsers.get(userId);
  if (sockets) {
    sockets.forEach(socketId => {
      this.server.to(socketId).emit(event, payload);
    });
    return true;
  }
  return false;
}
```

### 4. JWT-Secured Connections

WebSocket connections are authenticated using RS256 JWT:

```typescript
@WebSocketGateway({
  cors: { origin: '*' },
  namespace: 'notifications',
})
export class NotificationGateway
  implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect {
  
  async handleConnection(client: WebSocketClient) {
    try {
      const token = this.extractToken(client);
      if (!token) {
        client.disconnect();
        return;
      }

      const publicKey = readFileSync(this.configService.get('JWT_PUBLIC_KEY_PATH'));
      const payload = await this.jwtService.verifyAsync(token, {
        publicKey,
        algorithms: ['RS256'],
      });

      client.user = {
        userId: payload.sub,
        email: payload.email,
        role: payload.role,
      };

      this.addUserConnection(payload.sub, client.id);
    } catch (error) {
      client.disconnect();
    }
  }

  private extractToken(client: any): string | undefined {
    const authHeader = client.handshake?.headers?.authorization;
    if (authHeader?.split(' ')[0] === 'Bearer') {
      return authHeader.split(' ')[1];
    }
    return client.handshake?.query?.token;
  }
}
```

### 5. Event-Driven Integration with RabbitMQ

Notification Service consumes events from `trip.events` exchange and broadcasts via WebSocket:

```
+-------------+    RabbitMQ     +---------------------+    WebSocket    +---------+
|Trip/Driver  |---------------->| Notification Service|---------------->| Client  |
|  Services   |  trip.events    |   (notification_queue)|  notification  +---------+
+-------------+                 +---------------------+
```

**Consumer Implementation (amqplib):**
```typescript
@Injectable()
export class EventConsumer implements OnModuleInit {
  async onModuleInit() {
    const channel = await this.rabbitMQ.getChannel();
    
    await channel.consume('notification_queue', async (msg) => {
      const content = JSON.parse(msg.content.toString());
      const routingKey = msg.fields.routingKey;

      switch (routingKey) {
        case 'trip.assigned':
          await this.notificationService.notifyTripAssigned(content);
          break;
        case 'trip.started':
          await this.notificationService.notifyTripStarted(content);
          break;
        case 'trip.completed':
          await this.notificationService.notifyTripCompleted(content);
          break;
        case 'trip.cancelled':
          await this.notificationService.notifyTripCancelled(content);
          break;
        case 'trip.offered':
          await this.notificationService.notifyTripOffered(content);
          break;
      }
      channel.ack(msg);
    });
  }
}
```

---

## Trade-offs Accepted

| Trade-off | Impact | Mitigation |
|-----------|--------|------------|
| **Stateful connections** | Harder to scale horizontally | Use Redis adapter for multi-instance |
| **Memory per connection** | ~5KB per client | Acceptable for MVP scale |
| **No guaranteed delivery** | Messages may be lost during reconnection | Client-side retry + REST fallback |
| **Mobile battery drain** | Keep-alive impacts battery | Configurable ping intervals |

---

## Alternatives Considered

### Server-Sent Events (SSE)
- Simpler protocol
- Works through HTTP proxies
- Server-to-client only (no acknowledgments)
- No native room/namespace support

### Firebase Cloud Messaging (FCM)
- Handles mobile push notifications
- Works when app is backgrounded
- Not real-time for in-app updates
- Vendor lock-in

### gRPC Streaming
- Excellent performance
- Type-safe with protobuf
- Limited browser support (requires grpc-web)
- More complex client integration

---

## Consequences

### Positive

- Sub-50ms latency for notifications
- Bidirectional communication
- Multi-device support per user
- JWT-secured connections
- Native NestJS integration

### Negative

- WebSocket traffic bypasses Kong API Gateway
- Requires sticky sessions for multi-instance
- Memory overhead for persistent connections

---

## Architecture Flow

```
+----------------------------------------------------------------+
|                        Client Apps                              |
|              (Mobile / Web - Socket.IO Client)                  |
+-------------------------+--------------------------------------+
                          | WebSocket (ws://host:3001/notifications)
                          v
+----------------------------------------------------------------+
|                   Notification Service                          |
|  +------------------+  +------------------------------------+  |
|  | Socket.IO Server |<-|   EventConsumer (notification_queue)|  |
|  +--------+---------+  +------------------------------------+  |
|           |                           ^                         |
|  +--------v--------+                  |                         |
|  | connectedUsers  |                  |                         |
|  | Map<userId,Set> |                  |                         |
|  +-----------------+                  |                         |
+---------------------------------------+------------------------+
                                        |
        +-------------------------------+---------------+
        |                  RabbitMQ                      |
        |           (trip.events exchange)               |
        +-----------------------------------------------+
                    ^               ^
          +---------+---+   +-------+---------+
          |Trip Service |   | Driver Service  |
          |  publishes: |   |  publishes:     |
          |  requested  |   |  assigned       |
          |  started    |   |  offered        |
          |  completed  |   +-----------------+
          |  cancelled  |
          +-------------+
```

---

## Implementation Details

### Notification Service Implementation

```typescript
@Injectable()
export class NotificationService {
  constructor(private readonly notificationGateway: NotificationGateway) {}

  async notifyTripAssigned(payload: TripAssignedDto) {
    await this.sendNotification(payload.passengerId, {
      userId: payload.passengerId,
      type: 'TRIP_ASSIGNED',
      title: 'Driver Assigned',
      message: `Driver ${payload.driverName} is on the way. ETA: ${payload.etaMinutes} mins.`,
      payload,
      createdAt: new Date(),
    });
  }

  async notifyTripOffered(payload: TripOfferedDto) {
    await this.sendNotification(payload.driverId, {
      userId: payload.driverId,
      type: 'TRIP_OFFERED',
      title: 'New Trip Available',
      message: 'New trip request near you',
      payload,
      createdAt: new Date(),
    });
  }

  private async sendNotification(userId: string, notification: Notification) {
    const sent = this.notificationGateway.sendToUser(userId, 'notification', notification);
    if (!sent) {
      // User offline - could queue for later
    }
  }
}
```

### Notification Events

| Event Name | Direction | Payload |
|------------|-----------|----------|
| `notification` (TRIP_ASSIGNED) | Server -> Client | `{ type, title, message, payload }` |
| `notification` (TRIP_STARTED) | Server -> Client | `{ type, title, message, payload }` |
| `notification` (TRIP_COMPLETED) | Server -> Client | `{ type, title, message, payload }` |
| `notification` (TRIP_CANCELLED) | Server -> Client | `{ type, title, message, payload }` |
| `notification` (TRIP_OFFERED) | Server -> Client | `{ type, title, message, payload }` |

---

## Future Considerations

1. **Redis Adapter**: For horizontal scaling with multiple Notification Service instances
2. **Push Notifications**: Add FCM/APNs for background notifications
3. **Message Queue**: Persist undelivered messages for offline users
4. **Compression**: Enable WebSocket compression for mobile data savings

---

## References

- [Socket.IO Documentation](https://socket.io/docs/v4/)
- [NestJS WebSockets](https://docs.nestjs.com/websockets/gateways)
- [Socket.IO Redis Adapter](https://socket.io/docs/v4/redis-adapter/)
