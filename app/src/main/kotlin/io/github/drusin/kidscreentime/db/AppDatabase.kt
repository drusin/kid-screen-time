package io.github.drusin.kidscreentime.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The one local database of the app (Room). The Kindgerät keeps the last Kind-Snapshot here. Ticket 05 adds the
 * Outbox as a further entity in this same database: add the entity to [AppDatabase.entities], raise `version`,
 * and add a `Migration` (the schema JSON of every version is committed in `app/schemas`).
 */
@Database(entities = [ChildSnapshotEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childSnapshotDao(): ChildSnapshotDao

    companion object {
        const val FILE_NAME = "kid-screen-time.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME).build()
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ChildSnapshotEntity)

    @Query("DELETE FROM child_snapshot")
    suspend fun clear()
}
