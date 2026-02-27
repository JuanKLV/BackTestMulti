package database.products

import org.jetbrains.exposed.sql.Table
import kotlinx.serialization.Serializable

@Serializable
data class ProductsModel(
    val id: String = "",
    val quantity: Int = 0,
    val name: String = ""
)

object ProductsTable : Table() {
    val id = varchar("id",255)
    val quantity = integer("quantity")
    val name = varchar("name", 255)
    override val primaryKey = PrimaryKey(id)
}