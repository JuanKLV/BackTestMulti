package database.websocket

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not

data class WebSocketSessionRecord(
    val id: String,
    val establishmentId: String,
    val posId: String?,
    val connectedAt: Long,
    val disconnectedAt: Long? = null,
    val isActive: Boolean = true,
    val lastHeartbeat: Long = 0,
    val isSync: Boolean = true
)

@Suppress("unused")
class WebSocketSessionDao {

    @Suppress("unused")
    suspend fun saveSession(record: WebSocketSessionRecord) = dbQuery {
        WebSocketSessionTable.insert {
            it[id] = record.id
            it[establishmentId] = record.establishmentId
            it[posId] = record.posId
            it[connectedAt] = record.connectedAt
            it[disconnectedAt] = record.disconnectedAt
            it[isActive] = record.isActive
            it[isSync] = true
            it[lastHeartbeat] = record.lastHeartbeat
        }
    }

    @Suppress("unused")
    suspend fun updateSessionDisconnected(sessionId: String, disconnectedAt: Long) = dbQuery {
        WebSocketSessionTable.update({ WebSocketSessionTable.id eq sessionId }) {
            it[WebSocketSessionTable.disconnectedAt] = disconnectedAt
            it[isActive] = false
        }
    }

    @Suppress("unused")
    suspend fun updateHeartbeat(sessionId: String, heartbeatTime: Long) = dbQuery {
        WebSocketSessionTable.update({ WebSocketSessionTable.id eq sessionId }) {
            it[lastHeartbeat] = heartbeatTime
        }
    }

    @Suppress("unused")
    suspend fun getActiveSessions(establishmentId: String): List<WebSocketSessionRecord> = dbQuery {
        WebSocketSessionTable
            .select {
                (WebSocketSessionTable.establishmentId eq establishmentId) and (WebSocketSessionTable.isActive eq true)
            }
            .map { row ->
                WebSocketSessionRecord(
                    id = row[WebSocketSessionTable.id],
                    establishmentId = row[WebSocketSessionTable.establishmentId],
                    posId = row[WebSocketSessionTable.posId],
                    connectedAt = row[WebSocketSessionTable.connectedAt],
                    disconnectedAt = row[WebSocketSessionTable.disconnectedAt],
                    isActive = row[WebSocketSessionTable.isActive],
                    lastHeartbeat = row[WebSocketSessionTable.lastHeartbeat],
                    isSync = row[WebSocketSessionTable.isSync]
                )
            }
    }

    @Suppress("unused")
    suspend fun getAllActiveSessions(): List<WebSocketSessionRecord> = dbQuery {
        WebSocketSessionTable
            .select { WebSocketSessionTable.isActive eq true }
            .map { row ->
                WebSocketSessionRecord(
                    id = row[WebSocketSessionTable.id],
                    establishmentId = row[WebSocketSessionTable.establishmentId],
                    posId = row[WebSocketSessionTable.posId],
                    connectedAt = row[WebSocketSessionTable.connectedAt],
                    disconnectedAt = row[WebSocketSessionTable.disconnectedAt],
                    isActive = row[WebSocketSessionTable.isActive],
                    lastHeartbeat = row[WebSocketSessionTable.lastHeartbeat],
                    isSync = row[WebSocketSessionTable.isSync]
                )
            }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    /**
     * Obtiene todas las sesiones WebSocket que NO han sido sincronizadas (isSync = false)
     * para un establecimiento específico.
     */
    @Suppress("unused")
    suspend fun getUnsyncedSessions(establishmentId: String): List<WebSocketSessionRecord> = dbQuery {
        WebSocketSessionTable
            .select {
                (WebSocketSessionTable.establishmentId eq establishmentId) and (WebSocketSessionTable.isSync eq false)
            }
            .map { row ->
                WebSocketSessionRecord(
                    id = row[WebSocketSessionTable.id],
                    establishmentId = row[WebSocketSessionTable.establishmentId],
                    posId = row[WebSocketSessionTable.posId],
                    connectedAt = row[WebSocketSessionTable.connectedAt],
                    disconnectedAt = row[WebSocketSessionTable.disconnectedAt],
                    isActive = row[WebSocketSessionTable.isActive],
                    lastHeartbeat = row[WebSocketSessionTable.lastHeartbeat],
                    isSync = row[WebSocketSessionTable.isSync]
                )
            }
    }

    /**
     * Actualiza isSync a true para las sesiones que NO realizaron la venta.
     * Se ejecuta después de enviar los productos actualizados por WebSocket.
     */
    @Suppress("unused")
    suspend fun markUnsyncedSessionsAsSynced(establishmentId: String) = dbQuery {
        WebSocketSessionTable.update({
            (WebSocketSessionTable.establishmentId eq establishmentId) and (WebSocketSessionTable.isSync eq false)
        }) {
            it[WebSocketSessionTable.isSync] = true
        }
    }

    fun updateSync(establishmentId: String, pointOfSaleId: String) {
        WebSocketSessionTable.update({
            not(
                (WebSocketSessionTable.establishmentId eq establishmentId) and
                        (WebSocketSessionTable.posId eq pointOfSaleId)
            )
        }) {
            it[isSync] = false
        }
    }
}

