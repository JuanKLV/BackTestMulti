package database.establishmentPivot

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.insert
import java.util.UUID

class EstablishmentPivotDao {

    private fun rowToEstablishmentPivot(row: ResultRow) = EstablishmentPivotTable(
        id = row[EstablishmentPivot.id],
        establishmentId = row[EstablishmentPivot.establishmentId],
        productId = row[EstablishmentPivot.productId],
        pointOfSaleId = row[EstablishmentPivot.pointOfSaleId],
        stock = row[EstablishmentPivot.stock],
        isSync = row[EstablishmentPivot.isSync]
    )

    // Internal method that works within an existing transaction
    fun getStockByEstablishmentAndProductInTx(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String
    ): Long? {
        return EstablishmentPivot.select {
            (EstablishmentPivot.establishmentId eq establishmentId) and
            (EstablishmentPivot.pointOfSaleId eq pointOfSaleId) and
            (EstablishmentPivot.productId eq productId)
        }.map { rowToEstablishmentPivot(it).stock }.singleOrNull()
    }

    // Internal method: Check if a pivot record exists
    fun recordExistsInTx(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String
    ): Boolean {
        return EstablishmentPivot.select {
            (EstablishmentPivot.establishmentId eq establishmentId) and
            (EstablishmentPivot.pointOfSaleId eq pointOfSaleId) and
            (EstablishmentPivot.productId eq productId)
        }.count() > 0
    }

    // Internal method: UPSERT - Insert or update stock record
    // Creates a new record if it doesn't exist, updates if it does
    fun upsertStockInTx(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String,
        stock: Long
    ): Boolean {
        val exists = recordExistsInTx(establishmentId, pointOfSaleId, productId)

        return if (exists) {
            // Update existing record
            EstablishmentPivot.update(
                where = {
                    (EstablishmentPivot.establishmentId eq establishmentId) and
                    (EstablishmentPivot.pointOfSaleId eq pointOfSaleId) and
                    (EstablishmentPivot.productId eq productId)
                }
            ) {
                it[EstablishmentPivot.stock] = stock
                it[EstablishmentPivot.isSync] = 0
            } > 0
        } else {
            // Insert new record
            EstablishmentPivot.insert {
                it[id] = UUID.randomUUID().toString()
                it[EstablishmentPivot.establishmentId] = establishmentId
                it[EstablishmentPivot.pointOfSaleId] = pointOfSaleId
                it[EstablishmentPivot.productId] = productId
                it[EstablishmentPivot.stock] = stock
                it[EstablishmentPivot.isSync] = 0
            }.insertedCount > 0
        }
    }

    // Internal method: UPSERT with stock deduction
    // Updates existing record or creates new one with stock initialized
    // This handles the case where a product needs to be tracked from first sale
    fun upsertAndDeductStockInTx(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String,
        quantityToDeduct: Long,
        initialStock: Long = 0L
    ): Boolean {
        val exists = recordExistsInTx(establishmentId, pointOfSaleId, productId)

        return if (exists) {
            // Update existing record: deduct the quantity
            val currentStock = getStockByEstablishmentAndProductInTx(
                establishmentId,
                pointOfSaleId,
                productId
            )

            require(currentStock != null) {
                "Product $productId not found in inventory for establishment $establishmentId"
            }

            require(currentStock >= quantityToDeduct) {
                "Insufficient stock for product $productId. Available: $currentStock, Requested: $quantityToDeduct"
            }

            EstablishmentPivot.update(
                where = {
                    (EstablishmentPivot.establishmentId eq establishmentId) and
                    (EstablishmentPivot.productId eq productId)
                }
            ) {
                it[EstablishmentPivot.stock] = currentStock - quantityToDeduct
                it[EstablishmentPivot.isSync] = 0
            } > 0
        } else {
            // Insert new record with initialStock - quantityToDeduct
            val finalStock = initialStock - quantityToDeduct
            // Allow negative stock to track inventory debt
            // (No validation needed - finalStock can be negative)

            EstablishmentPivot.insert {
                it[id] = UUID.randomUUID().toString()
                it[EstablishmentPivot.establishmentId] = establishmentId
                it[EstablishmentPivot.pointOfSaleId] = pointOfSaleId
                it[EstablishmentPivot.productId] = productId
                it[EstablishmentPivot.stock] = finalStock
                it[EstablishmentPivot.isSync] = 0
            }.insertedCount > 0
        }
    }

    // Internal method that works within an existing transaction
    fun updateStockInTx(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String,
        quantityToDeduct: Long
    ): Boolean {
        val currentStock = getStockByEstablishmentAndProductInTx(
            establishmentId,
            pointOfSaleId,
            productId
        )

        require(currentStock != null) {
            "Product $productId not found in inventory for establishment $establishmentId"
        }

        require(currentStock >= quantityToDeduct) {
            "Insufficient stock for product $productId. Available: $currentStock, Requested: $quantityToDeduct"
        }

        return EstablishmentPivot.update(
            where = {
                (EstablishmentPivot.establishmentId eq establishmentId) and
                (EstablishmentPivot.pointOfSaleId eq pointOfSaleId) and
                (EstablishmentPivot.productId eq productId)
            }
        ) {
            it[EstablishmentPivot.stock] = currentStock - quantityToDeduct
            it[EstablishmentPivot.isSync] = 0
        } > 0
    }

    // Internal method that works within an existing transaction
    fun markAsUnsyncByEstablishmentInTx(establishmentId: String): Int {
        return EstablishmentPivot.update(
            where = { EstablishmentPivot.establishmentId eq establishmentId }
        ) {
            it[EstablishmentPivot.isSync] = 0
        }
    }

    // Suspend methods for use outside transactions
    suspend fun getStockByEstablishmentAndProduct(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String
    ): Long? = dbQuery {
        getStockByEstablishmentAndProductInTx(establishmentId, pointOfSaleId, productId)
    }

    suspend fun upsertStock(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String,
        stock: Long
    ): Boolean = dbQuery {
        upsertStockInTx(establishmentId, pointOfSaleId, productId, stock)
    }

    suspend fun upsertAndDeductStock(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String,
        quantityToDeduct: Long,
        initialStock: Long = 0L
    ): Boolean = dbQuery {
        upsertAndDeductStockInTx(establishmentId, pointOfSaleId, productId, quantityToDeduct, initialStock)
    }

    suspend fun updateStock(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String,
        quantityToDeduct: Long
    ): Boolean = dbQuery {
        updateStockInTx(establishmentId, pointOfSaleId, productId, quantityToDeduct)
    }

    suspend fun markAsUnsyncByEstablishment(establishmentId: String): Int = dbQuery {
        markAsUnsyncByEstablishmentInTx(establishmentId)
    }

    // Internal method: Mark as synced (isSync = 1) for a specific pivot record
    private fun confirmSyncInTx(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String
    ): Boolean {
        return EstablishmentPivot.update(
            where = {
                (EstablishmentPivot.establishmentId eq establishmentId) and
                (EstablishmentPivot.pointOfSaleId eq pointOfSaleId) and
                (EstablishmentPivot.productId eq productId)
            }
        ) {
            it[EstablishmentPivot.isSync] = 1
        } > 0
    }


    // Suspend method: Confirm sync for a POS product
    suspend fun confirmSync(
        establishmentId: String,
        pointOfSaleId: String,
        productId: String
    ): Boolean = dbQuery {
        if (recordExistsInTx(establishmentId, pointOfSaleId, productId)) {
            confirmSyncInTx(establishmentId, pointOfSaleId, productId)
        } else {
            false
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}



