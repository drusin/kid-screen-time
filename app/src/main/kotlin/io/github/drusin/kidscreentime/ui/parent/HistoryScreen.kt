package io.github.drusin.kidscreentime.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.api.HistoryAdjustment
import io.github.drusin.kidscreentime.api.HistoryDay
import io.github.drusin.kidscreentime.api.HistorySession
import io.github.drusin.kidscreentime.api.RecentDay
import io.github.drusin.kidscreentime.api.SessionEndReason
import io.github.drusin.kidscreentime.ui.minutesText
import io.github.drusin.kidscreentime.ui.signedMinutesText
import io.github.drusin.kidscreentime.ui.toMinutesRoundedUp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMANY)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

private fun zoneOf(timeZone: String?): ZoneId = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC

private fun dayText(day: String): String = runCatching { dayFormat.format(LocalDate.parse(day)) }.getOrDefault(day)

private fun timeText(epochMillis: Long, zone: ZoneId): String = timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/**
 * "Letzte Tage" on the Startseite: one line per day, "genutzt / Kontingent", the Überziehung marked, and the link to the
 * full Verlauf. The Verlauf needs the server: while the Elterngerät is [offline] the link is off and a hint says why.
 */
@Composable
internal fun RecentDaysCard(days: List<RecentDay>, canAct: Boolean, offline: Boolean, onOpenHistory: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.history_recent_title), style = MaterialTheme.typography.titleMedium)
            if (days.isEmpty()) {
                Text(stringResource(R.string.history_recent_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            days.forEach { RecentDayRow(it) }
            if (offline) {
                Text(stringResource(R.string.history_needs_server), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenHistory, enabled = canAct) { Text(stringResource(R.string.history_open)) }
        }
    }
}

@Composable
private fun RecentDayRow(day: RecentDay) {
    val overrun = day.overrunMillis > 0
    Column {
        Text(
            stringResource(
                R.string.history_day_line,
                dayText(day.day),
                minutesText(Duration.ofMillis(day.usedMillis).toMinutesRoundedUp()),
                minutesText(day.allowance.totalMinutes),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = if (overrun) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (overrun) {
            Text(
                stringResource(R.string.history_overrun, minutesText(Duration.ofMillis(day.overrunMillis).toMinutesRoundedUp())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The full Verlauf: per day the Sitzungen (split at midnight, with the marks) and the Tagesanpassungen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, timeZone: String?, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = zoneOf(timeZone)
    BackHandler(onBack = onClose)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.unavailable) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfflineBar(onRetry = viewModel::load)
                        Text(stringResource(R.string.history_needs_server), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (state.rejected) item { Text(stringResource(R.string.history_unknown_child), color = MaterialTheme.colorScheme.error) }
            if (state.loading) item { Text(stringResource(R.string.history_loading), style = MaterialTheme.typography.bodyLarge) }
            items(state.days, key = { it.day }) { HistoryDayCard(it, zone) }
        }
    }
}

@Composable
private fun HistoryDayCard(day: HistoryDay, zone: ZoneId) {
    val overrun = day.overrunMillis > 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dayText(day.day), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.history_used_of,
                    minutesText(Duration.ofMillis(day.usedMillis).toMinutesRoundedUp()),
                    minutesText(day.allowance.totalMinutes),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (overrun) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (overrun) {
                Text(
                    stringResource(R.string.history_overrun, minutesText(Duration.ofMillis(day.overrunMillis).toMinutesRoundedUp())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.parent_status_breakdown, minutesText(day.allowance.templateMinutes), signedMinutesText(day.allowance.adjustmentMinutes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (day.sessions.isEmpty() && day.adjustments.isEmpty()) {
                Text(stringResource(R.string.history_no_sessions), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            day.sessions.forEach { SessionRow(it, zone) }
            day.adjustments.forEach { AdjustmentRow(it, zone) }
        }
    }
}

@Composable
private fun SessionRow(session: HistorySession, zone: ZoneId) {
    val end = session.endEpochMillis
    val from = if (session.fromPreviousDay) stringResource(R.string.history_midnight) else timeText(session.startEpochMillis, zone)
    val to = when {
        end == null -> stringResource(R.string.history_running)
        session.continuesNextDay -> stringResource(R.string.history_midnight)
        else -> timeText(end, zone)
    }
    val marks = buildList {
        if (session.fromPreviousDay) add(stringResource(R.string.history_from_previous_day))
        if (session.continuesNextDay) add(stringResource(R.string.history_continues))
        if (session.endReason == SessionEndReason.AUTOMATIC_STOP) add(stringResource(R.string.history_automatic_stop))
        if (session.recordedOffline) add(stringResource(R.string.history_recorded_offline))
    }
    Column {
        Text(
            stringResource(R.string.history_session_line, from, to, minutesText(Duration.ofMillis(session.durationMillis).toMinutesRoundedUp())),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (marks.isNotEmpty()) {
            Text(
                marks.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (session.endReason == SessionEndReason.AUTOMATIC_STOP) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdjustmentRow(adjustment: HistoryAdjustment, zone: ZoneId) {
    val note = when {
        adjustment.takenBack -> stringResource(R.string.history_adjustment_taken_back)
        adjustment.reversal -> stringResource(R.string.history_adjustment_reversal)
        else -> null
    }
    Text(
        stringResource(R.string.history_adjustment_line, signedMinutesText(adjustment.deltaMinutes), timeText(adjustment.bookedAtEpochMillis, zone)) +
            (note?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        color = if (adjustment.takenBack) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}
