package websocket

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/**
 * Handler para conexiones WebSocket individuales
 *
 * Responsabilidades:
 * - Registrar/desregistrar sesión
 * - Enviar ACK de conexión
 * - Procesar frames entrantes
 * - Manejar desconexiones graceful
 * - Manejar excepciones
 */
class WebSocketHandler(
    private val sessionManager: WebSocketSessionManager,
    private val json: Json = Json
) {
    private val logger = LoggerFactory.getLogger(WebSocketHandler::class.java)

    /**
     * Manejar una conexión WebSocket entrante
     *
     * @param establishmentId ID del establecimiento (validado antes)
     * @param posId ID del POS (opcional)
     * @param session Sesión WebSocket de Ktor
     */
    suspend fun handle(
        establishmentId: String,
        posId: String?,
        session: WebSocketSession
    ) {
        var sessionId: String? = null

        try {
            // 1. Registrar sesión
            sessionId = sessionManager.register(
                establishmentId = establishmentId,
                posId = posId,
                session = session
            )

            logger.info(
                "WebSocket handler started: establishmentId=$establishmentId, " +
                "sessionId=$sessionId, posId=$posId"
            )

            // 2. Enviar ACK de conexión
            sendConnectionAck(
                session = session,
                establishmentId = establishmentId,
                posId = posId,
                sessionId = sessionId
            )

            // 3. Loop de frames entrantes
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        handleTextFrame(
                            establishmentId = establishmentId,
                            sessionId = sessionId,
                            frameData = frame.readText()
                        )
                    }

                    is Frame.Close -> {
                        logger.info(
                            "WebSocket close frame received: establishmentId=$establishmentId, " +
                            "sessionId=$sessionId"
                        )
                        break
                    }

                    is Frame.Ping -> {
                        logger.debug("Ping received from $sessionId")
                    }

                    is Frame.Pong -> {
                        logger.debug("Pong received from $sessionId")
                    }

                    else -> {
                        logger.warn("Unexpected frame type: ${frame::class.simpleName}")
                    }
                }
            }

        } catch (e: Exception) {
            logger.error(
                "Error in WebSocket handler: establishmentId=$establishmentId, " +
                "sessionId=$sessionId, error=${e.message}",
                e
            )
            sendError(
                session = session,
                establishmentId = establishmentId,
                errorCode = "HANDLER_ERROR",
                errorMessage = "Internal server error: ${e.message}"
            )

        } finally {
            // 4. Desregistrar sesión (cleanup)
            if (sessionId != null) {
                try {
                    sessionManager.unregister(
                        establishmentId = establishmentId,
                        sessionId = sessionId
                    )

                    logger.info(
                        "WebSocket unregistered: establishmentId=$establishmentId, " +
                        "sessionId=$sessionId"
                    )

                } catch (e: Exception) {
                    logger.error(
                        "Error unregistering session $sessionId: ${e.message}",
                        e
                    )
                }
            }
        }
    }

    /**
     * Manejar frame de texto
     */
    private fun handleTextFrame(
        establishmentId: String,
        sessionId: String,
        frameData: String
    ) {
        try {
            logger.debug(
                "Text frame received from session $sessionId in establishment $establishmentId. " +
                "Data length: ${frameData.length}"
            )

        } catch (e: Exception) {
            logger.warn("Error handling text frame from $sessionId: ${e.message}")
        }
    }

    /**
     * Enviar ACK de conexión al cliente
     */
    private suspend fun sendConnectionAck(
        session: WebSocketSession,
        establishmentId: String,
        posId: String?,
        sessionId: String
    ) {
        try {
            val ackPayload = ConnectionAckPayload(
                connectionId = sessionId,
                establishmentId = establishmentId,
                posId = posId,
                message = "Connected successfully to establishment $establishmentId"
            )

            val message = WebSocketMessage(
                id = java.util.UUID.randomUUID().toString(),
                type = EventType.CONNECTION_ACK.name,
                establishmentId = establishmentId,
                posId = posId,
                payload = json.parseToJsonElement(
                    json.encodeToString(ackPayload)
                ),
                timestamp = System.currentTimeMillis()
            )

            val serialized = json.encodeToString(message)
            session.send(serialized)

            logger.info("Connection ACK sent to session $sessionId")

        } catch (e: Exception) {
            logger.error("Error sending connection ACK to $sessionId: ${e.message}", e)
        }
    }

    /**
     * Enviar error al cliente
     */
    private suspend fun sendError(
        session: WebSocketSession,
        establishmentId: String,
        errorCode: String,
        errorMessage: String
    ) {
        try {
            val errorPayload = ErrorPayload(
                errorCode = errorCode,
                errorMessage = errorMessage
            )

            val message = WebSocketMessage(
                id = java.util.UUID.randomUUID().toString(),
                type = EventType.ERROR.name,
                establishmentId = establishmentId,
                payload = json.parseToJsonElement(
                    json.encodeToString(errorPayload)
                ),
                timestamp = System.currentTimeMillis()
            )

            val serialized = json.encodeToString(message)
            session.send(serialized)

        } catch (e: Exception) {
            logger.warn("Error sending error message: ${e.message}")
        }
    }
}

