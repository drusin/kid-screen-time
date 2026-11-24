package io.github.drusin.kidscreentime.server

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite via plain JDBC with a tiny migration runner (instead of Exposed or Flyway: the schema is small,
 * we want no ORM and no extra service-file/fat-JAR handling).
 *
 * One connection guarded by a lock: SQLite allows a single writer anyway and the load is a family.
 * Migrations are the resources `db/migration/001_*.sql`, `002_*.sql`, ... listed in [MIGRATIONS]. The applied
 * version lives in `PRAGMA user_version`. Add a schema change as a new file and a new entry, never edit an old one.
 */
class Database private constructor(private val connection: Connection) : AutoCloseable {
    private val lock = Any()

    /** Runs [block] in one transaction (commit on success, rollback on exception). */
    fun <T> transaction(block: (Connection) -> T): T = synchronized(lock) {
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    fun schemaVersion(): Int = transaction { c ->
        c.createStatement().use { s -> s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) } }
    }

    override fun close() = synchronized(lock) { if (!connection.isClosed) connection.close() }

    companion object {
        val MIGRATIONS = listOf("db/migration/001_pairing.sql", "db/migration/002_kind_stand.sql", "db/migration/003_adjustment_reversal.sql")

        fun open(path: Path): Database {
            path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            val connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
            try {
                connection.createStatement().use {
                    it.execute("PRAGMA foreign_keys = ON")
                    it.execute("PRAGMA journal_mode = WAL")
                }
                val database = Database(connection)
                database.migrate()
                return database
            } catch (e: Throwable) {
                connection.close()
                throw e
            }
        }
    }

    private fun migrate() {
        val current = schemaVersion()
        MIGRATIONS.forEachIndexed { index, resource ->
            val version = index + 1
            if (version <= current) return@forEachIndexed
            val sql = Database::class.java.classLoader.getResourceAsStream(resource)
                ?.use { it.readBytes().decodeToString() }
                ?: error("Migration fehlt: $resource")
            transaction { c ->
                sql.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { statement ->
                    c.createStatement().use { it.execute(statement) }
                }
                // PRAGMA does not accept bind parameters; version is an Int from our own list.
                c.createStatement().use { it.execute("PRAGMA user_version = $version") }
            }
        }
    }
}
