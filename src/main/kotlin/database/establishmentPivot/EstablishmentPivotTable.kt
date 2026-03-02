package database.establishmentPivot

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import database.products.ProductsTable

/**
 * DTO para serialización de EstablishmentPivot
 * Representa el stock de un producto en un punto de venta específico
 */
@Serializable
data class EstablishmentPivotTable(
    val id: String = "",
    val establishmentId: String = "",
    val productId: String = "",
    val pointOfSaleId: String = "",
    val stock: Long = 0L,
    val isSync: Int = 0
)

/**
 * Tabla pivote: EstablishmentPivot
 *
 * Propósito: Relacionar productos con múltiples POS dentro de un establecimiento
 *
 * Estructura:
 * - id: Clave primaria UUID única para cada registro
 * - establishmentId: Referencia al establecimiento (FK implícita para auditoría)
 * - productId: Referencia a Products (FK)
 * - pointOfSaleId: Referencia al POS dentro del establecimiento (en conjunto con establishmentId)
 * - stock: Stock actual del producto en ese POS
 * - isSync: Flag de sincronización (0 = no sincronizado, 1 = sincronizado)
 *
 * Claves:
 * - PK: id (UUID)
 * - Índices compuestos: (establishmentId, pointOfSaleId, productId) para búsquedas rápidas
 * - FK: productId -> ProductsTable.id
 */
object EstablishmentPivot: Table("establishment_pivot") {
    val id = varchar("id", 36)  // UUID format
    val establishmentId = varchar("establishment_id", 255)
    val productId = varchar("product_id", 255).references(ProductsTable.id)
    val pointOfSaleId = varchar("point_of_sale_id", 255)
    val stock = long("stock")
    val isSync = integer("is_sync").default(0)  // 0 = not synced, 1 = synced

    override val primaryKey = PrimaryKey(id)

    init {
        // Índice compuesto: búsqueda por establecimiento, POS y producto
        index(isUnique = false, establishmentId, pointOfSaleId, productId)
        // Índice para encontrar registros no sincronizados
        index(isUnique = false, isSync)
        // Índice para búsquedas por establecimiento
        index(isUnique = false, establishmentId)
    }
}
