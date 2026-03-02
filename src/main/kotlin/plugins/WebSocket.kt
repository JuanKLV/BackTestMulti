package com.onoff.plugins

import database.eventstore.EventStoreDao
import database.websocket.WebSocketSessionDao
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory
import websocket.ConnectionManager
import websocket.EventBroadcaster
import websocket.EventType
import websocket.WebSocketMessage
import websocket.ConnectionAckPayload
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

// AttributeKeys para almacenar instancias globales
private val connectionManagerKey = AttributeKey<ConnectionManager>("connectionManager")
private val eventBroadcasterKey = AttributeKey<EventBroadcaster>("eventBroadcaster")
private val eventStoreDaoKey = AttributeKey<EventStoreDao>("eventStoreDao")

// Singletons globales (alternativa a attributes si no están disponibles)
object WebSocketState {
    lateinit var connectionManager: ConnectionManager
    lateinit var eventBroadcaster: EventBroadcaster
    lateinit var eventStoreDao: EventStoreDao
    lateinit var webSocketSessionDao: WebSocketSessionDao
}

/**
 * Plugin global para WebSockets
 * Instala el servidor WebSocket y configura:
 * - ConnectionManager (singleton)
 * - EventBroadcaster (singleton)
 * - Cleanup de sesiones inactivas
 * - Heartbeat periódico
 */
fun Application.configureWebSocket() {
    val logger = LoggerFactory.getLogger("WebSocketPlugin")

    // Instanciar singletons
    val connectionManager = ConnectionManager()
    val eventStoreDao = EventStoreDao()
    val webSocketSessionDao = WebSocketSessionDao()
    val eventBroadcaster = EventBroadcaster(connectionManager, eventStoreDao)

    // Guardar en objeto global singleton
    WebSocketState.connectionManager = connectionManager
    WebSocketState.eventStoreDao = eventStoreDao
    WebSocketState.webSocketSessionDao = webSocketSessionDao
    WebSocketState.eventBroadcaster = eventBroadcaster

    // También guardar en atributos de aplicación para acceso desde contexto de Request
    try {
        attributes.put(connectionManagerKey, connectionManager)
        attributes.put(eventBroadcasterKey, eventBroadcaster)
        attributes.put(eventStoreDaoKey, eventStoreDao)
    } catch (e: Exception) {
        logger.warn("Could not store attributes in Application context: ${e.message}")
    }

    // Instalar plugin WebSocket con configuración Ktor 2.x
    install(WebSockets) {
        maxFrameSize = 1024 * 1024  // 1 MB
        masking = false
    }

    // Iniciar corrutina de cleanup de sesiones inactivas
    startCleanupTask(connectionManager, logger)

    // Iniciar corrutina de heartbeat global
    startHeartbeatTask(connectionManager, eventBroadcaster, logger)

    logger.info("WebSocket plugin configured successfully")
}

/**
 * Tarea periódica de cleanup de sesiones inactivas
 */
private fun startCleanupTask(
    connectionManager: ConnectionManager,
    logger: org.slf4j.Logger
) {
    val appScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
    appScope.launch {
        try {
            while (true) {
                delay(60_000)  // Cada 60 segundos
                connectionManager.cleanupInactiveSessions(timeoutMillis = 120_000)
                logger.debug("Cleanup task executed")
            }
        } catch (e: Exception) {
            logger.error("Error in cleanup task: ${e.message}", e)
        }
    }
}

/**
 * Tarea periódica de heartbeat
 */
private fun startHeartbeatTask(
    connectionManager: ConnectionManager,
    eventBroadcaster: EventBroadcaster,
    logger: org.slf4j.Logger
) {
    val appScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
    appScope.launch {
        try {
            while (true) {
                delay(30_000)  // Cada 30 segundos
                val stats = connectionManager.getStats()

                // Enviar heartbeat a cada establecimiento con conexiones activas
                for (establishmentId in stats.connectionsPerEstablishment.keys) {
                    try {
                        eventBroadcaster.sendHeartbeat(establishmentId)
                    } catch (e: Exception) {
                        logger.warn("Error sending heartbeat to $establishmentId: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error in heartbeat task: ${e.message}", e)
        }
    }
}

/**
 * Handler para WebSocket
 * Maneja:
 * 1. Validación de conexión
 * 2. Registro de sesión
 * 3. Envío de eventos históricos
 * 4. Escucha de mensajes entrantes
 * 5. Limpieza al desconectar
 */
@Suppress("SuspendFunctionOnCoroutineScope")
suspend fun DefaultWebSocketServerSession.handleConnection(
    establishmentId: String,
    posId: String?,
    connectionManager: ConnectionManager,
    eventBroadcaster: EventBroadcaster
) {
    val logger = LoggerFactory.getLogger("WebSocketHandler")
    val json = Json
    val sessionId = UUID.randomUUID().toString()

    try {
        // 1. Registrar sesión en ConnectionManager
        connectionManager.register(
            establishmentId = establishmentId,
            sessionId = sessionId,
            session = this,
            posId = posId
        )

        logger.info("WebSocket connected: sessionId=$sessionId, establishment=$establishmentId, posId=$posId")

        // 2. Enviar ACK de conexión
        val ackPayload = ConnectionAckPayload(
            connectionId = sessionId,
            establishmentId = establishmentId,
            posId = posId
        )

        val ackEvent = WebSocketMessage(
            type = EventType.CONNECTION_ACK.name,
            establishmentId = establishmentId,
            posId = posId,
            payload = json.parseToJsonElement(
                json.encodeToString(
                    ConnectionAckPayload.serializer(),
                    ackPayload
                )
            )
        )

        send(json.encodeToString(WebSocketMessage.serializer(), ackEvent))

        // 3. Enviar eventos históricos (última hora)
        val recentEvents = eventBroadcaster.getRecentEventsForEstablishment(
            establishmentId = establishmentId,
            limit = 50,
            minutesBack = 60
        )

        for (event in recentEvents) {
            send(json.encodeToString(WebSocketMessage.serializer(), event))
        }

        logger.info("Sent ${recentEvents.size} historical events to $sessionId")

        // 4. Escuchar mensajes entrantes (keep-alive)
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> {
                    // Procesar mensajes del cliente (ej: ACK de recepción, custom events)
                    logger.debug("Received message from $sessionId")
                }
                is Frame.Close -> {
                    logger.info("Client initiated close for session $sessionId")
                }
                is Frame.Ping, is Frame.Pong -> {
                    // Ktor maneja automáticamente ping/pong
                    logger.debug("Ping/Pong frame for $sessionId")
                }
                else -> {
                    logger.debug("Other frame type received")
                }
            }
        }

    } catch (e: Exception) {
        logger.error("WebSocket error for session $sessionId: ${e.message}", e)
    } finally {
        // 5. Limpiar sesión al desconectar
        try {
            connectionManager.unregister(establishmentId, sessionId)
            logger.info("WebSocket cleaned up: sessionId=$sessionId")
        } catch (e: Exception) {
            logger.error("Error cleaning up session $sessionId: ${e.message}", e)
        }
    }
}
