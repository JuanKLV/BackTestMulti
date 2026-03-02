package websocket

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Gestor centralizado de sesiones WebSocket agrupadas por establecimiento
 *
 * Garantiza:
 * - Thread-safety con Mutex y ConcurrentHashMap
 * - Aislamiento multi-tenant (cada establecimiento es independiente)
 * - Limpieza automática de sesiones cerradas
 * - Sin ConcurrentModificationException
 * - Sin race conditions en lifecycle de sesiones
 *
 * Estructura:
 * Map<EstablishmentId, Map<SessionId, WebSocketSessionWrapper>>
 */
class WebSocketSessionManager {
    private val logger = LoggerFactory.getLogger(WebSocketSessionManager::class.java)

    // Map principal: EstablishmentId -> Map de sesiones
    private val sessions = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSessionWrapper>>()

    // Mutex para operaciones de mutación (register, unregister)
    private val registrationMutex = Mutex()

    // Reverse lookup: SessionId -> EstablishmentId (para limpieza rápida, evita N queries)
    private val sessionToEstablishment = ConcurrentHashMap<String, String>()

    /**
     * Registrar una nueva sesión WebSocket en un establecimiento
     *
     * @param establishmentId ID del establecimiento (obligatorio, debe estar validado previamente)
     * @param posId ID del POS (opcional, para auditoría)
     * @param session Sesión WebSocket de Ktor
     * @return SessionId generado
     */
    suspend fun register(
        establishmentId: String,
        posId: String? = null,
        session: WebSocketSession
    ): String {
        val sessionId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        registrationMutex.withLock {
            try {
                // Obtener o crear el set de sesiones para este establecimiento
                val establishmentSessions = sessions.getOrPut(establishmentId) {
                    ConcurrentHashMap()
                }

                // Crear wrapper con metadatos
                val wrapper = WebSocketSessionWrapper(
                    sessionId = sessionId,
                    session = session,
                    posId = posId,
                    establishmentId = establishmentId,
                    connectedAt = timestamp,
                    lastMessageAt = timestamp
                )

                // Registrar sesión
                establishmentSessions[sessionId] = wrapper
                sessionToEstablishment[sessionId] = establishmentId

                logger.info(
                    "WebSocket registered: establishmentId=$establishmentId, " +
                    "sessionId=$sessionId, posId=$posId, " +
                    "totalConnectionsInEstablishment=${establishmentSessions.size}"
                )

                return sessionId

            } catch (e: Exception) {
                logger.error("Error registering session: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Desregistrar una sesión WebSocket
     *
     * GARANTÍAS DE THREAD-SAFETY:
     * - Usa Mutex para prevenir race conditions durante remove
     * - Try-catch alrededor de close() para casos donde sesión ya está cerrada
     * - Limpia bidireccional (sessions map + reverse lookup)
     *
     * @param establishmentId ID del establecimiento
     * @param sessionId ID de la sesión a desregistrar
     * @return true si fue desregistrada, false si no existía
     */
    suspend fun unregister(establishmentId: String, sessionId: String): Boolean {
        registrationMutex.withLock {
            return try {
                val establishmentSessions = sessions[establishmentId] ?: return false

                val removed = establishmentSessions.remove(sessionId)?.let { wrapper ->
                    try {
                        // Intentar cerrar la sesión gracefully
                        // En Ktor 2.x, close() puede lanzar excepción si ya está cerrada
                        // Por eso envolvemos en try-catch
                        wrapper.session.close()
                    } catch (e: Exception) {
                        // La sesión probablemente ya estaba cerrada, esto es normal
                        logger.debug(
                            "Session $sessionId already closed or error closing: ${e.message}"
                        )
                    }

                    // Eliminar del reverse lookup
                    sessionToEstablishment.remove(sessionId)

                    logger.info(
                        "WebSocket unregistered: establishmentId=$establishmentId, " +
                        "sessionId=$sessionId, remainingConnections=${establishmentSessions.size}"
                    )

                    true
                } ?: false

                // Limpiar el map del establecimiento si está vacío
                // Evita memory leak de establishments vacíos
                if (establishmentSessions.isEmpty()) {
                    sessions.remove(establishmentId)
                    logger.debug("Removed empty establishment: $establishmentId")
                }

                removed

            } catch (e: Exception) {
                logger.error("Error unregistering session $sessionId: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Enviar un mensaje a todas las sesiones de un establecimiento
     *
     * GARANTÍAS DE THREAD-SAFETY:
     * - Usa snapshot de sesiones (toList()) para evitar ConcurrentModificationException
     * - Detecta sesiones cerradas mediante try-catch al enviar
     * - Recopila inactivas en lista, luego limpia sin mantener locks
     * - No bloquea el broadcast si una sesión falla
     *
     * @param establishmentId ID del establecimiento
     * @param message Mensaje serializado (JSON)
     * @param excludeSessionId Sesión a excluir del broadcast (ej: quien envió el evento)
     * @return Número de sesiones que recibieron el mensaje exitosamente
     */
    suspend fun broadcast(
        establishmentId: String,
        message: String,
        excludeSessionId: String? = null
    ): Int {
        val establishmentSessions = sessions[establishmentId] ?: return 0

        var successCount = 0
        val inactiveSessions = mutableListOf<String>()

        // Usar snapshot para evitar ConcurrentModificationException
        for ((sessionId, wrapper) in establishmentSessions.toList()) {
            // Saltar sesión excluida (ej: quien originó el evento)
            if (excludeSessionId != null && sessionId == excludeSessionId) {
                continue
            }

            try {
                // Intentar enviar. Si falla, la sesión está probablemente cerrada
                wrapper.session.send(message)
                wrapper.lastMessageAt = System.currentTimeMillis()
                successCount++

                logger.debug(
                    "Message sent to session $sessionId " +
                    "in establishment $establishmentId"
                )

            } catch (e: Exception) {
                // La sesión está cerrada o hay error de envío
                logger.warn(
                    "Error broadcasting to session $sessionId " +
                    "in establishment $establishmentId: ${e.javaClass.simpleName} - ${e.message}"
                )
                inactiveSessions.add(sessionId)
            }
        }

        // Limpiar sesiones inactivas detectadas durante el broadcast
        // Hacerlo aquí (fuera del loop principal) evita modificar el map mientras iteramos
        if (inactiveSessions.isNotEmpty()) {
            logger.debug(
                "Found ${inactiveSessions.size} inactive sessions in $establishmentId, " +
                "cleaning up asynchronously"
            )
            for (sessionId in inactiveSessions) {
                try {
                    unregister(establishmentId, sessionId)
                } catch (e: Exception) {
                    logger.warn("Error cleaning up inactive session $sessionId: ${e.message}")
                }
            }
        }

        logger.info(
            "Broadcast to establishment=$establishmentId: " +
            "sent to $successCount/${establishmentSessions.size} sessions, " +
            "excluded=$excludeSessionId"
        )

        return successCount
    }

    /**
     * Enviar un mensaje a una sesión específica
     *
     * @param establishmentId ID del establecimiento
     * @param sessionId ID de la sesión
     * @param message Mensaje serializado (JSON)
     * @return true si fue enviado exitosamente, false en caso contrario
     */
    suspend fun sendToSession(
        establishmentId: String,
        sessionId: String,
        message: String
    ): Boolean {
        return try {
            val establishmentSessions = sessions[establishmentId] ?: return false
            val wrapper = establishmentSessions[sessionId] ?: return false

            try {
                wrapper.session.send(message)
                wrapper.lastMessageAt = System.currentTimeMillis()

                logger.debug(
                    "Message sent to specific session $sessionId " +
                    "in establishment $establishmentId"
                )

                true

            } catch (e: Exception) {
                // La sesión está cerrada, limpiar
                logger.warn("Session $sessionId is not active or error sending: ${e.message}, cleaning up")
                unregister(establishmentId, sessionId)
                false
            }

        } catch (e: Exception) {
            logger.error(
                "Error sending to session $sessionId " +
                "in establishment $establishmentId: ${e.message}",
                e
            )
            false
        }
    }

    /**
     * Obtener todas las sesiones de un establecimiento
     *
     * @param establishmentId ID del establecimiento
     * @return Lista de wrappers de sesión (snapshot)
     */
    fun getSessionsForEstablishment(establishmentId: String): List<WebSocketSessionWrapper> {
        return sessions[establishmentId]?.values?.toList() ?: emptyList()
    }

    /**
     * Obtener una sesión específica
     *
     * @param establishmentId ID del establecimiento
     * @param sessionId ID de la sesión
     * @return Wrapper de sesión o null si no existe
     */
    fun getSession(
        establishmentId: String,
        sessionId: String
    ): WebSocketSessionWrapper? {
        return sessions[establishmentId]?.get(sessionId)
    }

    /**
     * Obtener el establishmentId de una sesión (reverse lookup)
     *
     * @param sessionId ID de la sesión
     * @return EstablishmentId o null
     */
    fun getEstablishmentForSession(sessionId: String): String? {
        return sessionToEstablishment[sessionId]
    }

    /**
     * Limpiar sesiones inactivas en todos los establecimientos
     *
     * Debe ser llamado periódicamente (ej: cada 60 segundos)
     *
     * @param timeoutMillis Tiempo máximo sin actividad antes de limpiar (default 120s)
     * @return Número de sesiones eliminadas
     */
    suspend fun cleanupInactiveSessions(timeoutMillis: Long = 120_000): Int {
        val now = System.currentTimeMillis()
        val inactiveSessions = mutableListOf<Pair<String, String>>() // (establishmentId, sessionId)

        // Recopilar sesiones inactivas sin locks prolongados
        for ((establishmentId, establishmentSessions) in sessions) {
            for ((sessionId, wrapper) in establishmentSessions) {
                if (now - wrapper.lastMessageAt > timeoutMillis) {
                    inactiveSessions.add(establishmentId to sessionId)
                }
            }
        }

        // Limpiar fuera del loop para evitar ConcurrentModificationException
        var cleanedCount = 0
        for ((establishmentId, sessionId) in inactiveSessions) {
            val unregistered = unregister(establishmentId, sessionId)
            if (unregistered) {
                cleanedCount++
                logger.warn(
                    "Cleaned up inactive session: $sessionId " +
                    "in establishment $establishmentId (timeout: $timeoutMillis ms)"
                )
            }
        }

        if (cleanedCount > 0) {
            logger.info("Cleanup cycle: removed $cleanedCount inactive sessions")
        }

        return cleanedCount
    }

    /**
     * Obtener estadísticas de conexiones
     *
     * @return DTO con métricas
     */
    fun getStats(): WebSocketStats {
        return WebSocketStats(
            totalEstablishments = sessions.size,
            totalConnections = sessions.values.sumOf { it.size },
            connectionsPerEstablishment = sessions.mapValues { (_, establishment) ->
                establishment.size
            }
        )
    }

    /**
     * Verificar si un establecimiento tiene conexiones activas
     *
     * @param establishmentId ID del establecimiento
     * @return true si tiene al menos una sesión
     */
    fun hasActiveConnections(establishmentId: String): Boolean {
        return (sessions[establishmentId]?.size ?: 0) > 0
    }

    /**
     * Obtener el número de conexiones activas en un establecimiento
     *
     * @param establishmentId ID del establecimiento
     * @return Número de sesiones activas
     */
    fun getConnectionCount(establishmentId: String): Int {
        return sessions[establishmentId]?.size ?: 0
    }

    /**
     * Cerrar todas las conexiones de un establecimiento
     * Útil para mantenimiento o al eliminar un establecimiento
     *
     * @param establishmentId ID del establecimiento
     * @return Número de sesiones cerradas
     */
    suspend fun closeAllForEstablishment(establishmentId: String): Int {
        val establishmentSessions = sessions[establishmentId] ?: return 0
        val sessionIds = establishmentSessions.keys.toList()

        var closedCount = 0
        for (sessionId in sessionIds) {
            if (unregister(establishmentId, sessionId)) {
                closedCount++
            }
        }

        logger.info(
            "Closed all connections for establishment $establishmentId: " +
            "closed $closedCount sessions"
        )

        return closedCount
    }
}


/**
 * DTO para estadísticas de sesiones WebSocket
 *
 * @property totalEstablishments Número de establecimientos con conexiones activas
 * @property totalConnections Número total de sesiones activas
 * @property connectionsPerEstablishment Map de establecimiento -> número de conexiones
 */
@Serializable
data class WebSocketStats(
    val totalEstablishments: Int = 0,
    val totalConnections: Int = 0,
    val connectionsPerEstablishment: Map<String, Int> = emptyMap()
)

