package com.onoff.database

import database.products.ProductsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import utils.ConstantsKotlin

object DatabaseSingleton {
    fun init() {
        val dataBase = Database.connect(
            url = ConstantsKotlin.URL_DB_LOCAL,
            driver = ConstantsKotlin.DRIVER_POSTGRES,
            user = ConstantsKotlin.USER_DB,
            password = ConstantsKotlin.PASSWORD,
        )
        transaction(dataBase) {
            SchemaUtils.create(ProductsTable)
        }
    }
}