package com.onoff.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mappers.response.SaleDto

fun Application.configureRouting() {
    routing {
        post("/sales") {
            val body = call.receive<SaleDto>()
            call.respondText("Puto el que lo lea")
        }
    }
}
