# 📸 Guía Visual - Cómo Usar Postman con MultiPOS WebSocket

## Resumen Visual Rápido

```
┌─────────────────────────────────────┐
│    1. Inicia el servidor            │
│    ./gradlew run                    │
│    ✅ Esperando en puerto 8080      │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│    2. Abre Postman                  │
│    Crea WebSocket Request           │
│    URL: ws://localhost:8080/...     │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│    3. Click "Connect"               │
│    Espera 1-2 segundos              │
│    Recibirás CONNECTION_ACK         │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│    4. En otra pestaña REST:         │
│    POST evento de inventario        │
│    Verás en WebSocket instantáneo   │
└─────────────────────────────────────┘
```

---

## 🎯 PASO 1: Crear WebSocket Request en Postman

### Dónde Clickear:
```
Postman Top Menu:
┌──────────────┐
│ File Edit ++ │
└──────────────┘
     Click "+"
```

### Seleccionar Tipo:
```
Dropdown (donde dice GET):
┌─────────────────┐
│ GET             │ ← Click aquí
│ POST            │
│ WebSocket       │ ← O selecciona esto
│ ...             │
└─────────────────┘
```

### Resultado:
```
┌─────────────────────────────────────────┐
│ WebSocket                               │
│                                         │
│ ws:// localhost:8080/...  │ Connect    │
│                           │             │
│ Messages                                │
│ ┌─────────────────────────────────────┐ │
│ │                                     │ │
│ │ (Sin mensajes aún)                  │ │
│ │                                     │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

---

## 🔌 PASO 2: Ingresar URL WebSocket

### Campo a Llenar:
```
ws:// localhost:8080/ws/est-123/pos-001
└─┬──┘└───┬────┘    └──┬───┘ └─────┬─────┘
  │       │            │           │
  │       │            │      POS ID (opcional)
  │       │     Establishment ID
  │       │
  │    Host:Port
  │
WebSocket Protocol
```

### Ejemplos Válidos:
```
✅ ws://localhost:8080/ws/est-123/pos-001
✅ ws://localhost:8080/ws/est-A/pos-A
✅ ws://localhost:8080/ws/mystore/mypos
✅ ws://192.168.1.100:8080/ws/est-123/pos-001
```

### Ejemplos Inválidos:
```
❌ ws://localhost:8080/ws//pos-001        (sin establishmentId)
❌ http://localhost:8080/ws/est-123       (HTTP en vez de WS)
❌ ws://localhost:9090/ws/est-123         (puerto incorrecto)
```

---

## ✅ PASO 3: Conectar

### Dónde está el botón:
```
┌──────────────────────────────────────┐
│ ws:// localhost:8080/ws/est-123/pos-001  │
│                          ┌─────────┐ │
│                          │ Connect │ │ ← CLICK AQUÍ
│                          └─────────┘ │
└──────────────────────────────────────┘
```

### Durante la Conexión:
```
Status: Connecting...
⏳ (espera 1-2 segundos)
```

### Conexión Exitosa:
```
Status: Connected ✅
Messages:
┌─────────────────────────────────────┐
│ {                                   │
│   "type": "CONNECTION_ACK",         │
│   "establishmentId": "est-123",     │
│   "posId": "pos-001",               │
│   "payload": {                      │
│     "connectionId": "550e8400...",  │
│     "message": "Connected..."       │
│   }                                 │
│ }                                   │
└─────────────────────────────────────┘
```

### Conexión Fallida:
```
Status: Closed ❌
Razón: Missing establishmentId parameter
```

---

## 🔄 PASO 4: Disparar Evento desde REST

### Abrir Nueva Pestaña:
```
En Postman:
Click "+" → New Request
```

### Configurar para POST:
```
Dropdown: GET → POST
```

### Ingresar URL:
```
http://localhost:8080/events/inventory-update
```

### Ingresar Body (JSON):
```
Pestaña "Body" → Raw → JSON

{
  "establishmentId": "est-123",
  "productId": "prod-001",
  "productName": "Coca Cola",
  "previousQuantity": 100,
  "newQuantity": 95,
  "changeReason": "SALE"
}
```

### Enviar:
```
Click "Send"

Respuesta esperada (202 Accepted):
{
  "success": true,
  "message": "Inventory update broadcasted",
  "timestamp": 1699564800000
}
```

---

## 📨 PASO 5: Ver Evento en WebSocket

### Vuelve a la pestaña WebSocket:
```
Click en la pestaña "Connect Establishment 1 - POS 1"
```

### Verás el evento:
```
Status: Connected ✅

Messages:
┌──────────────────────────────────────┐
│ {                                    │
│   "type": "CONNECTION_ACK",          │
│   ...                                │
│ }                                    │
│                                      │
│ {                                    │ ← NUEVO!
│   "type": "INVENTORY_UPDATED",       │
│   "establishmentId": "est-123",      │
│   "payload": {                       │
│     "productId": "prod-001",         │
│     "productName": "Coca Cola",      │
│     "previousQuantity": 100,         │
│     "newQuantity": 95,               │
│     "changeReason": "SALE"           │
│   }                                  │
│ }                                    │
└──────────────────────────────────────┘
```

---

## 🧪 PRUEBA: Multi-Establecimiento

### Escenario:
```
Est-123 (2 POS) - Reciben eventos
Est-456 (1 POS) - NO recibe eventos
```

### Paso 1: Conecta 3 WebSockets
```
Tab 1: ws://localhost:8080/ws/est-123/pos-001
Tab 2: ws://localhost:8080/ws/est-123/pos-002
Tab 3: ws://localhost:8080/ws/est-456/pos-001

Todas deberían mostrar: Status Connected ✅
```

### Paso 2: Envía evento a est-123
```
POST /events/inventory-update
Body:
{
  "establishmentId": "est-123",
  "productId": "prod-001",
  "productName": "Test",
  "previousQuantity": 100,
  "newQuantity": 99,
  "changeReason": "TEST"
}
```

### Resultado Esperado:
```
Tab 1 (est-123/pos-001): ✅ RECIBE EVENTO
Tab 2 (est-123/pos-002): ✅ RECIBE EVENTO
Tab 3 (est-456/pos-001): ❌ NO RECIBE (correcto - aislamiento)
```

---

## 📊 PASO 6: Verificar Estadísticas

### Nueva Request REST:
```
GET http://localhost:8080/ws/stats
```

### Respuesta:
```json
{
  "totalEstablishments": 2,
  "totalConnections": 3,
  "connectionsPerEstablishment": {
    "est-123": 2,
    "est-456": 1
  }
}
```

---

## 🎬 GIF/VIDEO MENTAL: Flujo Completo

```
┌────────────────────────────────────────────────┐
│                                                │
│  1. Postman WebSocket Tab                      │
│     URL: ws://localhost:8080/ws/est-123/...   │
│     Status: Connecting...                      │
│                                                │
│  2. [CLICK Connect]                           │
│     Status: Connected ✅                       │
│     Message: CONNECTION_ACK                    │
│                                                │
│  3. Postman REST Tab                          │
│     POST /events/inventory-update              │
│     [CLICK Send]                               │
│     Response: 202 Accepted                     │
│                                                │
│  4. Vuelve a WebSocket Tab                     │
│     Nuevo mensaje: INVENTORY_UPDATED ✅       │
│                                                │
│  5. [CLICK Disconnect]                        │
│     Status: Closed                             │
│                                                │
└────────────────────────────────────────────────┘
```

---

## 🐛 Troubleshooting Visual

### Problema 1: "Connection Refused"
```
┌──────────────────────────┐
│ Status: Closed           │
│ Error: Connection refused│
└──────────────────────────┘

Solución:
1. Abre Terminal
2. ./gradlew run
3. Espera a ver: "Responding at http://0.0.0.0:8080"
4. Intenta conectar nuevamente
```

### Problema 2: "Missing establishmentId"
```
┌─────────────────────────────────────┐
│ Status: Closed                       │
│ Error: Missing establishmentId param │
└─────────────────────────────────────┘

URL Incorrecta:
❌ ws://localhost:8080/ws//pos-001

Solución:
✅ ws://localhost:8080/ws/est-123/pos-001
```

### Problema 3: "Timeout"
```
┌──────────────────────────┐
│ Status: Connecting...    │
│ (después de 30 seg)      │
│ Error: Timeout           │
└──────────────────────────┘

Causas:
1. Firewall bloqueando puerto 8080
2. Servidor no responde
3. Red desconectada

Soluciones:
1. Verifica: netstat -an | grep 8080
2. Reinicia servidor
3. Ping localhost
```

---

## ✅ Checklist Visual

```
Setup Inicial:
□ Servidor ejecutándose (terminal con logs)
□ Postman abierto
□ Puerto 8080 libre

Conexión WebSocket:
□ Botón "+" clickeado
□ Tipo "WebSocket Request" seleccionado
□ URL ingresada: ws://localhost:8080/ws/est-123/pos-001
□ Botón "Connect" clickeado
□ Status muestra "Connected" (verde)
□ Mensaje CONNECTION_ACK visible

REST Event:
□ Nueva request creado
□ Tipo "POST" seleccionado
□ URL: http://localhost:8080/events/inventory-update
□ Headers: Content-Type: application/json
□ Body ingresado (JSON válido)
□ Botón "Send" clickeado
□ Response: 202 Accepted

WebSocket Verification:
□ Evento INVENTORY_UPDATED visible en WebSocket tab
□ Payload contiene los datos correctos
□ Timestamp presente
```

---

## 🚀 Flujo de Trabajo Diario

### Morning: Setup
```
1. Terminal: ./gradlew run
2. Postman: File → Open → postman_collection.json
3. Conectar a WebSocket
4. ¡Listo para trabajar!
```

### Midday: Testing
```
1. Cambiar establishmentId en variable
2. Crear nuevas requests según necesidad
3. Usar la colección como template
4. Guardar requests útiles
```

### End of Day: Cleanup
```
1. Disconnect WebSocket
2. Close Postman
3. Stop server (Ctrl+C)
```

---

## 📚 Referencias Visuales

- [Postman Learning Center](https://learning.postman.com/)
- Tu guía: [POSTMAN_GUIDE.md](POSTMAN_GUIDE.md)
- API completa: [API_REFERENCE.md](API_REFERENCE.md)

---

**¿Necesitas ayuda? Revisa POSTMAN_GUIDE.md 🚀**

