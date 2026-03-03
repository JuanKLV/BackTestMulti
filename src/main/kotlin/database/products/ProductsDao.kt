package database.products

import database.products.ProductsTable.averageCost
import database.products.ProductsTable.barCode
import database.products.ProductsTable.brandId
import database.products.ProductsTable.brandName
import database.products.ProductsTable.categoryId
import database.products.ProductsTable.categoryName
import database.products.ProductsTable.description
import database.products.ProductsTable.establishmentId
import database.products.ProductsTable.id
import database.products.ProductsTable.initialStock
import database.products.ProductsTable.isNotAlterInventory
import database.products.ProductsTable.manufacturerId
import database.products.ProductsTable.minimumStock
import database.products.ProductsTable.name
import database.products.ProductsTable.price
import database.products.ProductsTable.productBusinessId
import database.products.ProductsTable.productBusinessName
import database.products.ProductsTable.stock
import database.products.ProductsTable.subCategoryId
import database.products.ProductsTable.subCategoryName
import database.products.ProductsTable.unitCost
import database.products.ProductsTable.unitMeasureId
import database.products.ProductsTable.urlImage
import database.websocket.WebSocketSessionTable.isActive
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ProductsDao {

    private fun tableToProducts(row: ResultRow) = ProductsModel(
        id = row[id],
        name = row[name],
        description = row[description],
        stock = row[stock],
        initialStock = row[initialStock],
        minimumStock = row[minimumStock],
        urlImage = row[urlImage],
        unitCost = row[unitCost],
        price = row[price],
        averageCost = row[averageCost],
        isNotAlterInventory = row[isNotAlterInventory],
        establishmentId = row[establishmentId],
        productBusinessId = row[productBusinessId],
        productBusinessName = row[productBusinessName],
        barCode = row[barCode],
        brandName = row[brandName],
        brandId = row[brandId],
        manufacturerId = row[manufacturerId],
        categoryId = row[categoryId],
        categoryName = row[categoryName],
        subCategoryId = row[subCategoryId],
        subCategoryName = row[subCategoryName],
        unitMeasureId = row[unitMeasureId],
        isActive = row[isActive]
    )

    suspend fun saveProduct(products: ProductsModel) = dbQueryProducts {
        ProductsTable.insert {
            it[id] = products.id
            it[name] = products.name
            it[description] = products.description
            it[stock] = products.stock
            it[initialStock] = products.initialStock
            it[minimumStock] = products.minimumStock
            it[urlImage] = products.urlImage
            it[unitCost] = products.unitCost
            it[price] = products.price
            it[averageCost] = products.averageCost
            it[isNotAlterInventory] = products.isNotAlterInventory
            it[establishmentId] = products.establishmentId
            it[productBusinessId] = products.productBusinessId
            it[productBusinessName] = products.productBusinessName
            it[barCode] = products.barCode
            it[brandName] = products.brandName
            it[brandId] = products.brandId
            it[manufacturerId] = products.manufacturerId
            it[categoryName] = products.categoryName
            it[categoryId] = products.categoryId
            it[subCategoryName] = products.subCategoryName
            it[subCategoryId] = products.subCategoryId
            it[unitMeasureId] = products.unitMeasureId
        }
    }

    private suspend fun <T> dbQueryProducts(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

}