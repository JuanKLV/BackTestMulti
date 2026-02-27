package mappers.response

import kotlinx.serialization.Serializable

@Serializable
data class SaleDetailDto(
    val id: String = "",
    var saleId : String = "",
    val productId: String = "",
    val quantity: Int = 0,
    val unitPrice: Double = 0.0,
    val discountPercentage: Double = 0.0,
    val discountValue: Double = 0.0,
    val appliedTaxValue: Double = 0.0,
    val unitValueWithTax: Double = 0.0,
    val totalValue: Double = 0.0,
    val isSync: Int = 0,
    var movementHistory: SalesDetailsHistoryDto? = null
)

@Serializable
data class SalesDetailsHistoryDto(
    val id: String = "",
    val saleDetailId: String = "",
    val quantityMoved: Int = 0,
    val lastQuantity: Int = 0,
    val currentQuantity: Int = 0,
    val createAt: String = "",
    val isSync: Int = 0
)