package websocket

import database.eventstore.EventStoreDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Encapsula la lógica de emitir y distribuir eventos entre POS
 * Combina persistencia en BD (EventStore) con broadcast en tiempo real (WebSocket)
 */
class EventBroadcaster(
    private val connectionManager: ConnectionManager,
    private val eventStoreDao: EventStoreDao
) {
    private val logger = LoggerFactory.getLogger(EventBroadcaster::class.java)
    private val json = Json

    /**
     * Emitir un evento a todos los POS de un establecimiento
     * 1. Persistir en BD (auditoría + recuperación)
     * 2. Broadcast en tiempo real vía WebSocket
     *
     * @param establishmentId ID del establecimiento (REQUERIDO)
     * @param eventType Tipo de evento (SALE_CREATED, INVENTORY_UPDATED, etc.)
     * @param payload Datos del evento (será serializado a JSON)
     * @param excludeSessionId Sesión a excluir del broadcast (ej: quien emitió)
     * @param posId ID del POS que originó el evento (para auditoría)
     */
    suspend fun broadcastEvent(
        establishmentId: String,
        eventType: EventType,
        payload: JsonElement,
        excludeSessionId: String? = null,
        posId: String? = null
    ) {
        try {
            // 1. Crear envelope
            val envelope = WebSocketMessage(
                id = UUID.randomUUID().toString(),
                type = eventType.name,
                establishmentId = establishmentId,
                posId = posId,
                payload = payload,
                timestamp = System.currentTimeMillis()
            )

            // 2. Serializar envelope a JSON
            val serializedEvent = json.encodeToString(
                WebSocketMessage.serializer(),
                envelope
            )

            // 3. Persistir en EventStore (sin bloquear)
            val storeRecord = EventStoreRecord(
                id = envelope.id,
                establishmentId = establishmentId,
                eventType = eventType.name,
                payload = serializedEvent
            )

            // Async insertion - no bloquea broadcast
            try {
                eventStoreDao.insertEvent(storeRecord)
            } catch (e: Exception) {
                logger.error("Failed to persist event ${envelope.id}: ${e.message}", e)
                // Continuar con broadcast aunque falle persistencia
            }

            // 4. Broadcast a todas las sesiones WebSocket del establecimiento
            logger.info(
                "Broadcasting event: establishment=$establishmentId, " +
                "type=${eventType.name}, eventId=${envelope.id}"
            )

            connectionManager.broadcastToEstablishment(
                establishmentId = establishmentId,
                message = serializedEvent,
                excludeSessionId = excludeSessionId
            )

        } catch (e: Exception) {
            logger.error(
                "Error broadcasting event: establishment=$establishmentId, " +
                "type=${eventType.name}, error=${e.message}",
                e
            )
            throw e
        }
    }

    /**
     * Enviar evento a una sesión específica
     * Útil para notificaciones dirigidas
     */
    @Suppress("unused")
    suspend fun sendEventToSession(
        establishmentId: String,
        sessionId: String,
        eventType: EventType,
        payload: JsonElement,
        posId: String? = null
    ): Boolean {
        return try {
            val envelope = WebSocketMessage(
                id = UUID.randomUUID().toString(),
                type = eventType.name,
                establishmentId = establishmentId,
                posId = posId,
                payload = payload,
                timestamp = System.currentTimeMillis()
            )

            val serializedEvent = json.encodeToString(
                WebSocketMessage.serializer(),
                envelope
            )

            // Persistir
            val storeRecord = EventStoreRecord(
                id = envelope.id,
                establishmentId = establishmentId,
                eventType = eventType.name,
                payload = serializedEvent
            )

            try {
                eventStoreDao.insertEvent(storeRecord)
            } catch (e: Exception) {
                logger.warn("Failed to persist event: ${e.message}")
            }

            // Enviar a sesión específica
            connectionManager.sendToSession(
                establishmentId = establishmentId,
                sessionId = sessionId,
                message = serializedEvent
            )
        } catch (e: Exception) {
            logger.error(
                "Error sending event to session: establishment=$establishmentId, " +
                "session=$sessionId, error=${e.message}",
                e
            )
            false
        }
    }

    /**
     * Enviar un heartbeat/ping a todas las sesiones
     * Mantiene conexiones vivas y detecta desconexiones
     */
    suspend fun sendHeartbeat(establishmentId: String) {
        try {
            val heartbeat = WebSocketMessage(
                id = UUID.randomUUID().toString(),
                type = EventType.HEARTBEAT.name,
                establishmentId = establishmentId,
                payload = json.parseToJsonElement(
                    json.encodeToString(
                        HeartbeatPayload.serializer(),
                        HeartbeatPayload(connectionId = UUID.randomUUID().toString())
                    )
                ),
                timestamp = System.currentTimeMillis()
            )

            val serialized = json.encodeToString(
                WebSocketMessage.serializer(),
                heartbeat
            )

            connectionManager.broadcastToEstablishment(
                establishmentId = establishmentId,
                message = serialized
            )
        } catch (e: Exception) {
            logger.warn("Error sending heartbeat to $establishmentId: ${e.message}")
        }
    }

    /**
     * Obtener eventos recientes para un cliente que se reconecta
     * Permite sincronización sin perder eventos durante desconexión
     */
    suspend fun getRecentEventsForEstablishment(
        establishmentId: String,
        limit: Int = 50,
        minutesBack: Int = 60
    ): List<WebSocketMessage> {
        return try {
            val records = eventStoreDao.getRecentEvents(
                establishmentId = establishmentId,
                limit = limit,
                offsetMinutes = minutesBack
            )

            records.mapNotNull { record ->
                try {
                    val jsonElement = json.parseToJsonElement(record.payload)
                    json.decodeFromJsonElement(
                        WebSocketMessage.serializer(),
                        jsonElement
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to deserialize event ${record.id}: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(
                "Error retrieving recent events for $establishmentId: ${e.message}",
                e
            )
            emptyList()
        }
    }
}

