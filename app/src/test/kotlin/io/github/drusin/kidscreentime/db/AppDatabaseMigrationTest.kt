package io.github.drusin.kidscreentime.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every Room migration keeps the data and produces exactly the schema of the exported JSON (`app/schemas`).
 * A database of the old version is built from the committed schema JSON; opening it with the real [AppDatabase]
 * runs the migrations, and Room itself rejects a result that differs from the new schema.
 * (Room's `MigrationTestHelper` would need the schemas as assets, which would end up in the APK.)
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(DB)
    }

    /** Creates the database file in the state of schema [version], exactly as Room did back then. */
    private fun createOldDatabase(version: Int, fill: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/${AppDatabase::class.java.name}/$version.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase.also(fill).close()
        helper.close()
    }

    @Test
    fun `Die Migration auf Version 2 behält den Kind-Snapshot und legt die leere Outbox an`() {
        createOldDatabase(1) { it.execSQL("INSERT INTO child_snapshot (id, json, receivedAtEpochMillis) VALUES (0, '{}', 42)") }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            runBlocking {
                val snapshot = db.childSnapshotDao().current()
                assertEquals("{}", snapshot?.json)
                assertEquals(42L, snapshot?.receivedAtEpochMillis)
                assertEquals(emptyList(), db.outboxDao().all())
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `Die Migration auf Version 3 behält Kind-Snapshot und Outbox und legt den leeren Eltern-Snapshot-Cache an`() {
        createOldDatabase(2) {
            it.execSQL("INSERT INTO child_snapshot (id, json, receivedAtEpochMillis) VALUES (0, '{}', 42)")
            it.execSQL("INSERT INTO outbox_session (sessionId, startMonotonicMillis, startWallMillis, revision) VALUES ('s1', 1, 2, 0)")
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB).addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            runBlocking {
                assertEquals(42L, db.childSnapshotDao().current()?.receivedAtEpochMillis)
                assertEquals(listOf("s1"), db.outboxDao().all().map { it.sessionId })
                assertEquals(null, db.parentSnapshotDao().current())
                db.parentSnapshotDao().save(ParentSnapshotEntity(json = "{}", receivedAtEpochMillis = 7))
                assertEquals(7L, db.parentSnapshotDao().current()?.receivedAtEpochMillis)
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
