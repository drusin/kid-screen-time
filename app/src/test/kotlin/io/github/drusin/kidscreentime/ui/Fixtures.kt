package io.github.drusin.kidscreentime.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import io.github.drusin.kidscreentime.AppContainer
import io.github.drusin.kidscreentime.alarm.AlarmEffects
import io.github.drusin.kidscreentime.db.AppDatabase
import io.github.drusin.kidscreentime.domain.clock.ManualClock
import io.github.drusin.kidscreentime.pairing.FakeTokenStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Robolectric test setup for ViewModels: an [AppContainer] with a fake token store, a MockEngine whose
 * answers the test sets through [handler], a [ManualClock] for both clocks and an in-memory Room database.
 * Reuse it for later screens.
 */
class TestContainer(
    /** Wall and monotonic clock of the container. Advance it, never wait. A sync scenario shares the server's clock. */
    val clock: ManualClock = ManualClock(),
    /** Replaces the MockEngine, e.g. by an engine that forwards to the real server (see `SyncScenario`). */
    engine: HttpClientEngine? = null,
    /** Replaces sound and full screen of the alarm, e.g. by `RecordingAlarmEffects`. */
    alarmEffects: AlarmEffects = AlarmEffects.NONE,
) {
    val requests = mutableListOf<HttpRequestData>()
    var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("{}") }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val container = AppContainer(
        context = context,
        tokenStore = FakeTokenStore(),
        engine = engine ?: MockEngine { request -> requests += request; handler(request) },
        wallClock = clock,
        monotonicClock = clock,
        database = database,
        alarmEffects = alarmEffects,
    )
}

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/**
 * Waits (in real time, at most 5 s) until the state satisfies [predicate]. Ktor's MockEngine answers on a
 * background dispatcher, so a ViewModel's request finishes slightly after `submit()` returns.
 */
suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
    withContext(Dispatchers.Default) { withTimeout(5_000) { first(predicate) } }

/** The Kind-Snapshot of the REST contract as JSON, as the server would send it (Monday 2026-01-05, Berlin). */
fun childSnapshotJson(snapshot: io.github.drusin.kidscreentime.api.ChildSnapshot): String =
    kotlinx.serialization.json.Json.encodeToString(io.github.drusin.kidscreentime.api.ChildSnapshot.serializer(), snapshot)

/** A Kind-Snapshot for Monday 2026-01-05 09:00 Berlin: Wochenvorlage 60 min, nothing used, no Sitzung, unless given. */
fun testChildSnapshot(
    serverTime: java.time.Instant,
    sessions: List<io.github.drusin.kidscreentime.api.SessionRecord> = emptyList(),
    adjustments: List<io.github.drusin.kidscreentime.api.DayAdjustment> = emptyList(),
    templateToday: List<Int> = listOf(60, 60, 60, 60, 60, 120, 120),
    templateTomorrow: List<Int> = templateToday,
): io.github.drusin.kidscreentime.api.ChildSnapshot {
    val zone = java.time.ZoneId.of("Europe/Berlin")
    val today = serverTime.atZone(zone).toLocalDate()
    return io.github.drusin.kidscreentime.api.ChildSnapshot(
        childId = "kind-1",
        childName = "Mia",
        serverTimeEpochMillis = serverTime.toEpochMilli(),
        timeZone = zone.id,
        // The device calculates on its own; the derived status is not what the screen reads.
        status = io.github.drusin.kidscreentime.api.ChildStatus(
            day = today.toString(),
            allowance = io.github.drusin.kidscreentime.api.AllowanceBreakdown(60, 0, 60),
            usedMillis = 0,
            remainingMillis = 3_600_000,
            overrunMillis = 0,
            state = io.github.drusin.kidscreentime.api.ChildState.READY,
        ),
        warningThresholdMinutes = 5,
        overrunLimitMinutes = 60,
        templateToday = io.github.drusin.kidscreentime.api.WeeklyTemplateVersion("2026-01-01", templateToday),
        templateTomorrow = io.github.drusin.kidscreentime.api.WeeklyTemplateVersion(if (templateTomorrow == templateToday) "2026-01-01" else today.plusDays(1).toString(), templateTomorrow),
        todaySessions = sessions,
        todayAdjustments = adjustments,
    )
}
