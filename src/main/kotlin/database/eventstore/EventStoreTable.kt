package database.eventstore

import org.jetbrains.exposed.sql.Table

/**
 * Tabla para almacenar eventos de sincronización
 * Proporciona auditoría, recuperación y replay de eventos
 */
object EventStoreTable : Table("event_store") {
    val id = varchar("id", 36)
    val establishmentId = varchar("establishment_id", 255)
    val eventType = varchar("event_type", 50)
    val payload = text("payload")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val processedCount = integer("processed_count").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, establishmentId)
        index(isUnique = false, createdAt)
    }
}



