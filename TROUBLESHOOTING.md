# ⚠️ Troubleshooting Guide - MultiPOS Backend

## Connection Issues

### WebSocket Connection Refused
**Symptom:** `WebSocket is closed with code 1006`

**Causes & Solutions:**
```
1. Server not running
   → Run: ./gradlew run
   → Check: lsof -i :8080

2. Invalid establishment ID
   → WebSocket closes if establishmentId is blank
   → Check: establishmentId parameter is not empty

3. Network issue
   → Check firewall allowing port 8080
   → Try: curl http://localhost:8080/ws/stats

4. Server restarted while connected
   → Expected behavior - client must reconnect
   → Implement exponential backoff in client
```

---

## Event Broadcasting Issues

### Events Not Received by Clients
**Symptom:** POST /events/inventory-update returns 202, but WebSocket client sees nothing

**Diagnosis:**
```bash
# 1. Check if there are active connections
curl http://localhost:8080/ws/stats

# Should show:
{
  "totalConnections": > 0,
  "connectionsPerEstablishment": {
    "est-123": N
  }
}

# If totalConnections = 0:
# Problem: No clients connected to that establishment
```

**Solutions:**
```
1. Verify establishmentId matches
   ✓ WebSocket: ws://localhost:8080/ws/est-123/pos-001
   ✓ Event: "establishmentId": "est-123"
   ✗ Won't work: establishment IDs don't match

2. Check client is connected
   → Open another terminal
   → wscat -c "ws://localhost:8080/ws/est-123/pos-001"
   → Should receive CONNECTION_ACK

3. Verify event format
   → POST /events/inventory-update
   → All required fields present?
   → Check API reference for exact format

4. Check server logs
   → Look for: "Broadcasting event:"
   → Look for: "Broadcast to establishment="
```

---

## Memory Issues

### Memory Keeps Growing
**Symptom:** Heap usage increases over time, eventually OOM

**Root Causes:**
```
1. Sessions not being cleaned up
   → Check: unregister() is called on disconnect
   → Look for: "WebSocket cleaned up:"

2. Event store growing unbounded
   → Problem: No event cleanup
   → Solution: Implement purgeOldEvents() task
   → Currently TODO

3. Connection map leak
   → Problem: Empty establishment not removed
   → Check: EventBroadcaster removes empty maps
```

**Investigation:**
```bash
# Check heap size
jps -l | grep "embedded"  # Find process ID
jstat -gc <PID>           # Check GC stats

# If memory growing:
1. Get heap dump
   jmap -dump:live,format=b,file=heap.bin <PID>

2. Analyze with Eclipse MAT
   java -jar mat.jar heap.bin

3. Look for:
   - Large HashMap growth
   - WebSocketSession accumulation
   - EventStore memory
```

**Fix:**
```kotlin
// Implement in WebSocket.kt
private fun startCleanupTask() {
    val appScope = CoroutineScope(Dispatchers.Default)
    appScope.launch {
        while (true) {
            delay(3600_000) // 1 hour
            try {
                // Purge events older than 7 days
                WebSocketState.eventStoreDao.purgeOldEvents(7)
                logger.info("Purged old events")
            } catch (e: Exception) {
                logger.error("Cleanup failed: ${e.message}")
            }
        }
    }
}
```

---

## Database Issues

### Connection Pool Exhausted
**Symptom:** `org.postgresql.util.PSQLException: too many connections`

**Causes:**
```
1. Connections not being returned
   → Exposed transactions not closing
   → Check: Using newSuspendedTransaction correctly

2. Too many concurrent requests
   → Increase pool size in DatabaseSingleton
   → Default: 10 connections, too low for 100+ users

3. Long-running queries
   → Check: getRecentEvents() limit is set
   → Default: limit 50 events
```

**Solution:**
```kotlin
// In DatabaseSingleton.kt
Database.connect(
    url = databaseUrl,
    driver = "org.postgresql.Driver",
    user = dbUser,
    password = dbPassword,
    setupConnection = {
        // Increase pool size
        it.networkTimeout = java.util.concurrent.TimeUnit.SECONDS, 30
    }
)

// Also configure HikariCP
val config = HikariConfig().apply {
    maximumPoolSize = 30  // Increase from default 10
    minimumIdle = 5
    connectionTimeout = 30000
    idleTimeout = 600000
    maxLifetime = 1800000
}
```

---

## Performance Issues

### High Latency on Event Broadcast
**Symptom:** Event takes > 1 second to reach clients

**Investigation:**
```bash
# 1. Measure network latency
ping localhost  # Should be < 1ms

# 2. Check database latency
# Run query in psql:
\timing on
SELECT COUNT(*) FROM event_store;
# Should be < 100ms

# 3. Check CPU usage
top -p <PID>  # CPU should be < 50%

# 4. Check database connections
psql -c "SELECT count(*) FROM pg_stat_activity;"
```

**Common Causes:**
```
1. Slow database insert
   → Add index on (establishmentId, createdAt)
   → Check: eventStoreDao.insertEvent() is async

2. Too many WebSocket sessions to broadcast to
   → Limit connections per establishment?
   → Profile: where is time spent?

3. GC pauses
   → Check: jstat output for full GC times
   → Consider: JVM tuning (-Xmx, -XX:+UseG1GC)

4. Network saturation
   → Check: Network bandwidth usage
   → Check: WebSocket frame size
```

**Optimization:**
```kotlin
// In EventBroadcaster.kt
suspend fun broadcastEvent(...) {
    // Time measurement
    val startTime = System.nanoTime()

    try {
        // ... broadcast code ...
    } finally {
        val duration = (System.nanoTime() - startTime) / 1_000_000
        logger.debug("Broadcast took ${duration}ms")
    }
}
```

---

## WebSocket Protocol Issues

### Connection Closes Unexpectedly
**Symptom:** WebSocket closes after some time (e.g., 30 seconds)

**Causes:**
```
1. Firewall/Proxy timeout (common: 30s, 60s, 300s)
   → Enable heartbeat/keep-alive
   → Current: Already implemented (30s interval)

2. Network timeout on client
   → Client not processing frames
   → Check: onmessage handler not blocking

3. Server-side timeout
   → Check: Ktor timeout settings
   → Default: 15 seconds per frame

4. Reverse proxy timeout
   → If behind nginx/haproxy
   → Set: proxy_read_timeout 3600s
```

**Diagnosis:**
```bash
# 1. Connect and observe
wscat -c "ws://localhost:8080/ws/est-123/pos-001"

# 2. Don't send anything, just wait
# 3. After 30s, should receive HEARTBEAT message
# 4. If connection closes, check server logs

# In logs, look for:
# "Broadcasting HEARTBEAT to est-123"
# "Error in heartbeat task:"
```

**Fix:**
```javascript
// Client-side connection manager
class WebSocketManager {
    constructor(url) {
        this.url = url;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.connect();
    }

    connect() {
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
            this.reconnectAttempts = 0;
            console.log('Connected');
        };

        this.ws.onclose = () => {
            this.attemptReconnect();
        };

        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };
    }

    attemptReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            const delay = Math.pow(2, this.reconnectAttempts) * 1000;
            console.log(`Reconnecting in ${delay}ms...`);
            setTimeout(() => this.connect(), delay);
        }
    }
}
```

---

## Request Validation Errors

### 400 Bad Request on Valid Input
**Symptom:** POST /events/inventory-update returns 400 despite correct data

**Check:**
```bash
# 1. Verify request format
curl -X POST http://localhost:8080/events/inventory-update \
  -H "Content-Type: application/json" \
  -d '{
    "establishmentId": "est-123",
    "productId": "prod-001",
    "productName": "Test",
    "previousQuantity": 100,
    "newQuantity": 95,
    "changeReason": "SALE"
  }' -v

# 2. Check response body
# Response should show which field is missing or invalid

# 3. Common issues:
# - "establishmentId": "" (blank string)
# - "productId": null (null instead of string)
# - Missing required field
```

**Solutions:**
```
1. Check for blank strings
   ✗ "establishmentId": ""
   ✓ "establishmentId": "est-123"

2. Check for null values
   ✗ "changeReason": null
   ✓ "changeReason": "SALE"

3. For portfolio-update, validate changeType
   ✗ "changeType": "INVALID"
   ✓ "changeType": "UPDATE"  (must be ADD, REMOVE, or UPDATE)

4. Check JSON syntax
   ✗ Trailing comma: {"field": "value",}
   ✓ Valid JSON: {"field": "value"}
```

---

## Logging & Monitoring

### Enable Debug Logging
```bash
# Run with environment variable
RUST_LOG=debug ./gradlew run

# Or in application.yaml:
ktor {
  application {
    modules = [ ... ]
  }

  logging {
    level = DEBUG
  }
}
```

### Important Log Messages

**Connection Establishment:**
```
INFO  - WebSocket connection attempt: establishmentId=est-123, posId=pos-001
INFO  - WebSocket connected: sessionId=xxx, establishment=est-123
```

**Event Broadcasting:**
```
INFO  - Broadcasting event: establishment=est-123, type=INVENTORY_UPDATED
INFO  - Broadcast to establishment=est-123: sent to 2/3 sessions
WARN  - Error sending to session xxx
```

**Cleanup:**
```
INFO  - WebSocket cleaned up: sessionId=xxx
INFO  - Cleanup: removed 2 inactive sessions
```

**Errors:**
```
ERROR - Error broadcasting event: establishment=est-123, error=...
ERROR - Error in cleanup task: ...
ERROR - Error in heartbeat task: ...
```

---

## Health Checks

### Quick Health Check Script
```bash
#!/bin/bash
# health_check.sh

echo "🔍 MultiPOS Backend Health Check"
echo "=================================="

# 1. Server running
echo -n "1. Server responding: "
if curl -s http://localhost:8080/ws/stats > /dev/null; then
    echo "✅"
else
    echo "❌ Server not responding"
    exit 1
fi

# 2. Stats endpoint
echo -n "2. Stats endpoint: "
if curl -s http://localhost:8080/ws/stats | jq . > /dev/null 2>&1; then
    echo "✅"
else
    echo "❌ Stats endpoint returning invalid JSON"
    exit 1
fi

# 3. Database connection
echo -n "3. Database: "
# This requires implementing a /health endpoint
# For now, we'll check if events table exists (psql required)
if psql -c "SELECT 1 FROM event_store LIMIT 1;" 2>/dev/null; then
    echo "✅"
else
    echo "⚠️ Could not verify database"
fi

echo ""
echo "Health check complete!"
```

---

## Contact & Support

### Getting Help
1. Check this troubleshooting guide first
2. Review logs in server console
3. Run health check script
4. Check API Reference for endpoint details
5. Open issue on GitHub with:
   - What you were trying to do
   - Error message (full stack trace)
   - Steps to reproduce
   - Server logs

### Files to Include in Bug Reports
- Server logs (stdout/stderr)
- Request/response examples
- Health check output
- Network traces (if possible)

---

## Related Documentation

- [Quick Start](QUICK_START.md)
- [API Reference](API_REFERENCE.md)
- [Architecture](ARCHITECTURE.md)
- [Testing](TESTING.md)

