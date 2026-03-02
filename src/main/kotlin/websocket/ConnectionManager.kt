package websocket

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestiona todas las conexiones WebSocket agrupadas por establecimiento
 * Garantiza thread-safety y aislamiento multi-tenant
 *
 * Estructura:
 * Map<EstablishmentId, Map<SessionId, WebSocketSessionWrapper>>
 */
class ConnectionManager {
    private val logger = LoggerFactory.getLogger(ConnectionManager::class.java)

    // Map de establecimiento -> (sessionId -> sesión)
    private val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSessionWrapper>>()

    // Mutex para operaciones críticas de registro/desregistro
    private val mutex = Mutex()

    /**
     * Registrar una nueva conexión WebSocket
     * @param establishmentId ID del establecimiento (validar en controlador antes de llamar)
     * @param sessionId ID único de la sesión
     * @param session Sesión WebSocket de Ktor
     * @param posId ID del POS (opcional, para auditoría)
     */
    suspend fun register(
        establishmentId: String,
        sessionId: String,
        session: WebSocketSession,
        posId: String? = null
    ) {
        mutex.withLock {
            val establishmentSessions = connections.getOrPut(establishmentId) {
                ConcurrentHashMap()
            }

            val wrapper = WebSocketSessionWrapper(
                sessionId = sessionId,
                session = session,
                posId = posId,
                establishmentId = establishmentId,
                connectedAt = System.currentTimeMillis()
            )

            establishmentSessions[sessionId] = wrapper

            logger.info(
                "WebSocket registered: establishment=$establishmentId, " +
                "sessionId=$sessionId, posId=$posId, totalConnections=${establishmentSessions.size}"
            )
        }
    }

    /**
     * Desregistrar una conexión
     * Limpia referencias y cierra sesión si está abierta
     */
    suspend fun unregister(establishmentId: String, sessionId: String) {
        mutex.withLock {
            connections[establishmentId]?.let { sessions ->
                sessions.remove(sessionId)?.let { wrapper ->
                    try {
                        wrapper.session.close()
                    } catch (e: Exception) {
                        logger.warn("Error closing session $sessionId: ${e.message}")
                    }

                    logger.info(
                        "WebSocket unregistered: establishment=$establishmentId, " +
                        "sessionId=$sessionId, remainingConnections=${sessions.size}"
                    )
                }

                // Limpiar mapa vacío
                if (sessions.isEmpty()) {
                    connections.remove(establishmentId)
                }
            }
        }
    }

    /**
     * Obtener todas las sesiones de un establecimiento
     * @return Lista de sesiones activas
     */
    suspend fun getSessionsForEstablishment(establishmentId: String): List<WebSocketSessionWrapper> {
        return mutex.withLock {
            connections[establishmentId]?.values?.toList() ?: emptyList()
        }
    }

    /**
     * Obtener sesión específica
     */
    suspend fun getSession(
        establishmentId: String,
        sessionId: String
    ): WebSocketSessionWrapper? {
        return mutex.withLock {
            connections[establishmentId]?.get(sessionId)
        }
    }

    /**
     * Broadcast a todas las sesiones de un establecimiento
     * Excluyendo opcionalmente una sesión (ej: no enviar al emisor)
     */
    suspend fun broadcastToEstablishment(
        establishmentId: String,
        message: String,
        excludeSessionId: String? = null
    ) {
        val sessions = getSessionsForEstablishment(establishmentId)

        logger.debug(
            "Broadcasting to establishment=$establishmentId, " +
            "totalSessions=${sessions.size}, excludeSession=$excludeSessionId"
        )

        for (wrapper in sessions) {
            // Saltar si es la sesión a excluir
            if (excludeSessionId != null && wrapper.sessionId == excludeSessionId) {
                continue
            }

            try {
                wrapper.session.send(message)
                wrapper.lastMessageAt = System.currentTimeMillis()
            } catch (e: Exception) {
                logger.error(
                    "Error broadcasting to session ${wrapper.sessionId}: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Enviar mensaje a una sesión específica
     */
    suspend fun sendToSession(
        establishmentId: String,
        sessionId: String,
        message: String
    ): Boolean {
        return try {
            val wrapper = getSession(establishmentId, sessionId)
            if (wrapper != null) {
                wrapper.session.send(message)
                wrapper.lastMessageAt = System.currentTimeMillis()
                true
            } else {
                logger.warn("Session not found: $sessionId")
                false
            }
        } catch (e: Exception) {
            logger.error("Error sending to session $sessionId: ${e.message}", e)
            false
        }
    }

    /**
     * Obtener estadísticas de conexiones
     * Útil para monitoring y debugging
     */
    suspend fun getStats(): ConnectionStats {
        return mutex.withLock {
            val totalEstablishments = connections.size
            val totalConnections = connections.values.sumOf { it.size }
            val establishmentStats = connections.mapValues { (_, sessions) ->
                sessions.size
            }

            ConnectionStats(
                totalEstablishments = totalEstablishments,
                totalConnections = totalConnections,
                connectionsPerEstablishment = establishmentStats
            )
        }
    }

    /**
     * Limpiar sesiones inactivas
     * Ejecutar periódicamente via corrutina schedulada
     */
    suspend fun cleanupInactiveSessions(timeoutMillis: Long = 60_000) {
        val now = System.currentTimeMillis()
        val inactiveSessions = mutableListOf<Triple<String, String, String?>>()

        mutex.withLock {
            for ((establishmentId, sessions) in connections) {
                for ((sessionId, wrapper) in sessions) {
                    if (now - wrapper.lastMessageAt > timeoutMillis) {
                        inactiveSessions.add(Triple(establishmentId, sessionId, wrapper.posId))
                    }
                }
            }
        }

        // Limpiar fuera del lock para evitar deadlock
        for ((establishmentId, sessionId, posId) in inactiveSessions) {
            logger.warn("Cleaning up inactive session: $sessionId (pos=$posId)")
            unregister(establishmentId, sessionId)
        }
    }
}

/**
 * Wrapper para WebSocketSession con metadatos
 */
data class WebSocketSessionWrapper(
    val sessionId: String,
    val session: WebSocketSession,
    val posId: String?,
    val establishmentId: String,
    val connectedAt: Long,
    var lastMessageAt: Long = System.currentTimeMillis()
)

/**
 * DTO para estadísticas de conexiones
 */
data class ConnectionStats(
    val totalEstablishments: Int,
    val totalConnections: Int,
    val connectionsPerEstablishment: Map<String, Int>
)

