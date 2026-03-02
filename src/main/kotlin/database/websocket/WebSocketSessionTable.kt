package database.websocket

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object WebSocketSessionTable : Table("websocket_sessions") {
    val id = varchar("id", 255).primaryKey()
    val establishmentId = varchar("establishment_id", 255).index()
    val posId = varchar("pos_id", 255).nullable()
    val connectedAt = long("connected_at")
    val disconnectedAt = long("disconnected_at").nullable()
    val isActive = bool("is_active").default(true)
    val lastHeartbeat = long("last_heartbeat").default(0)
}

