package com.onoff.database.establishmentPivot

import database.establishmentPivot.EstablishmentPivot.isSync
import database.establishmentPivot.EstablishmentPivot.pointOfSaleId
import database.establishmentPivot.EstablishmentPivot.stock
import database.establishmentPivot.EstablishmentPivotTable
import database.products.ProductsTable.id
import kotlinx.coroutines.Dispatchers
import mappers.response.SaleDetailDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class EstablishmentPivotDao {

    private fun tableToEstablismentPivot(row: ResultRow) = EstablishmentPivotTable(
        id = row[id],
        pointOfSaleId = row[pointOfSaleId],
        stock = row[stock],
        isSync = row[isSync],
    )

    private suspend fun <T> dbQueryEstablisment(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

}