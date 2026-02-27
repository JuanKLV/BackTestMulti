package mappers.response

import kotlinx.serialization.Serializable

@Serializable
data class SalePaymentsDto(
    val id: String = "",
    var saleId: String = "",
    var cashSessionId: String = "",
    val paymentMethodId: String = "",
    val date: String = "",
    val amount: Double = 0.0,
    val change: Double = 0.0,
    val receivedAmount: Double = 0.0,
    val approvalCode: String = "",
    val isSync: Int = 0
)