package com.onoff.plugins

import com.onoff.plugins.sale.SaleRepository
import database.products.ProductsModel
import database.products.ProductsTable
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import websocket.InventoryUpdateRequest
import websocket.PortfolioUpdateRequest
import websocket.EventResponse
import websocket.ErrorResponse
import websocket.ConnectionManager
import websocket.EventBroadcaster
import websocket.EventType
import websocket.InventoryUpdatedPayload
import websocket.PortfolioUpdatedPayload
import websocket.WebSocketMessage
import websocket.ConnectionAckPayload
import io.ktor.websocket.Frame
import java.util.UUID
import database.websocket.WebSocketSessionDao
import database.websocket.WebSocketSessionRecord
import mappers.response.SaleDto

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")
    val json = Json
    val repository = SaleRepository()

    routing {
        // ================== WEBSOCKET ENDPOINT ==================
        webSocket("/ws/{establishmentId}/{posId}") {
            val establishmentId = call.parameters["establishmentId"]
            val posId = call.parameters["posId"]
            lateinit var sessionId: String

            // Validar parámetros
            if (establishmentId.isNullOrBlank()) {
                logger.warn("WebSocket connection rejected: missing establishmentId")
                this.close(CloseReason(
                    CloseReason.Codes.CANNOT_ACCEPT,
                    "Missing establishmentId parameter"
                ))
                return@webSocket
            }

            logger.info(
                "WebSocket connection attempt: establishmentId=$establishmentId, posId=$posId"
            )

            // Registrar sesión
            try {
                sessionId = UUID.randomUUID().toString()
                WebSocketState.connectionManager.register(
                    establishmentId = establishmentId,
                    sessionId = sessionId,
                    session = this,
                    posId = posId
                )

                // Guardar sesión en BD
                WebSocketState.webSocketSessionDao.saveSession(
                    WebSocketSessionRecord(
                        id = sessionId,
                        establishmentId = establishmentId,
                        posId = posId,
                        connectedAt = System.currentTimeMillis(),
                        isActive = true
                    )
                )

                logger.info("WebSocket registered: sessionId=$sessionId, establishment=$establishmentId, posId=$posId")
            } catch (e: Exception) {
                logger.error("Error registering WebSocket session: ${e.message}", e)
                this.close(CloseReason(
                    CloseReason.Codes.INTERNAL_ERROR,
                    "Failed to register connection"
                ))
                return@webSocket
            }

            try {
                // Enviar CONNECTION_ACK
                try {
                    val ackPayload = ConnectionAckPayload(
                        connectionId = sessionId,
                        establishmentId = establishmentId,
                        posId = posId
                    )

                    val ackMessage = WebSocketMessage(
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

                    this.send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(), ackMessage)))
                    logger.debug("CONNECTION_ACK sent to sessionId=$sessionId")
                } catch (e: Exception) {
                    logger.error("Error sending CONNECTION_ACK: ${e.message}", e)
                }

                // Escuchar mensajes entrantes
                @Suppress("UNUSED_VARIABLE")
                for (_frame in incoming) {
                    logger.debug("Received frame from $sessionId")
                    // Aquí puedes procesar mensajes del cliente si lo necesitas
                }

            } catch (e: Exception) {
                logger.error("WebSocket error for session $sessionId: ${e.message}", e)
            } finally {
                // Limpiar al desconectar
                try {
                    WebSocketState.connectionManager.unregister(establishmentId, sessionId)

                    // Actualizar sesión como desconectada en BD
                    WebSocketState.webSocketSessionDao.updateSessionDisconnected(
                        sessionId = sessionId,
                        disconnectedAt = System.currentTimeMillis()
                    )

                    logger.info("WebSocket unregistered: sessionId=$sessionId, establishment=$establishmentId")
                } catch (e: Exception) {
                    logger.error("Error unregistering session $sessionId: ${e.message}", e)
                }
            }

            // TODO: Validar que establishmentId existe en BD
            // TODO: Validar autenticación (JWT, API key, etc.)
        }

        // ================== INVENTORY UPDATE ENDPOINT ==================
        post("/events/inventory-update") {
            handleInventoryUpdate(call, eventBroadcaster = WebSocketState.eventBroadcaster, logger, json)
        }

        // ================== PORTFOLIO UPDATE ENDPOINT ==================
        post("/events/portfolio-update") {
            handlePortfolioUpdate(call, eventBroadcaster = WebSocketState.eventBroadcaster, logger, json)
        }

        // ================== MONITORING ENDPOINT ==================
        get("/ws/stats") {
            handleGetStats(call, connectionManager = WebSocketState.connectionManager, logger)
        }

        // ================== WEBSOCKET SESSIONS ENDPOINT ==================
        get("/ws/sessions") {
            handleGetSessions(call, webSocketSessionDao = WebSocketState.webSocketSessionDao, logger)
        }

        get("/ws/sessions/{establishmentId}") {
            val establishmentId = call.parameters["establishmentId"]
            if (establishmentId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "establishmentId parameter is required")
                )
                return@get
            }
            handleGetSessionsByEstablishment(call, establishmentId, webSocketSessionDao = WebSocketState.webSocketSessionDao, logger)
        }

        post("/sales") {
            val sales = call.receive<SaleDto>()
            val response = repository.updateStockProduct(sales)
            call.respond(response)
        }

        post("/product") {
            val products = call.receive<ProductsModel>()
            val response = repository.saveProduct(products)
            call.respond(response)
        }

        // ================== SYNC CONFIRM ENDPOINT ==================
        post("/sync/confirm") {
            handleSyncConfirm(call, logger)
        }
    }
}

/**
 * Handler para POST /events/inventory-update
 * Refactorizado para reducir complejidad cognitiva
 */
private suspend fun handleInventoryUpdate(
    call: ApplicationCall,
    eventBroadcaster: EventBroadcaster,
    logger: Logger,
    json: Json
) {
    try {
        val request = call.receive<InventoryUpdateRequest>()

        // Validar request
        if (request.establishmentId.isBlank() || request.productId.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "establishmentId and productId are required")
            )
            return
        }

        logger.info(
            "Inventory update: establishment=${request.establishmentId}, " +
            "product=${request.productId}, qty=${request.previousQuantity}->${request.newQuantity}"
        )

        // Broadcast del evento
        eventBroadcaster.broadcastEvent(
            establishmentId = request.establishmentId,
            eventType = EventType.INVENTORY_UPDATED,
            payload = json.parseToJsonElement(
                json.encodeToString(
                    InventoryUpdatedPayload.serializer(),
                    InventoryUpdatedPayload(
                        productId = request.productId,
                        productName = request.productName,
                        previousQuantity = request.previousQuantity,
                        newQuantity = request.newQuantity,
                        changeReason = request.changeReason
                    )
                )
            )
        )

        call.respond(
            HttpStatusCode.Accepted,
            EventResponse(
                success = true,
                message = "Inventory update broadcasted"
            )
        )

    } catch (e: Exception) {
        logger.error("Error in inventory-update endpoint: ${e.message}", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse(error = "Failed to process inventory update")
        )
    }
}

/**
 * Handler para POST /events/portfolio-update
 * Refactorizado para reducir complejidad cognitiva
 */
private suspend fun handlePortfolioUpdate(
    call: ApplicationCall,
    eventBroadcaster: EventBroadcaster,
    logger: Logger,
    json: Json
) {
    try {
        val request = call.receive<PortfolioUpdateRequest>()

        // Validar request
        if (request.establishmentId.isBlank() || request.portfolioId.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "establishmentId and portfolioId are required")
            )
            return
        }

        // Validar changeType
        val validChangeTypes = setOf("ADD", "REMOVE", "UPDATE")
        if (request.changeType !in validChangeTypes) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "changeType must be one of: ADD, REMOVE, UPDATE")
            )
            return
        }

        logger.info(
            "Portfolio update: establishment=${request.establishmentId}, " +
            "portfolio=${request.portfolioId}, changeType=${request.changeType}"
        )

        // Broadcast del evento
        eventBroadcaster.broadcastEvent(
            establishmentId = request.establishmentId,
            eventType = EventType.PORTFOLIO_UPDATED,
            payload = json.parseToJsonElement(
                json.encodeToString(
                    PortfolioUpdatedPayload.serializer(),
                    PortfolioUpdatedPayload(
                        portfolioId = request.portfolioId,
                        portfolioName = request.portfolioName,
                        totalValue = request.totalValue,
                        changeType = request.changeType
                    )
                )
            )
        )

        call.respond(
            HttpStatusCode.Accepted,
            EventResponse(
                success = true,
                message = "Portfolio update broadcasted"
            )
        )

    } catch (e: Exception) {
        logger.error("Error in portfolio-update endpoint: ${e.message}", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse(error = "Failed to process portfolio update")
        )
    }
}

/**
 * Handler para GET /ws/stats
 * Refactorizado para reducir complejidad cognitiva
 */
private suspend fun handleGetStats(
    call: ApplicationCall,
    connectionManager: ConnectionManager,
    logger: Logger
) {
    try {
        val stats = connectionManager.getStats()
        call.respond(HttpStatusCode.OK, stats)

    } catch (e: Exception) {
        logger.error("Error getting WebSocket stats: ${e.message}", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse(error = "Failed to get WebSocket stats")
        )
    }
}

/**
 * Handler para GET /ws/sessions
 * Retorna todas las sesiones WebSocket activas
 */
private suspend fun handleGetSessions(
    call: ApplicationCall,
    webSocketSessionDao: database.websocket.WebSocketSessionDao,
    logger: Logger
) {
    try {
        val sessions = webSocketSessionDao.getAllActiveSessions()
        call.respond(HttpStatusCode.OK, sessions)
    } catch (e: Exception) {
        logger.error("Error getting WebSocket sessions: ${e.message}", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse(error = "Failed to get WebSocket sessions")
        )
    }
}

/**
 * Handler para GET /ws/sessions/{establishmentId}
 * Retorna sesiones WebSocket activas por establecimiento
 */
private suspend fun handleGetSessionsByEstablishment(
    call: ApplicationCall,
    establishmentId: String,
    webSocketSessionDao: database.websocket.WebSocketSessionDao,
    logger: Logger
) {
    try {
        val sessions = webSocketSessionDao.getActiveSessions(establishmentId)
        call.respond(HttpStatusCode.OK, sessions)
    } catch (e: Exception) {
        logger.error("Error getting WebSocket sessions for establishment: ${e.message}", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse(error = "Failed to get WebSocket sessions")
        )
    }
}

/**
 * Handler para POST /sync/confirm
 * Marca un registro EstablishmentPivot como sincronizado (isSync = 1)
 */
private suspend fun handleSyncConfirm(
    call: ApplicationCall,
    logger: Logger
) {
    try {
        val request = call.receive<websocket.SyncConfirmRequest>()

        // Validar campos requeridos
        if (request.productId.isBlank() || request.establishmentId.isBlank() || request.pointOfSaleId.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                websocket.SyncConfirmResponse(
                    success = false,
                    message = "productId, establishmentId y pointOfSaleId son requeridos"
                )
            )
            return
        }

        logger.info(
            "Sync confirm request: establishment=${request.establishmentId}, " +
            "pointOfSale=${request.pointOfSaleId}, product=${request.productId}"
        )

        // Ejecutar actualización en BD
        val dao = database.establishmentPivot.EstablishmentPivotDao()
        val updated = dao.confirmSync(
            establishmentId = request.establishmentId,
            pointOfSaleId = request.pointOfSaleId,
            productId = request.productId
        )

        if (updated) {
            logger.info(
                "Sync confirmed: establishment=${request.establishmentId}, " +
                "pointOfSale=${request.pointOfSaleId}, product=${request.productId}"
            )
            call.respond(
                HttpStatusCode.OK,
                websocket.SyncConfirmResponse(
                    success = true,
                    message = "Sincronización confirmada exitosamente"
                )
            )
        } else {
            logger.warn(
                "Sync confirmation failed - record not found: establishment=${request.establishmentId}, " +
                "pointOfSale=${request.pointOfSaleId}, product=${request.productId}"
            )
            call.respond(
                HttpStatusCode.NotFound,
                websocket.SyncConfirmResponse(
                    success = false,
                    message = "Registro no encontrado en la base de datos"
                )
            )
        }

    } catch (e: Exception) {
        logger.error("Error in sync/confirm endpoint: ${e.message}", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            websocket.SyncConfirmResponse(
                success = false,
                message = "Error interno del servidor al procesar la sincronización"
            )
        )
    }
}
