# 🔌 Cómo Consumir WebSocket desde Postman y Otras Herramientas

## Resumen Rápido

Tu endpoint WebSocket está en:
```
ws://localhost:8080/ws/{establishmentId}/{posId}
```

Donde:
- `establishmentId`: ID único del establecimiento (ej: "est-123")
- `posId`: ID del punto de venta, OPCIONAL (ej: "pos-001")

---

## 1️⃣ POSTMAN (Recomendado)

### Paso 1: Crear Nueva Request WebSocket
1. Abre Postman
2. Click en **"+"** para crear nueva request
3. En el dropdown (donde dice "GET"), selecciona **"WebSocket Request"**

### Paso 2: Ingresar URL
En el campo URL, escribe:
```
ws://localhost:8080/ws/est-123/pos-001
```

Donde:
- `est-123` = tu establishmentId
- `pos-001` = tu posId (opcional, puedes dejarlo vacío)

**Ejemplos válidos:**
```
ws://localhost:8080/ws/est-123/pos-001
ws://localhost:8080/ws/est-123/      (sin posId)
ws://localhost:8080/ws/est-A/pos-A
```

### Paso 3: Conectar
1. Click en el botón **"Connect"**
2. Deberías ver:
   - Status: **"Connected"**
   - Un mensaje de conexión exitosa

### Paso 4: Recibir Mensajes
Espera a recibir el **CONNECTION_ACK**:
```json
{
  "type": "CONNECTION_ACK",
  "establishmentId": "est-123",
  "posId": "pos-001",
  "payload": {
    "connectionId": "550e8400-e29b-41d4-a716-446655440000",
    "establishmentId": "est-123",
    "posId": "pos-001",
    "serverTimestamp": 1699564800000,
    "message": "Connected successfully to establishment est-123"
  },
  "timestamp": 1699564800000
}
```

### Paso 5: Enviar Mensajes (Opcional)
En la sección **"Message"**, puedes enviar:
```json
{
  "type": "HEARTBEAT",
  "payload": {}
}
```

### Paso 6: Desconectar
Click en **"Disconnect"**

---

## 2️⃣ ALTERNATIVAS A POSTMAN

### Option A: Usando `wscat` (CLI)
```bash
# Instalar (si no lo tienes)
npm install -g wscat

# Conectar
wscat -c "ws://localhost:8080/ws/est-123/pos-001"

# Verás algo como:
# Connected (press CTRL+C to quit)
# > {"type":"CONNECTION_ACK", ...}

# Para desconectar
# Presiona: CTRL+C
```

### Option B: Usando JavaScript en Browser
```javascript
// Abre la consola del navegador (F12)
// Pega esto:

const ws = new WebSocket('ws://localhost:8080/ws/est-123/pos-001');

ws.onopen = (event) => {
    console.log('✅ Conectado');
};

ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    console.log('📨 Mensaje recibido:', message);
};

ws.onerror = (error) => {
    console.error('❌ Error:', error);
};

ws.onclose = () => {
    console.log('⚠️ Desconectado');
};

// Para desconectar:
// ws.close();
```

### Option C: Usando Thunder Client (VS Code Extension)
1. Instala extensión "Thunder Client"
2. New Request → WebSocket
3. URL: `ws://localhost:8080/ws/est-123/pos-001`
4. Click Connect

---

## 3️⃣ PRUEBAS COMPLETAS EN POSTMAN

### Escenario 1: Conexión Básica
```
1. Conecta a: ws://localhost:8080/ws/est-123/pos-001
2. Deberías recibir: CONNECTION_ACK
3. Desconecta
```

**Expected:**
```
✅ Status: Connected
✅ Recibe: {"type": "CONNECTION_ACK", ...}
```

### Escenario 2: Múltiples Conexiones
```
1. Abre 2 tabs en Postman
2. Tab 1: ws://localhost:8080/ws/est-123/pos-001
3. Tab 2: ws://localhost:8080/ws/est-123/pos-002

Ambas deberían conectarse exitosamente
```

### Escenario 3: Error - Falta establishmentId
```
Intenta: ws://localhost:8080/ws//pos-001
```

**Expected:**
```
❌ Status: Closed
❌ Razón: Missing establishmentId parameter
```

### Escenario 4: Verificar con Stats REST
```
1. Después de conectar vía WebSocket
2. Abre nueva request REST
3. GET: http://localhost:8080/ws/stats

Expected:
{
  "totalConnections": 1,
  "totalEstablishments": 1,
  "connectionsPerEstablishment": {
    "est-123": 1
  }
}
```

---

## 4️⃣ DISPARAR EVENTOS DESDE POSTMAN (REST)

### Inventario Update
```
POST http://localhost:8080/events/inventory-update
Content-Type: application/json

{
  "establishmentId": "est-123",
  "productId": "prod-456",
  "productName": "Coca Cola",
  "previousQuantity": 100,
  "newQuantity": 95,
  "changeReason": "SALE"
}
```

**Expected Response (202 Accepted):**
```json
{
  "success": true,
  "message": "Inventory update broadcasted",
  "timestamp": 1699564800000
}
```

**Y en tu WebSocket conectado, recibirás:**
```json
{
  "type": "INVENTORY_UPDATED",
  "establishmentId": "est-123",
  "payload": {
    "productId": "prod-456",
    "productName": "Coca Cola",
    "previousQuantity": 100,
    "newQuantity": 95,
    "changeReason": "SALE"
  }
}
```

### Portfolio Update
```
POST http://localhost:8080/events/portfolio-update
Content-Type: application/json

{
  "establishmentId": "est-123",
  "portfolioId": "port-789",
  "portfolioName": "Main Portfolio",
  "totalValue": 50000.00,
  "changeType": "UPDATE"
}
```

---

## 5️⃣ TROUBLESHOOTING EN POSTMAN

### Problema: "Connection Refused"
```
Causa: Servidor no está ejecutándose
Solución:
  1. Terminal: ./gradlew run
  2. Espera: "Responding at http://0.0.0.0:8080"
  3. Intenta conectar nuevamente
```

### Problema: "Missing establishmentId parameter"
```
Causa: Olvidaste pasar establishmentId
❌ ws://localhost:8080/ws//pos-001
✅ ws://localhost:8080/ws/est-123/pos-001
```

### Problema: Conexión se cierra inmediatamente
```
Causa: Exceción al registrar sesión
Solución:
  1. Verifica los logs del servidor
  2. Mira si WebSocketState está inicializado
  3. Prueba con otro establishmentId
```

### Problema: No recibo ningún mensaje
```
Causas posibles:
  1. Conexión no completó handshake
     → Mira el estado en Postman

  2. Servidor no envía CONNECTION_ACK
     → Revisa logs: "WebSocket connected:"

  3. No esperar lo suficiente
     → Algunos sistemas toman 1-2 segundos
```

---

## 6️⃣ FLUJO COMPLETO CON POSTMAN (PASO A PASO)

### Paso 1: Inicia el servidor
```bash
Terminal 1:
$ ./gradlew run

# Espera a ver:
# Application started in 0.303 seconds
# Responding at http://0.0.0.0:8080
```

### Paso 2: Abre Postman
```
Postman → New → WebSocket Request
```

### Paso 3: Conecta al WebSocket
```
URL: ws://localhost:8080/ws/est-123/pos-001
Click: Connect

# Deberías ver en 1-2 segundos:
{
  "type": "CONNECTION_ACK",
  ...
}

# Status: Connected (verde)
```

### Paso 4: Abre otra pestaña para REST
```
New → REST Request
URL: http://localhost:8080/ws/stats

Click: Send
# Respuesta:
{
  "totalConnections": 1,
  "totalEstablishments": 1,
  "connectionsPerEstablishment": {
    "est-123": 1
  }
}
```

### Paso 5: Dispara un evento
```
New → REST Request
POST http://localhost:8080/events/inventory-update

Body:
{
  "establishmentId": "est-123",
  "productId": "prod-001",
  "productName": "Test Product",
  "previousQuantity": 100,
  "newQuantity": 99,
  "changeReason": "SALE"
}

Click: Send
# Response: 202 Accepted
```

### Paso 6: Mira tu WebSocket
```
Vuelve a la pestaña WebSocket
Deberías ver:
{
  "type": "INVENTORY_UPDATED",
  "payload": {
    "productId": "prod-001",
    "productName": "Test Product",
    "previousQuantity": 100,
    "newQuantity": 99,
    "changeReason": "SALE"
  }
}
```

### Paso 7: Desconecta
```
En la pestaña WebSocket
Click: Disconnect
```

---

## 7️⃣ VARIABLES EN POSTMAN (Opcional pero útil)

Para evitar escribir URLs largas, usa variables:

### Setup
1. Postman → Settings → Environments
2. New Environment: "MultiPOS Dev"
3. Agrega variables:
```
WEBSOCKET_BASE_URL = ws://localhost:8080
ESTABLISHMENT_ID = est-123
POS_ID = pos-001
```

### Uso en URL
```
{{WEBSOCKET_BASE_URL}}/ws/{{ESTABLISHMENT_ID}}/{{POS_ID}}
```

---

## 8️⃣ EJEMPLOS POR TIPO DE USUARIO

### Desarrollador Frontend
```javascript
// En tu aplicación web:
const ws = new WebSocket('ws://localhost:8080/ws/est-123/pos-001');

ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);

    if (msg.type === 'INVENTORY_UPDATED') {
        updateInventoryUI(msg.payload);
    } else if (msg.type === 'PORTFOLIO_UPDATED') {
        updatePortfolioUI(msg.payload);
    }
};
```

### Desarrollador Backend
```kotlin
// Usar Postman para testing mientras desarrollas
// Luego escribe tests en Kotlin
```

### QA/Tester
```
Usar Postman para:
1. Verificar conexión WebSocket
2. Verificar reception de eventos
3. Verificar aislamiento multi-tenant
```

---

## 9️⃣ CHECKLIST DE CONEXIÓN

```
✅ Servidor ejecutándose (./gradlew run)
✅ URL correcta (ws://localhost:8080/ws/est-123/pos-001)
✅ establishmentId no está blank
✅ Postman está en modo WebSocket
✅ Click Connect presionado
✅ Esperar 1-2 segundos
✅ Ver mensaje CONNECTION_ACK
✅ Status dice "Connected"
```

Si faltan alguno, revisa Troubleshooting.

---

## 🔟 LINKS ÚTILES

- [Postman WebSocket Documentation](https://learning.postman.com/docs/sending-requests/websocket/overview/)
- [WebSocket Specification (RFC 6455)](https://tools.ietf.org/html/rfc6455)
- [Tu API Reference](API_REFERENCE.md)
- [Tu Architecture Guide](ARCHITECTURE.md)

---

## 📞 REFERENCIA RÁPIDA

| Necesitas | Usa |
|-----------|-----|
| Conectar | Postman WebSocket |
| Ver eventos | Postman WebSocket tab |
| Disparar evento | Postman REST POST |
| Ver estadísticas | Postman REST GET |
| CLI | wscat |
| Browser | JavaScript WebSocket API |
| Testing | cURL o Postman |

---

**Happy Testing! 🚀**

Si tienes problemas, revisa [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

