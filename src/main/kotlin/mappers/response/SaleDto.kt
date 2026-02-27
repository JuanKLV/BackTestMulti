package mappers.response

import kotlinx.serialization.Serializable

@Serializable
data class SaleDto(
    val id: String = "",
    val customerId: String = "",
    val pointOfSaleId: String = "",
    val salesDate: String = "",
    val cashSessionId: String = "",
    val saleType: Int = 0,
    val discount: Double = 0.0,
    val subtotal: Double = 0.0,
    val totalSale: Double = 0.0,
    var balance: Double = 0.0,
    val amountToBePaid: Double = 0.0,
    val paidValue: Double = 0.0,
    val deliveryValue: Double = 0.0,
    val cashMoneyAdjustment: Int = 0,
    val isSync: Int = 0,
    var saleDetails: List<SaleDetailDto> = emptyList(),
    var salePayments: List<SalePaymentsDto> = emptyList()
)
