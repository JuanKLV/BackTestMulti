package com.onoff.plugins.sale

import database.products.ProductsDao
import database.products.ProductsModel
import database.websocket.WebSocketSessionDao
import kotlinx.coroutines.Dispatchers
import mappers.response.SaleDto
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import com.onoff.plugins.WebSocketState

class SaleRepository {

    private val logger = LoggerFactory.getLogger(SaleRepository::class.java)
    private val productsDao = ProductsDao()
    private val webSocketSession = WebSocketSessionDao()

    suspend fun updateStockProduct(sale: SaleDto): String {
        try {
            // Validate establishmentId from sale
            require(sale.establishmentId.isNotBlank()) {
                "establishmentId is required in sale DTO"
            }

            require(sale.pointOfSaleId.isNotBlank()) {
                "pointOfSaleId is required in sale DTO"
            }

            val result = newSuspendedTransaction(Dispatchers.IO) {
                sale.saleDetails.forEach { detail ->
                    productsDao.updateProductStock(detail)
                }

                webSocketSession.updateSync(sale.establishmentId, sale.pointOfSaleId)

                "Sale processed successfully. Stock updated and sync marked as unsync for establishment: ${sale.establishmentId}"
            }

            // Después de actualizar stock en BD, enviar productos a sesiones no sincronizadas
            notifyUnsyncedSessionsWithProducts(sale)

            return result

        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Error processing sale: ${e.message}", e)
        }
    }

    /**
     * Envía los productos actualizados a las sesiones WebSocket que tienen isSync = false
     * (aquellos POS que no realizaron la venta)
     * Después de enviar, marca esas sesiones como sincronizadas (isSync = true)
     */
    private suspend fun notifyUnsyncedSessionsWithProducts(sale: SaleDto) {
        try {
            val establishmentId = sale.establishmentId

            // 1. Obtener sesiones con isSync = false
            val unsyncedSessions = webSocketSession.getUnsyncedSessions(establishmentId)

            if (unsyncedSessions.isEmpty()) {
                logger.debug("No unsynced sessions found for establishment: $establishmentId")
                return
            }

            // 2. Obtener IDs de productos actualizados en la venta
            val productIds = sale.saleDetails.map { it.productId }

            // 3. Obtener productos completos actualizados de la BD
            val updatedProducts = productsDao.getProductsByIds(productIds)

            if (updatedProducts.isEmpty()) {
                logger.warn("No products found for sale details in establishment: $establishmentId")
                return
            }

            // 4. Obtener sesión IDs de las sesiones no sincronizadas
            val sessionIds = unsyncedSessions.map { it.id }

            // 5. Enviar productos a través de WebSocket a esas sesiones específicas
            logger.info(
                "Notifying ${sessionIds.size} unsynced sessions in establishment $establishmentId " +
                "with ${updatedProducts.size} updated products"
            )

            WebSocketState.eventBroadcaster.broadcastProductsToSessions(
                establishmentId = establishmentId,
                products = updatedProducts,
                sessionIds = sessionIds
            )

            logger.info(
                "Successfully marked ${sessionIds.size} sessions as synced in establishment $establishmentId"
            )

        } catch (e: Exception) {
            logger.error(
                "Error notifying unsynced sessions with products: ${e.message}",
                e
            )
            // No lanzar excepción para no afectar el flujo de venta
            // Solo logging para auditoría
        }
    }

    suspend fun saveProduct(products: ProductsModel) {
        productsDao.saveProduct(products)
    }

}