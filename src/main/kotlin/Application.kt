import database.DatabaseSingleton
import plugins.configureRouting
import plugins.configureSerialization
import com.onoff.plugins.configureWebSocket
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(CORS) {
        anyHost() // 🔥 Permite solicitudes desde cualquier origen (para desarrollo, no en producción)
        allowHeader(HttpHeaders.ContentType) // Permitir envío de JSON
        allowHeader(HttpHeaders.Authorization) // Si usas autenticación con tokens
        allowCredentials = true // Permitir credenciales (cookies, autenticación)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    configureSerialization()
    configureWebSocket()
    configureRouting()
    DatabaseSingleton.init()
}
