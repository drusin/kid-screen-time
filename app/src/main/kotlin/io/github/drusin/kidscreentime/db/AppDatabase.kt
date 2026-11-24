package io.github.drusin.kidscreentime.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The one local database of the app (Room). The Kindgerät keeps the last Kind-Snapshot and the Outbox here.
 * A new entity means: add it to `entities`, raise `version`, add a `Migration` to [MIGRATIONS] (the schema JSON of
 * every version is committed in `app/schemas`; `AppDatabaseMigrationTest` checks every step).
 */
@Database(entities = [ChildSnapshotEntity::class, OutboxSessionEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childSnapshotDao(): ChildSnapshotDao

    abstract fun outboxDao(): OutboxDao

    companion object {
        const val FILE_NAME = "kid-screen-time.db"

        /** Version 2 (ticket 05): the Outbox of the Kindgerät. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox_session` (`sessionId` TEXT NOT NULL, `startMonotonicMillis` INTEGER NOT NULL, " +
                        "`startWallMillis` INTEGER NOT NULL, `endMonotonicMillis` INTEGER, `endWallMillis` INTEGER, " +
                        "`revision` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))",
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME).addMigrations(*MIGRATIONS).build()
    }
}

/**
 * The last complete Kind-Snapshot (always exactly one row, [id] is 0), stored as the JSON of the REST contract.
 * The snapshot is always the full state and never a delta, so one blob is enough and survives contract
 * additions without a schema change. [receivedAtEpochMillis] is the device wall time of the last successful sync.
 */
@Entity(tableName = "child_snapshot")
data class ChildSnapshotEntity(
    @PrimaryKey val id: Int = 0,
    val json: String,
    val receivedAtEpochMillis: Long,
)

@Dao
interface ChildSnapshotDao {
    @Query("SELECT * FROM child_snapshot WHERE id = 0")
    fun observe(): Flow<ChildSnapshotEntity?>

    @Query("SELECT * FROM child_snapshot WHERE id = 0")
    suspend fun current(): ChildSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ChildSnapshotEntity)

    @Query("DELETE FROM child_snapshot")
    suspend fun clear()
}

/**
 * The Outbox: every Start or Stopp lands here first (before the UI shows it and before any sync). One row per
 * Sitzung holds its whole state (an upsert, like the sync). The Zeitpunkte are kept twice: [startMonotonicMillis] and
 * [endMonotonicMillis] (monotonic clock, the base for the age the sync sends) and the wall times, which the UI
 * calculates with immediately and which ticket 08 uses after a restart. A row stays until a sync delivered exactly
 * this [revision] (every change counts it up), so a Stopp pressed during a running sync is not lost.
 */
@Entity(tableName = "outbox_session")
data class OutboxSessionEntity(
    @PrimaryKey val sessionId: String,
    val startMonotonicMillis: Long,
    val startWallMillis: Long,
    val endMonotonicMillis: Long? = null,
    val endWallMillis: Long? = null,
    val revision: Int = 0,
)

@Dao
interface OutboxDao {
    /** All open Sitzungen in start order. */
    @Query("SELECT * FROM outbox_session ORDER BY startWallMillis, sessionId")
    fun observeAll(): Flow<List<OutboxSessionEntity>>

    @Query("SELECT * FROM outbox_session ORDER BY startWallMillis, sessionId")
    suspend fun all(): List<OutboxSessionEntity>

    @Query("SELECT * FROM outbox_session WHERE sessionId = :sessionId")
    suspend fun find(sessionId: String): OutboxSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OutboxSessionEntity)

    /** Removes a row once its state is delivered, unless it changed meanwhile. */
    @Query("DELETE FROM outbox_session WHERE sessionId = :sessionId AND revision = :revision")
    suspend fun deleteDelivered(sessionId: String, revision: Int)

    @Query("DELETE FROM outbox_session")
    suspend fun clear()
}
