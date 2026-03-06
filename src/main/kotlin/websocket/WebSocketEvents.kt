package websocket

import database.products.ProductsModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.*

/**
 * Tipos de eventos que se pueden sincronizar entre POS
 */
enum class EventType {
    PRODUCT,
    PORTFOLIO_UPDATED,
    HEARTBEAT,
    CONNECTION_ACK,
    ERROR
}

/**
 * Envelope genérico para todos los eventos WebSocket
 * Permite serialización flexible del payload
 */
@Serializable
data class WebSocketMessage(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val establishmentId: String,
    val posId: String? = null,
    val payload: JsonElement,
    val product: ProductsModel? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DTOs para payloads específicos
 */

@Serializable
data class InventoryUpdatedPayload(
    val productId: String,
    val productName: String,
    val previousQuantity: Int,
    val newQuantity: Int,
    val changeReason: String = "UNKNOWN"
)

@Serializable
data class PortfolioUpdatedPayload(
    val portfolioId: String,
    val portfolioName: String,
    val totalValue: Double,
    val changeType: String = "UNKNOWN" // ADD, REMOVE, UPDATE
)

@Serializable
data class HeartbeatPayload(
    val connectionId: String,
    val status: String = "ALIVE"
)

@Serializable
data class ConnectionAckPayload(
    val connectionId: String,
    val establishmentId: String,
    val posId: String?,
    val serverTimestamp: Long = System.currentTimeMillis(),
    val message: String = "Connected successfully"
)

@Serializable
data class ErrorPayload(
    val errorCode: String,
    val errorMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Clase para registrar eventos persistentemente
 */
@Serializable
data class EventStoreRecord(
    val id: String = UUID.randomUUID().toString(),
    val establishmentId: String,
    val eventType: String,
    val payload: String, // JSON serializado
    val createdAt: Long = System.currentTimeMillis(),
    val processedCount: Int = 0
)
