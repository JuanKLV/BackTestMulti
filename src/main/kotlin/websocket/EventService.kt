package websocket

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/**
 * Servicio de eventos que orquesta broadcasting vía WebSocket
 */
class EventService(
    private val sessionManager: WebSocketSessionManager,
    private val json: Json = Json
) {
    private val logger = LoggerFactory.getLogger(EventService::class.java)

    /**
     * Broadcast de evento INVENTORY_UPDATED
     */
    suspend fun broadcastInventoryUpdate(
        establishmentId: String,
        productId: String,
        productName: String,
        previousQuantity: Int,
        newQuantity: Int,
        changeReason: String = "UNKNOWN",
        excludeSessionId: String? = null
    ): Int {
        return try {
            val payload = InventoryUpdatedPayload(
                productId = productId,
                productName = productName,
                previousQuantity = previousQuantity,
                newQuantity = newQuantity,
                changeReason = changeReason
            )

            val message = WebSocketMessage(
                id = java.util.UUID.randomUUID().toString(),
                type = EventType.PRODUCT.name,
                establishmentId = establishmentId,
                payload = json.parseToJsonElement(
                    json.encodeToString(payload)
                ),
                timestamp = System.currentTimeMillis()
            )

            val serialized = json.encodeToString(message)

            logger.info(
                "Broadcasting INVENTORY_UPDATED: establishmentId=$establishmentId, " +
                "productId=$productId, quantity=$previousQuantity->$newQuantity"
            )

            sessionManager.broadcast(
                establishmentId = establishmentId,
                message = serialized,
                excludeSessionId = excludeSessionId
            )

        } catch (e: Exception) {
            logger.error(
                "Error broadcasting inventory update: establishmentId=$establishmentId, " +
                "productId=$productId, error=${e.message}",
                e
            )
            0
        }
    }

    /**
     * Broadcast de evento PORTFOLIO_UPDATED
     */
    suspend fun broadcastPortfolioUpdate(
        establishmentId: String,
        portfolioId: String,
        portfolioName: String,
        totalValue: Double,
        changeType: String = "UPDATE",
        excludeSessionId: String? = null
    ): Int {
        return try {
            val payload = PortfolioUpdatedPayload(
                portfolioId = portfolioId,
                portfolioName = portfolioName,
                totalValue = totalValue,
                changeType = changeType
            )

            val message = WebSocketMessage(
                id = java.util.UUID.randomUUID().toString(),
                type = EventType.PORTFOLIO_UPDATED.name,
                establishmentId = establishmentId,
                payload = json.parseToJsonElement(
                    json.encodeToString(payload)
                ),
                timestamp = System.currentTimeMillis()
            )

            val serialized = json.encodeToString(message)

            logger.info(
                "Broadcasting PORTFOLIO_UPDATED: establishmentId=$establishmentId, " +
                "portfolioId=$portfolioId, changeType=$changeType, value=$totalValue"
            )

            sessionManager.broadcast(
                establishmentId = establishmentId,
                message = serialized,
                excludeSessionId = excludeSessionId
            )

        } catch (e: Exception) {
            logger.error(
                "Error broadcasting portfolio update: establishmentId=$establishmentId, " +
                "portfolioId=$portfolioId, error=${e.message}",
                e
            )
            0
        }
    }

    /**
     * Obtener estadísticas
     */
    fun getStats(): WebSocketStats {
        return sessionManager.getStats()
    }

    /**
     * Verificar si hay conexiones activas
     */
    fun hasActiveConnections(establishmentId: String): Boolean {
        return sessionManager.hasActiveConnections(establishmentId)
    }
}

