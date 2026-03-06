package database.websocket

import org.jetbrains.exposed.sql.Table

/**
 * Tabla para rastrear sesiones WebSocket activas.
 * Permite auditar conexiones/desconexiones y monitorear sesiones activas por establecimiento.
 */
object WebSocketSessionTable : Table("websocket_sessions") {
    val id = varchar("id", 36)  // UUID format: 36 chars
    val establishmentId = varchar("establishment_id", 255)
    val posId = varchar("pos_id", 255).nullable()
    val connectedAt = long("connected_at").default(System.currentTimeMillis())
    val disconnectedAt = long("disconnected_at").nullable()
    val isActive = bool("is_active").default(true)
    val lastHeartbeat = long("last_heartbeat").default(System.currentTimeMillis())
    val isSync = bool("isSync").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = true, posId)
        index(isUnique = false, establishmentId)
        index(isUnique = false, isActive)
        index(isUnique = false, establishmentId, isActive)  // Índice compuesto para búsquedas frecuentes
    }
}

