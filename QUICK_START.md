# 🚀 MultiPOS Backend - Quick Start Guide

## Inicio Rápido (5 minutos)

### 1. Clonar y Configurar

```bash
# Clonar el repositorio
git clone <repository-url>
cd multipos-back

# Compilar el proyecto
./gradlew build

# Ejecutar la aplicación
./gradlew run
```

El servidor estará disponible en: `http://localhost:8080`

---

## 📡 Endpoints Principales

### WebSocket: Conexión en Tiempo Real
```
WebSocket GET /ws/{establishmentId}/{posId}
```

**Parámetros:**
- `establishmentId` (String, requerido): ID del establecimiento
- `posId` (String, opcional): ID del punto de venta

**Ejemplo de conexión:**
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/est-123/pos-001');

ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    console.log('Evento recibido:', message);
};
```

---

### REST API: Disparar Eventos

#### 1. Actualización de Inventario
```bash
POST /events/inventory-update
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

**Response:**
```json
{
  "success": true,
  "message": "Inventory update broadcasted",
  "timestamp": 1699564800000
}
```

---

#### 2. Actualización de Cartera
```bash
POST /events/portfolio-update
Content-Type: application/json

{
  "establishmentId": "est-123",
  "portfolioId": "port-789",
  "portfolioName": "Main Portfolio",
  "totalValue": 50000.00,
  "changeType": "UPDATE"
}
```

**Valores válidos para changeType:** `ADD`, `REMOVE`, `UPDATE`

---

#### 3. Estadísticas de Conexiones
```bash
GET /ws/stats
```

**Response:**
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

## 🎯 Flujo de Comunicación

```
┌─────────────────────────────────────┐
│   Cliente 1 (POS)                   │
│   ws:///.../ws/est-123/pos-001      │
└──────────────┬──────────────────────┘
               │ WebSocket conectado
               ▼
        ┌──────────────────┐
        │  Servidor Ktor   │
        │  Puerto 8080     │
        └──────────────────┘
               ▲
               │ Dispara evento REST
               │
   POST /events/inventory-update
               │
               ▼
        ┌──────────────────┐
        │  EventBroadcaster│
        │  ConnectionMgr   │
        └──────────────────┘
               │
               ├──► Broadcast a todos
               │    los POS del
               │    mismo establecimiento
               ▼
┌──────────────────────────────────────┐
│   Cliente 2 (POS)                    │
│   Recibe evento en tiempo real        │
│   ws:///.../ws/est-123/pos-002       │
└──────────────────────────────────────┘
```

---

## 🔄 Flujo de Mensaje WebSocket

### Conexión Exitosa

**1. Cliente conecta:**
```
ws://localhost:8080/ws/est-123/pos-001
```

**2. Servidor envía CONNECTION_ACK:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "CONNECTION_ACK",
  "establishmentId": "est-123",
  "posId": "pos-001",
  "payload": {
    "connectionId": "session-id",
    "message": "Connected successfully to establishment est-123"
  },
  "timestamp": 1699564800000
}
```

### Recepción de Evento

**Cuando otro POS actualiza inventario:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "type": "INVENTORY_UPDATED",
  "establishmentId": "est-123",
  "payload": {
    "productId": "prod-456",
    "productName": "Coca Cola",
    "previousQuantity": 100,
    "newQuantity": 95,
    "changeReason": "SALE"
  },
  "timestamp": 1699564800100
}
```

---

## 🧪 Testing

### Con cURL
```bash
# Disparar evento de inventario
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

# Ver estadísticas
curl http://localhost:8080/ws/stats
```

### Con WebSocket CLI (wscat)
```bash
# Instalar
npm install -g wscat

# Conectar
wscat -c "ws://localhost:8080/ws/est-123/pos-001"

# Esperar eventos...
```

---

## ✅ Verificación de Compilación

```bash
# Compilar el proyecto
./gradlew build

# Compilar solo Kotlin
./gradlew compileKotlin

# Ejecutar tests (si existen)
./gradlew test
```

---

## 📚 Estructura de Carpetas

```
src/main/kotlin/
├── Application.kt              # Punto de entrada
├── plugins/
│   ├── Routing.kt             # Endpoints REST y WebSocket
│   ├── WebSocket.kt           # Plugin de configuración WebSocket
│   └── Serialization.kt       # Configuración de JSON
├── websocket/
│   ├── ConnectionManager.kt   # Gestión de conexiones
│   ├── EventBroadcaster.kt    # Broadcasting de eventos
│   ├── WebSocketHandler.kt    # Handler de conexiones
│   ├── WebSocketSessionManager.kt  # Manager de sesiones
│   ├── WebSocketEvents.kt     # Modelos de eventos
│   ├── WebSocketDtos.kt       # DTOs de API REST
│   └── EventService.kt        # Servicio de eventos
└── database/
    ├── DatabaseSingleton.kt   # Configuración de BD
    └── eventstore/
        ├── EventStoreDao.kt   # DAO para eventos
        └── EventStoreTable.kt # Tabla de eventos (Exposed)
```

---

## 🔒 Seguridad (TODO)

- ⚠️ Autenticación: Implementar JWT o API keys
- ⚠️ Autorización: Validar que el usuario pertenece al establecimiento
- ⚠️ TLS/WSS: Usar conexiones seguras en producción
- ⚠️ Rate limiting: Limitar eventos por usuario/IP

---

## 🐳 Docker

```bash
# Compilar imagen
docker build -t multipos-back .

# Ejecutar contenedor
docker run -p 8080:8080 multipos-back
```

---

## 📞 Soporte

Para problemas, ver:
- `API_REFERENCE.md` - Referencia completa de endpoints
- `ARCHITECTURE.md` - Detalles de arquitectura
- `TROUBLESHOOTING.md` - Solución de problemas

