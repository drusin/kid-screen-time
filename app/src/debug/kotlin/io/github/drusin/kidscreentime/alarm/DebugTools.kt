package io.github.drusin.kidscreentime.alarm

import android.content.Context
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import io.github.drusin.kidscreentime.AppContainer
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.domain.bildschirmzeit.KindStand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Sitzung
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Wochenvorlage
import io.github.drusin.kidscreentime.domain.bildschirmzeit.WochenvorlageVersion
import io.github.drusin.kidscreentime.domain.clock.WallClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

fun createTestSession(context: Context, wallClock: WallClock): TestSession = DebugTestSession(context, wallClock)

/**
 * A Sitzung with a Tageskontingent of 2 minutes (Vorwarnschwelle 1 minute) that runs from the moment of the tap, kept in
 * SharedPreferences. It replaces the real stand until it is stopped, so the whole alarm path can be tried out in a few
 * minutes without the server.
 */
class DebugTestSession(context: Context, private val wallClock: WallClock) : TestSession {
    private val prefs = context.getSharedPreferences("debug_test_session", Context.MODE_PRIVATE)

    override val active: Boolean get() = prefs.contains(KEY_START)

    fun start() {
        prefs.edit().putLong(KEY_START, wallClock.now().toEpochMilli()).apply()
    }

    override fun stop() {
        prefs.edit().clear().apply()
    }

    override fun stand(now: Instant): KindStand? {
        val start = prefs.getLong(KEY_START, NONE).takeIf { it != NONE }?.let(Instant::ofEpochMilli) ?: return null
        // A time zone in which the start is exactly 12:00, so midnight never cuts the 2 minutes.
        val secondOfDay = start.epochSecond.mod(86_400L)
        val zone = ZoneOffset.ofTotalSeconds((12 * 3600 - secondOfDay).toInt())
        return KindStand(
            zeitzone = zone,
            wochenvorlage = Wochenvorlage(listOf(WochenvorlageVersion.gleichbleibend(LocalDate.of(2000, 1, 1), 2))),
            sitzungen = listOf(Sitzung(TEST_SESSION_ID, start)),
            vorwarnschwelle = Duration.ofMinutes(1),
        )
    }

    private companion object {
        const val KEY_START = "start"
        const val NONE = Long.MIN_VALUE
        const val TEST_SESSION_ID = "test-session"
    }
}

/** The hidden test button of the debug build: a small text button at the bottom of the Kindgerät screen. */
@Composable
fun DebugTools(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val session = container.testSession as? DebugTestSession ?: return
    TextButton(onClick = {
        session.start()
        scope.launch { container.alarmKern.replan() }
    }) {
        Text(stringResource(R.string.debug_test_session))
    }
}
