package database.products

import database.products.ProductsTable.id
import database.products.ProductsTable.name
import database.products.ProductsTable.quantity
import kotlinx.coroutines.Dispatchers
import mappers.response.SaleDetailDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ProductsDao {

    private fun tableToProducts(row: ResultRow) = ProductsModel(
        id = row[id],
        quantity = row[quantity],
        name = row[name],
    )

    suspend fun saveProduct(product: SaleDetailDto) = dbQueryProducts {
        val products = ProductsTable.select { id eq product.productId }.map { tableToProducts(it) }.singleOrNull()
        if (products == null) {
            ProductsTable.insert {
                it[id] = product.productId
                it[quantity] = product.quantity
                it[name] = "Product ${product.productId}"
            }
        }
    }

    private suspend fun <T> dbQueryProducts(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

}