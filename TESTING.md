# 🧪 Testing Guide - MultiPOS Backend

## Quick Tests

### 1. Health Check
```bash
# Check if server is running
curl http://localhost:8080/ws/stats

# Expected response (200 OK):
{
  "totalEstablishments": 0,
  "totalConnections": 0,
  "connectionsPerEstablishment": {}
}
```

### 2. WebSocket Connection Test
```bash
# Install wscat globally (if not already installed)
npm install -g wscat

# Connect to WebSocket
wscat -c "ws://localhost:8080/ws/est-123/pos-001"

# You should receive CONNECTION_ACK immediately:
{
  "type": "CONNECTION_ACK",
  "establishmentId": "est-123",
  ...
}

# Keep the connection open in this terminal
```

### 3. Inventory Update (In another terminal)
```bash
curl -X POST http://localhost:8080/events/inventory-update \
  -H "Content-Type: application/json" \
  -d '{
    "establishmentId": "est-123",
    "productId": "prod-001",
    "productName": "Coca Cola",
    "previousQuantity": 100,
    "newQuantity": 95,
    "changeReason": "SALE"
  }'

# Expected response (202 Accepted):
{
  "success": true,
  "message": "Inventory update broadcasted",
  "timestamp": 1699564800000
}

# In the wscat terminal, you should see:
{
  "type": "INVENTORY_UPDATED",
  "establishmentId": "est-123",
  "payload": {
    "productId": "prod-001",
    "productName": "Coca Cola",
    "previousQuantity": 100,
    "newQuantity": 95,
    "changeReason": "SALE"
  }
}
```

---

## Multi-Client Test

### Terminal 1: Connect POS#1
```bash
wscat -c "ws://localhost:8080/ws/est-123/pos-001"
# Receives CONNECTION_ACK
```

### Terminal 2: Connect POS#2
```bash
wscat -c "ws://localhost:8080/ws/est-123/pos-002"
# Receives CONNECTION_ACK
```

### Terminal 3: Check Stats
```bash
curl http://localhost:8080/ws/stats

# Expected:
{
  "totalEstablishments": 1,
  "totalConnections": 2,
  "connectionsPerEstablishment": {
    "est-123": 2
  }
}
```

### Terminal 4: Dispatch Event
```bash
curl -X POST http://localhost:8080/events/inventory-update \
  -H "Content-Type: application/json" \
  -d '{
    "establishmentId": "est-123",
    "productId": "prod-001",
    "productName": "Laptop",
    "previousQuantity": 10,
    "newQuantity": 9,
    "changeReason": "SALE"
  }'
```

**Expected in Terminal 1 & 2:**
Both should receive the INVENTORY_UPDATED event simultaneously.

---

## Multi-Establishment Isolation Test

### Terminal 1: Connect to Establishment A
```bash
wscat -c "ws://localhost:8080/ws/est-A/pos-001"
```

### Terminal 2: Connect to Establishment B
```bash
wscat -c "ws://localhost:8080/ws/est-B/pos-001"
```

### Terminal 3: Dispatch Event to Est-A
```bash
curl -X POST http://localhost:8080/events/inventory-update \
  -H "Content-Type: application/json" \
  -d '{
    "establishmentId": "est-A",
    "productId": "prod-001",
    "productName": "Test Product",
    "previousQuantity": 100,
    "newQuantity": 99,
    "changeReason": "SALE"
  }'
```

**Expected:**
- Terminal 1 (Est-A): Receives INVENTORY_UPDATED ✅
- Terminal 2 (Est-B): Does NOT receive it ✅ (Isolation working)

---

## Error Scenarios

### Missing establishmentId
```bash
curl -X POST http://localhost:8080/events/inventory-update \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "prod-001"
  }'

# Expected (400 Bad Request):
{
  "success": false,
  "error": "establishmentId and productId are required"
}
```

### Invalid changeType
```bash
curl -X POST http://localhost:8080/events/portfolio-update \
  -H "Content-Type: application/json" \
  -d '{
    "establishmentId": "est-123",
    "portfolioId": "port-001",
    "portfolioName": "Test",
    "totalValue": 1000,
    "changeType": "INVALID"
  }'

# Expected (400 Bad Request):
{
  "success": false,
  "error": "changeType must be one of: ADD, REMOVE, UPDATE"
}
```

### Server Error
```bash
# Kill the server or disconnect database
curl http://localhost:8080/ws/stats

# Expected (5xx error):
Connection refused or 500 Internal Server Error
```

---

## Load Testing

### Simple Load Test (100 concurrent connections)
```bash
#!/bin/bash
# test_load.sh

for i in {1..100}; do
  (
    wscat -c "ws://localhost:8080/ws/est-123/pos-$i" &
  ) &
done

wait

# Then in another terminal:
curl http://localhost:8080/ws/stats
# Should show: totalConnections: 100
```

### Stress Test (send 1000 events)
```bash
#!/bin/bash
# test_stress.sh

for i in {1..1000}; do
  curl -X POST http://localhost:8080/events/inventory-update \
    -H "Content-Type: application/json" \
    -d "{
      \"establishmentId\": \"est-123\",
      \"productId\": \"prod-$((i % 100))\",
      \"productName\": \"Product $i\",
      \"previousQuantity\": 100,
      \"newQuantity\": $((99 - (i % 100))),
      \"changeReason\": \"LOAD_TEST\"
    }" &
done

wait
```

---

## Integration Testing

### JavaScript Test Client
```javascript
// test_client.js
const ws = new WebSocket('ws://localhost:8080/ws/est-123/pos-001');

ws.onopen = () => {
  console.log('✅ Connected');
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('📨 Received:', message.type);

  if (message.type === 'INVENTORY_UPDATED') {
    console.log(`  Product: ${message.payload.productName}`);
    console.log(`  Qty: ${message.payload.previousQuantity} → ${message.payload.newQuantity}`);
  }
};

ws.onerror = (error) => {
  console.error('❌ Error:', error);
};

ws.onclose = () => {
  console.log('⚠️ Disconnected');
};
```

Run with:
```bash
node test_client.js
```

---

## Checklist

### Functionality Tests
- [ ] WebSocket connection successful
- [ ] CONNECTION_ACK received on connect
- [ ] Inventory update broadcasts correctly
- [ ] Portfolio update broadcasts correctly
- [ ] Stats endpoint returns correct data
- [ ] Multiple POS receive same event
- [ ] Establishments are isolated
- [ ] Errors return correct HTTP status codes

### Performance Tests
- [ ] Supports 100+ concurrent connections
- [ ] Broadcast latency < 100ms
- [ ] No memory leaks after 1000 events
- [ ] CPU usage stable under load

### Error Handling Tests
- [ ] Missing parameters return 400
- [ ] Invalid changeType returns 400
- [ ] Server errors return 500
- [ ] Graceful disconnect handling
- [ ] Reconnection works

### Security Tests (TODO)
- [ ] Authentication required
- [ ] Authorization validated
- [ ] TLS/WSS enforced
- [ ] Rate limiting working

---

## Debugging

### Enable Server Logs
```bash
# Run with debug logging
RUST_LOG=debug ./gradlew run
```

### Check Active Connections
```bash
curl http://localhost:8080/ws/stats | jq .

# Pretty-printed JSON output
```

### WebSocket Frame Inspection
```bash
# Using Chrome DevTools
1. Open DevSockets extension
2. Connect to ws://localhost:8080/ws/est-123/pos-001
3. Inspect frames in real-time
```

### Database Queries
```sql
-- Check event store contents
SELECT id, eventType, createdAt FROM event_store
WHERE establishmentId = 'est-123'
ORDER BY createdAt DESC
LIMIT 10;
```

---

## Common Issues

### Connection Refused
**Problem:** `curl: (7) Failed to connect`
**Solution:**
1. Check server is running: `ps aux | grep java`
2. Check port 8080 is open: `lsof -i :8080`
3. Restart server: `./gradlew run`

### No Events Received
**Problem:** Send event but WebSocket doesn't receive it
**Solution:**
1. Check establishment ID matches
2. Verify stats endpoint shows connection
3. Check server logs for errors
4. Verify event was sent with correct format

### Memory Growing
**Problem:** Memory usage increases over time
**Solution:**
1. Check ConnectionManager cleanup is working
2. Verify SessionManager.unregister() is called
3. Monitor with: `jps -lm` and `jstat -gc <pid>`

### Database Connection Errors
**Problem:** `org.postgresql.util.PSQLException`
**Solution:**
1. Check PostgreSQL is running
2. Check connection string in DatabaseSingleton
3. Check database exists
4. Check credentials

---

## Performance Baselines

Expected metrics (single instance, 8 CPU cores):

| Metric | Target | Acceptable |
|--------|--------|-----------|
| Connections | 100+ | 50+ |
| Broadcast latency | < 50ms | < 100ms |
| Memory (idle) | 200MB | 300MB |
| Memory (100 conns) | 250MB | 350MB |
| CPU (idle) | < 1% | < 5% |
| CPU (100 events/s) | < 20% | < 50% |

---

## Related Documentation

- [Quick Start](QUICK_START.md)
- [API Reference](API_REFERENCE.md)
- [Architecture](ARCHITECTURE.md)
- [Troubleshooting](TROUBLESHOOTING.md)

