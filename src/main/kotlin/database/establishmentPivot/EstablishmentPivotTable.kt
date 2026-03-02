package database.establishmentPivot

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

@Serializable
data class EstablishmentPivotTable(
    val id: String = "",
    val pointOfSaleId: String = "",
    val stock: Long = 0L,
    val isSync: Int = 0
)

object EstablishmentPivot: Table() {
    val id = varchar("id", 255)
    val pointOfSaleId = varchar("pointOfSaleId", 255)
    val stock = long("stock")
    val isSync = integer("isSync")
    override val primaryKey = PrimaryKey(id)
}
