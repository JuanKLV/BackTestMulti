package database.products

import org.jetbrains.exposed.sql.Table
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class ProductsModel(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val stock: Int = 0,
    val initialStock: Int = 0,
    val minimumStock: Int = 0,
    val urlImage: String = "",
    val unitCost: Double = 0.0,
    val price: Double = 0.0,
    val averageCost: Double = 0.0,
    val isNotAlterInventory: Boolean = false,
    val establishmentId: String = "",
    val productBusinessId: String = "",
    val productBusinessName: String = "",
    val barCode: String = "",
    val brandName: String = "",
    val brandId: String = "",
    val manufacturerId: String = "",
    val categoryName: String = "",
    val categoryId: String = "",
    val subCategoryName: String = "",
    val subCategoryId: String = "",
    val unitMeasureId: String = "",
    val isActive: Boolean = false
)

object ProductsTable : Table() {
    val id = varchar("id",255)
    val name = varchar("name", 255)
    val description = varchar("description",255)
    val stock = integer("stock")
    val initialStock = integer("initialStock")
    val minimumStock = integer("minimumStock")
    val urlImage = varchar("urlImage",255)
    val unitCost = double("unitCost")
    val price = double("price")
    val averageCost = double("averageCost")
    val isNotAlterInventory = bool("isNotAlterInventory")
    val establishmentId = varchar("establishmentId",255)
    val productBusinessId = varchar("productBusinessId",255)
    val productBusinessName = varchar("productBusinessName",255)
    val barCode = varchar("barCode",255)
    val brandName = varchar("brandName",255)
    val brandId = varchar("brandId",255)
    val manufacturerId = varchar("manufacturerId",255)
    val categoryName = varchar("categoryName",255)
    val categoryId = varchar("categoryId",255)
    val subCategoryName = varchar("subCategoryName",255)
    val subCategoryId = varchar("subCategoryId",255)
    val unitMeasureId = varchar("unitMeasureId",255)
    override val primaryKey = PrimaryKey(id)
}