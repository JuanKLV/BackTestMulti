package com.onoff.plugins

import com.onoff.plugins.sale.SaleRepository
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mappers.response.SaleDto

fun Application.configureRouting() {

    val repository = SaleRepository()

    routing {
        post("/sales") {
            val sales = call.receive<SaleDto>()
            val response = repository.updateStockProduct(sales)
            call.respond(response)
        }
    }
}
