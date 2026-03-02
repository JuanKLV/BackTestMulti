package plugins

import com.onoff.plugins.WebSocketState
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
import java.util.UUID

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")
    val json = Json

    routing {
        // ================== WEBSOCKET ENDPOINT ==================
        webSocket("/ws/{establishmentId}/{posId}") {
            val establishmentId = call.parameters["establishmentId"]
            val posId = call.parameters["posId"]

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

            // Usar singletons globales del WebSocket plugin
            try {
                WebSocketState.connectionManager.register(
                    establishmentId = establishmentId,
                    sessionId = UUID.randomUUID().toString(),
                    session = this,
                    posId = posId
                )
            } catch (e: Exception) {
                logger.error("Error registering WebSocket session: ${e.message}", e)
                this.close(CloseReason(
                    CloseReason.Codes.INTERNAL_ERROR,
                    "Failed to register connection"
                ))
                return@webSocket
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

