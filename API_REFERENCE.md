# 📚 API Reference - MultiPOS Backend

## Base URL
```
http://localhost:8080
```

---

## 🔌 WebSocket Endpoint

### Connection
```
GET ws://localhost:8080/ws/{establishmentId}/{posId}
```

### Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `establishmentId` | String | Yes | Identificador único del establecimiento |
| `posId` | String | No | Identificador del punto de venta |

### Connection Flow

#### 1️⃣ Initial Connection
```
Client → Server: WebSocket upgrade request
```

#### 2️⃣ Server Response (CONNECTION_ACK)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "CONNECTION_ACK",
  "establishmentId": "est-123",
  "posId": "pos-001",
  "payload": {
    "connectionId": "unique-session-id",
    "establishmentId": "est-123",
    "posId": "pos-001",
    "serverTimestamp": 1699564800000,
    "message": "Connected successfully to establishment est-123"
  },
  "timestamp": 1699564800000
}
```

#### 3️⃣ Receive Events
All events follow this structure:
```json
{
  "id": "event-uuid",
  "type": "EVENT_TYPE",
  "establishmentId": "est-123",
  "posId": "pos-002",
  "payload": { /* event-specific data */ },
  "timestamp": 1699564800000
}
```

### Message Types

#### CONNECTION_ACK
Sent on successful connection.
```json
{
  "type": "CONNECTION_ACK",
  "payload": {
    "connectionId": "session-id",
    "message": "Connected successfully"
  }
}
```

#### INVENTORY_UPDATED
Sent when inventory changes.
```json
{
  "type": "INVENTORY_UPDATED",
  "payload": {
    "productId": "prod-456",
    "productName": "Coca Cola",
    "previousQuantity": 100,
    "newQuantity": 95,
    "changeReason": "SALE"
  }
}
```

#### PORTFOLIO_UPDATED
Sent when portfolio changes.
```json
{
  "type": "PORTFOLIO_UPDATED",
  "payload": {
    "portfolioId": "port-789",
    "portfolioName": "Main Portfolio",
    "totalValue": 50000.00,
    "changeType": "UPDATE"
  }
}
```

#### HEARTBEAT
Periodic keep-alive message.
```json
{
  "type": "HEARTBEAT",
  "payload": {
    "connectionId": "session-id",
    "status": "ALIVE"
  }
}
```

#### ERROR
Sent when an error occurs.
```json
{
  "type": "ERROR",
  "payload": {
    "errorCode": "HANDLER_ERROR",
    "errorMessage": "Description of error",
    "timestamp": 1699564800000
  }
}
```

### Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| `INVALID_ESTABLISHMENT` | Missing establishmentId | Reconnect with valid ID |
| `HANDLER_ERROR` | Internal server error | Reconnect with exponential backoff |
| `CANNOT_ACCEPT` | Server cannot accept connection | Check server logs |
| `INTERNAL_ERROR` | Registration failed | Retry connection |

---

## 📤 REST Endpoints

### POST /events/inventory-update
Update inventory across all POS in an establishment.

**Request:**
```bash
curl -X POST http://localhost:8080/events/inventory-update \
  -H "Content-Type: application/json" \
  -d '{
    "establishmentId": "est-123",
    "productId": "prod-456",
    "productName": "Coca Cola",
    "previousQuantity": 100,
    "newQuantity": 95,
    "changeReason": "SALE"
  }'
```

**Request Body:**
```json
{
  "establishmentId": "est-123",
  "productId": "prod-456",
  "productName": "Coca Cola",
  "previousQuantity": 100,
  "newQuantity": 95,
  "changeReason": "SALE"
}
```

**Parameters:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `establishmentId` | String | Yes | Establishment ID |
| `productId` | String | Yes | Product ID |
| `productName` | String | Yes | Product name |
| `previousQuantity` | Int | Yes | Previous stock quantity |
| `newQuantity` | Int | Yes | New stock quantity |
| `changeReason` | String | Yes | Reason: SALE, ADJUSTMENT, RETURN, etc. |

**Response (202 Accepted):**
```json
{
  "success": true,
  "message": "Inventory update broadcasted",
  "timestamp": 1699564800000
}
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "error": "establishmentId and productId are required",
  "timestamp": 1699564800000
}
```

**Response (500 Internal Server Error):**
```json
{
  "success": false,
  "error": "Failed to process inventory update",
  "timestamp": 1699564800000
}
```

---

### POST /events/portfolio-update
Update portfolio across all POS in an establishment.

**Request:**
```bash
curl -X POST http://localhost:8080/events/portfolio-update \
  -H "Content-Type: application/json" \
  -d '{
    "establishmentId": "est-123",
    "portfolioId": "port-789",
    "portfolioName": "Main Portfolio",
    "totalValue": 50000.00,
    "changeType": "UPDATE"
  }'
```

**Request Body:**
```json
{
  "establishmentId": "est-123",
  "portfolioId": "port-789",
  "portfolioName": "Main Portfolio",
  "totalValue": 50000.00,
  "changeType": "UPDATE"
}
```

**Parameters:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `establishmentId` | String | Yes | Establishment ID |
| `portfolioId` | String | Yes | Portfolio ID |
| `portfolioName` | String | Yes | Portfolio name |
| `totalValue` | Double | Yes | Total portfolio value |
| `changeType` | String | Yes | ADD, REMOVE, or UPDATE |

**Response (202 Accepted):**
```json
{
  "success": true,
  "message": "Portfolio update broadcasted",
  "timestamp": 1699564800000
}
```

---

### GET /ws/stats
Get real-time WebSocket connection statistics.

**Request:**
```bash
curl http://localhost:8080/ws/stats
```

**Response (200 OK):**
```json
{
  "totalEstablishments": 2,
  "totalConnections": 5,
  "connectionsPerEstablishment": {
    "est-123": 3,
    "est-456": 2
  }
}
```

---

## 🔐 Authentication (TODO)

Currently, no authentication is implemented.

**Planned:**
- JWT tokens
- API key validation
- Establishment ownership verification

---

## 🔄 Event Broadcasting Flow

```
1. REST Client calls POST /events/inventory-update
   ├─ Server receives request
   ├─ Validates parameters
   ├─ Creates WebSocketMessage
   ├─ Persists to EventStore
   ├─ Broadcasts to all WebSocket sessions in establishment
   └─ Returns 202 Accepted

2. All connected POS clients receive INVENTORY_UPDATED
   ├─ Event appears in onmessage handler
   ├─ Update local state
   ├─ Refresh UI
   └─ Optionally confirm with server
```

---

## 📊 Example Scenarios

### Scenario 1: Stock Update
```
1. Manager updates stock in POS-001
   POST /events/inventory-update (Coca Cola: 100 → 95)

2. Server broadcasts to all POS in establishment
   ├─ POS-001 receives update (should update itself)
   ├─ POS-002 receives update (refreshes stock)
   ├─ POS-003 receives update (refreshes stock)
   └─ Event persisted to EventStore

3. New POS connects (POS-004)
   ├─ Receives CONNECTION_ACK
   ├─ Receives last 50 events from last 60 minutes
   └─ Now synchronized with other POS
```

### Scenario 2: Multiple Establishments
```
1. Establishment A (est-123)
   ├─ POS-A1 connected
   ├─ POS-A2 connected
   └─ POST /events/inventory-update → broadcast to A1, A2

2. Establishment B (est-456)
   ├─ POS-B1 connected
   ├─ POS-B2 connected
   └─ Events from A NOT sent to B (isolated)
```

---

## ⚠️ Rate Limiting (TODO)

Not yet implemented. Consider adding:
- Max events per minute
- Max concurrent connections
- Event size limits

---

## 🔗 Related Documentation

- [Quick Start Guide](QUICK_START.md)
- [Architecture Guide](ARCHITECTURE.md)
- [Troubleshooting](TROUBLESHOOTING.md)

