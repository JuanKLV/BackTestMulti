package com.onoff.plugins.sale

import database.products.ProductsDao
import mappers.response.SaleDto

class SaleRepository {

    private val productsDao = ProductsDao()

    suspend fun updateStockProduct(sale: SaleDto): String {

        sale.saleDetails.forEach { detail ->
            productsDao.saveProduct(detail)
        }




        return ""
    }

}