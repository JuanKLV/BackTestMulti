package websocket

import kotlinx.serialization.Serializable

/**
 * DTOs para peticiones REST que disparan eventos WebSocket
 */

@Serializable
data class InventoryUpdateRequest(
    val establishmentId: String,
    val productId: String,
    val productName: String,
    val previousQuantity: Int,
    val newQuantity: Int,
    val changeReason: String = "UNKNOWN"
)

@Serializable
data class PortfolioUpdateRequest(
    val establishmentId: String,
    val portfolioId: String,
    val portfolioName: String,
    val totalValue: Double,
    val changeType: String = "UPDATE" // ADD, REMOVE, UPDATE
)

@Serializable
data class EventResponse(
    val success: Boolean,
    val message: String,
    val sessionsNotified: Int? = null,
    val eventId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val error: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SyncConfirmRequest(
    val productId: String,
    val establishmentId: String,
    val pointOfSaleId: String
)

@Serializable
data class SyncConfirmResponse(
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
