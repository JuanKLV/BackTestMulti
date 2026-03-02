# 🏗️ Architecture Guide - MultiPOS Backend

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     POS Client Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   POS #1     │  │   POS #2     │  │   POS #3     │          │
│  │ Establishment│  │ Establishment│  │ Establishment│          │
│  │   est-123    │  │   est-123    │  │   est-456    │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                  │                   │
│         │ WebSocket       │ WebSocket        │ WebSocket         │
│         └─────────────────┼──────────────────┘                   │
│                           │                                      │
└───────────────────────────┼──────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Ktor Server (Port 8080)                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  Routing Layer                          │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐   │   │
│  │  │ /ws/{...}    │  │ /events/inv  │  │ /ws/stats   │   │   │
│  │  │ WebSocket    │  │ REST POST    │  │ REST GET    │   │   │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬──────┘   │   │
│  └─────────┼──────────────────┼──────────────────┼─────────┘   │
│            │                  │                  │              │
└────────────┼──────────────────┼──────────────────┼──────────────┘
             │                  │                  │
             ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                  WebSocket Handler Layer                        │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  WebSocketHandler (per connection)                       │  │
│  │  ├─ Register session                                     │  │
│  │  ├─ Send CONNECTION_ACK                                 │  │
│  │  ├─ Process incoming frames                             │  │
│  │  └─ Cleanup on disconnect                               │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
└──────────────┬────────────────────────────────────────────────┬─┘
               │                                                │
               ▼                                                ▼
┌────────────────────────────┐                  ┌────────────────────────────┐
│    Service Layer           │                  │    Session Management      │
├────────────────────────────┤                  ├────────────────────────────┤
│  EventService              │                  │  WebSocketSessionManager   │
│  ├─ broadcastEvent()       │                  │  ├─ register()            │
│  ├─ getStats()            │                  │  ├─ unregister()          │
│  └─ hasActiveConnections()│                  │  ├─ broadcast()           │
│                            │                  │  └─ getStats()            │
│  EventBroadcaster          │                  │                            │
│  ├─ broadcastEvent()       │                  │  Concurrent HashMap        │
│  ├─ sendHeartbeat()        │◄─────────────┐   │  └─ Thread-safe          │
│  └─ getRecentEvents()      │              │   └────────────────────────────┘
└────────────────────────────┘              │
         ▲                                   │
         │ Orchestrates                      │ Uses for
         │ broadcasting                      │ management
         │                                   │
         └───────────────────────────────────┘
                      │
                      ▼
         ┌────────────────────────┐
         │  Database Layer        │
         ├────────────────────────┤
         │  EventStoreDao         │
         │  ├─ insertEvent()      │
         │  ├─ getRecentEvents()  │
         │  └─ purgeOldEvents()   │
         │                        │
         │  EventStoreTable       │
         │  └─ Exposed ORM        │
         └────────────────────────┘
                      │
                      ▼
         ┌────────────────────────┐
         │  Database (PostgreSQL) │
         │  event_store table     │
         └────────────────────────┘
```

---

## 📁 Directory Structure

```
src/main/kotlin/
│
├── Application.kt
│   └─ Entry point, module configuration
│
├── plugins/
│   ├── Routing.kt         (✅ CURRENT FILE)
│   │   ├─ /ws/{...}       WebSocket endpoint
│   │   ├─ POST /events/*  REST event endpoints
│   │   └─ GET /ws/stats   Statistics endpoint
│   │
│   ├── WebSocket.kt
│   │   ├─ configureWebSocket()     Plugin setup
│   │   ├─ WebSocketState           Global singletons
│   │   ├─ startCleanupTask()       Session cleanup
│   │   └─ startHeartbeatTask()     Keep-alive pings
│   │
└── websocket/
    ├── ConnectionManager.kt
    │   └─ Thread-safe session registry
    │       ├─ register()
    │       ├─ unregister()
    │       ├─ broadcastToEstablishment()
    │       └─ sendToSession()
    │
    ├── EventBroadcaster.kt
    │   └─ Event broadcasting orchestration
    │       ├─ broadcastEvent()
    │       ├─ sendHeartbeat()
    │       └─ getRecentEventsForEstablishment()
    │
    ├── WebSocketHandler.kt
    │   └─ Per-connection lifecycle
    │       ├─ register session
    │       ├─ send ACK
    │       ├─ process frames
    │       └─ cleanup
    │
    ├── WebSocketSessionManager.kt
    │   └─ DEPRECATED (superseded by ConnectionManager)
    │
    ├── WebSocketEvents.kt
    │   └─ Message models
    │       ├─ WebSocketMessage
    │       ├─ EventType enum
    │       └─ Payload classes
    │
    ├── WebSocketDtos.kt
    │   └─ Request/Response DTOs
    │
    └── EventService.kt
        └─ DEPRECATED (superseded by EventBroadcaster)

database/
├── DatabaseSingleton.kt
│   └─ Database initialization
│
└── eventstore/
    ├── EventStoreDao.kt
    │   ├─ insertEvent()
    │   ├─ getRecentEvents()
    │   └─ purgeOldEvents()
    │
    └── EventStoreTable.kt
        └─ Exposed table definition
```

---

## 🔄 Data Flow

### 1. WebSocket Connection

```
Client                           Server
  │                               │
  ├─ WebSocket upgrade ──────────►│
  │                               │
  │                      ┌────────┤
  │                      │ Register
  │                      │ in SessionManager
  │                      │
  │◄────── CONNECTION_ACK ────────┤
  │                               │
  ├─ (listens for events) ────────►│
  │                               │
```

### 2. Event Broadcasting

```
REST Client                     Server
  │                               │
  ├─ POST /events/inv-update ────►│
  │                               │
  │                      ┌────────┤
  │                      │ 1. Validate
  │                      │ 2. Create message
  │                      │ 3. Persist to DB
  │                      │ 4. Broadcast
  │                      │
  │◄─── 202 Accepted ─────────────┤
  │                               │
  │                     ┌─────────┤
  │                     │ Send to SessionManager
  │                     │ for all sessions in est-123
  │                     │
  │                     └─────► EventBroadcaster
  │                             ├─ createMessage()
  │                             ├─ serialize()
  │                             ├─ persist()
  │                             └─ broadcast()
  │                                 │
  │                  ┌──────────────┼──────────────┐
  │                  │              │              │
  │                  ▼              ▼              ▼
  │               POS#1          POS#2          POS#3
  │             receives         receives       receives
  │             INVENTORY_      INVENTORY_     INVENTORY_
  │             UPDATED         UPDATED        UPDATED
```

### 3. Message Persistence

```
Event                 EventBroadcaster       EventStoreDao        Database
  │                        │                      │                  │
  ├─ broadcastEvent() ────►│                      │                  │
  │                        │                      │                  │
  │                        ├─ createMessage() ────┼──────────────────┤
  │                        │                      │                  │
  │                        └─ persistEvent() ────►│                  │
  │                                               │                  │
  │                                               ├─ insertEvent() ──►│
  │                                               │                  │
  │                                               │◄─── Acknowledged ─┤
  │                                               │                  │
```

---

## 🔐 Multi-Tenant Isolation

```
Establishment A (est-123)        Establishment B (est-456)
    │                                    │
    ├─ POS#1 ──┐                        ├─ POS#3 ──┐
    │          │                        │          │
    ├─ POS#2 ──┼─► WebSocket Session   ├─ POS#4 ──┼─► WebSocket Session
    │          │   (est-123)           │          │   (est-456)
    │          │                        │          │
    └──────────┘                        └──────────┘
       Events in A                      Events in B
    are NOT broadcast                are NOT broadcast
    to B connections                  to A connections
```

**Isolation Implementation:**
- SessionManager uses `Map<establishmentId, Set<sessions>>`
- broadcast() only sends to matching establishmentId
- Each WebSocket is registered only to its establishment

---

## 🎯 Key Design Patterns

### 1. **Singleton Pattern**
```kotlin
object WebSocketState {
    lateinit var connectionManager: ConnectionManager
    lateinit var eventBroadcaster: EventBroadcaster
    lateinit var eventStoreDao: EventStoreDao
}
```
- Single instance per application
- Initialized at startup
- Used across all requests

### 2. **Manager/DAO Pattern**
```
SessionManager (Manages in-memory sessions)
    │
    └─ broadcast(message) → sends to WebSocket sessions

EventStoreDao (Manages database persistence)
    └─ insertEvent() → saves to database
```

### 3. **Broadcaster Pattern**
```
EventBroadcaster
├─ receive event
├─ persist
├─ broadcast via SessionManager
└─ return result
```

### 4. **Handler Pattern**
```
WebSocketHandler
├─ register connection
├─ send ACK
├─ listen for frames
├─ handle disconnect
└─ cleanup
```

---

## ⚡ Performance Considerations

### Thread Safety
- `ConnectionManager` uses `Mutex` for thread-safe registration
- `ConcurrentHashMap` for session storage
- Snapshot pattern for iteration (`.toList()`)

### Database Operations
- Async with `newSuspendedTransaction(Dispatchers.IO)`
- Non-blocking operations
- Connection pooling (configured in DatabaseSingleton)

### Memory Management
- Automatic session cleanup on disconnect
- Automatic cleanup of empty establishment maps
- Configurable event retention (TODO)

---

## 🚀 Scalability Path

### Current (Single Instance)
```
Ktor Server (1 instance)
├─ In-memory SessionManager
├─ In-memory EventBroadcaster
└─ Database (PostgreSQL)
```

**Suitable for:** < 100 concurrent POS

### Future (Multiple Instances)
```
Load Balancer
├─ Ktor Server #1
├─ Ktor Server #2
└─ Ktor Server #3
    │
    ├─ Redis Pub/Sub (inter-instance messaging)
    ├─ Redis Session Store (shared)
    └─ Database (PostgreSQL)
```

**Steps:**
1. Extract SessionManager to Redis
2. Use Redis Pub/Sub for broadcasts
3. Use Redis sessions for persistence
4. Add sticky sessions at load balancer

---

## 📊 Statistics Endpoint

The `/ws/stats` endpoint provides:
```json
{
  "totalEstablishments": number of unique establishments,
  "totalConnections": sum of all active connections,
  "connectionsPerEstablishment": {
    "est-123": 3,
    "est-456": 2,
    ...
  }
}
```

Used for:
- Monitoring health
- Detecting connection issues
- Capacity planning
- Debugging

---

## 🔗 Component Interactions

```
REST Request (inventory update)
        │
        ▼
   Routing.kt
   handleInventoryUpdate()
        │
        ├─ Validate request
        │
        ├─ Create EventMessage
        │
        ▼
   EventBroadcaster.broadcastEvent()
        │
        ├─ Serialize message
        │
        ├─ Persist to DB
        │     │
        │     ▼
        │  EventStoreDao.insertEvent()
        │     │
        │     ▼
        │  Database
        │
        └─ Broadcast to WebSockets
              │
              ▼
         ConnectionManager.broadcastToEstablishment()
              │
              ├─ Get all sessions for est-123
              │
              └─ Send message to each session
                     │
                     ▼
              WebSocketSession.send(message)
                     │
                     ▼
              Client WebSocket.onmessage
```

---

## 📋 Related Documentation

- [Quick Start](QUICK_START.md)
- [API Reference](API_REFERENCE.md)
- [Troubleshooting](TROUBLESHOOTING.md)

