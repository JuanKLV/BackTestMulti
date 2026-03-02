package database.eventstore

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SortOrder
import websocket.EventStoreRecord

class EventStoreDao {

    /**
     * Insertar un nuevo evento en el store
     */
    suspend fun insertEvent(record: EventStoreRecord): EventStoreRecord = dbQuery {
        EventStoreTable.insert {
            it[id] = record.id
            it[establishmentId] = record.establishmentId
            it[eventType] = record.eventType
            it[payload] = record.payload
            it[processedCount] = record.processedCount
        }
        record
    }

    /**
     * Obtener eventos recientes de un establecimiento
     */
    suspend fun getRecentEvents(
        establishmentId: String,
        limit: Int = 50,
        offsetMinutes: Int = 60
    ): List<EventStoreRecord> = dbQuery {
        val cutoffTime = System.currentTimeMillis() - (offsetMinutes.toLong() * 60 * 1000)

        EventStoreTable
            .select {
                (EventStoreTable.establishmentId eq establishmentId) and
                (EventStoreTable.createdAt greaterEq cutoffTime)
            }
            .orderBy(EventStoreTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                EventStoreRecord(
                    id = row[EventStoreTable.id],
                    establishmentId = row[EventStoreTable.establishmentId],
                    eventType = row[EventStoreTable.eventType],
                    payload = row[EventStoreTable.payload],
                    createdAt = row[EventStoreTable.createdAt],
                    processedCount = row[EventStoreTable.processedCount]
                )
            }
    }

    /**
     * Incrementar contador de procesamiento
     * Función auxiliar para auditoría (puede no estar en uso inmediato)
     */
    @Suppress("unused")
    suspend fun incrementProcessedCount(eventId: String): Int = dbQuery {
        // Obtener el valor actual
        val currentCount = EventStoreTable
            .select { EventStoreTable.id eq eventId }
            .map { it[EventStoreTable.processedCount] }
            .firstOrNull() ?: 0

        // Actualizar con el nuevo valor
        EventStoreTable.update({ EventStoreTable.id eq eventId }) {
            it[processedCount] = currentCount + 1
        }

        currentCount + 1
    }

    /**
     * Limpiar eventos antiguos
     * Función de mantenimiento para limpieza periódica de datos históricos
     */
    @Suppress("unused")
    suspend fun purgeOldEvents(retentionDays: Int = 30): Int = dbQuery {
        val cutoffTime = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)

        EventStoreTable.deleteWhere {
            EventStoreTable.createdAt lessEq cutoffTime
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
