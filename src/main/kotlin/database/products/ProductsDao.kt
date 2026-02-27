package database.products

import database.products.ProductsTable.id
import database.products.ProductsTable.name
import database.products.ProductsTable.quantity
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ProductsDao {

    private fun tableToProducts(row: ResultRow) = ProductsModel(
        id = row[id],
        quantity = row[quantity],
        name = row[name],
    )


    private suspend fun <T> dbQueryProducts(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}