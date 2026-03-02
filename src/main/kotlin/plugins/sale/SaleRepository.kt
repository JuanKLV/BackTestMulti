package com.onoff.plugins.sale

import database.establishmentPivot.EstablishmentPivotDao
import database.products.ProductsDao
import kotlinx.coroutines.Dispatchers
import mappers.response.SaleDto
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class SaleRepository {

    private val productsDao = ProductsDao()
    private val establishmentPivotDao = EstablishmentPivotDao()

    suspend fun updateStockProduct(sale: SaleDto): String {
        try {
            // Validate establishmentId from sale
            require(sale.establishmentId.isNotBlank()) {
                "establishmentId is required in sale DTO"
            }

            require(sale.pointOfSaleId.isNotBlank()) {
                "pointOfSaleId is required in sale DTO"
            }

            // Process each sale detail
            sale.saleDetails.forEach { detail ->
                // Save or update product details (UPSERT)
                // This ensures product exists and quantity is current
                productsDao.saveProduct(detail)
            }

            // Now process stock deductions within a transaction
            val result = newSuspendedTransaction(Dispatchers.IO) {
                sale.saleDetails.forEach { detail ->
                    // Get the quantity to deduct (corrected: use actual quantity from detail)
                    val quantityToDeduct = detail.quantity.toLong()

                    // UPSERT: Update stock if exists, insert with deduction if new
                    // This ensures synchronization logic is maintained per product
                    establishmentPivotDao.upsertAndDeductStockInTx(
                        establishmentId = sale.establishmentId,
                        pointOfSaleId = sale.pointOfSaleId,
                        productId = detail.productId,
                        quantityToDeduct = quantityToDeduct,
                        initialStock = 100L  // Start from 0 if new product
                    )
                }

                // Mark all POS in the same establishment as unsync
                // This ensures proper synchronization of all updated products
                establishmentPivotDao.markAsUnsyncByEstablishmentInTx(sale.establishmentId)

                "Sale processed successfully. Stock updated and sync marked as unsync for establishment: ${sale.establishmentId}"
            }

            return result

        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Error processing sale: ${e.message}", e)
        }
    }

}