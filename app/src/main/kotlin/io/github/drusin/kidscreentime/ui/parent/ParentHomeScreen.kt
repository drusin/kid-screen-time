package io.github.drusin.kidscreentime.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.api.ChildStatus
import io.github.drusin.kidscreentime.api.ParentChildSnapshot
import io.github.drusin.kidscreentime.ui.minutesText
import io.github.drusin.kidscreentime.ui.signedMinutesText
import io.github.drusin.kidscreentime.ui.stateWord
import io.github.drusin.kidscreentime.ui.theme.stateColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.drusin.kidscreentime.ui.toMinutesRoundedUp

/** Eltern-Startseite: the Status-Karte of the Kind (or the hint that none is paired yet) and the way to pair a device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(viewModel: ParentHomeViewModel, onOpenWeeklyTemplate: () -> Unit, onOpenSettings: () -> Unit, onOpenHistory: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    val menuLabel = stringResource(R.string.settings_menu)
                    TextButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = menuLabel }) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { menuOpen = false; onOpenSettings() })
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.offline) OfflineBar(onRetry = viewModel::refresh)
            if (state.showNoChildHint) {
                Text(stringResource(R.string.parent_home_no_child_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.parent_home_no_child_hint), style = MaterialTheme.typography.bodyLarge)
            } else {
                state.children.forEach { StatusCard(it, state.timeZone, state.knownSinceEpochMillis) }
                if (state.child?.status != null) AdjustTodayCard(state, viewModel)
                state.child?.let { RecentDaysCard(it.recentDays, state.canAct, state.offline, onOpenHistory) }
                state.child?.let { WeeklyTemplateRow(it, onOpenWeeklyTemplate) }
            }
        }
    }
}

/** Status-Karte: remaining time, progress bar and the breakdown of the Tageskontingent, as the server calculated it. */
@Composable
private fun StatusCard(child: ParentChildSnapshot, timeZone: String?, knownSince: Long?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(child.name, style = MaterialTheme.typography.titleLarge)
            val status = child.status
            if (status == null) {
                Text(stringResource(R.string.parent_home_unavailable), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            val color = stateColor(status.state)
            Text(
                if (status.state == ChildState.OVERRUN) stringResource(R.string.parent_status_overrun, minutesText(Duration.ofMillis(status.overrunMillis).toMinutesRoundedUp()))
                else stringResource(R.string.parent_status_remaining, minutesText(Duration.ofMillis(status.remainingMillis).toMinutesRoundedUp())),
                style = MaterialTheme.typography.headlineMedium,
                color = color,
            )
            Text(stateWord(status.state), style = MaterialTheme.typography.titleMedium, color = color)
            val running = status.runningSession
            if (running != null && timeZone != null) {
                Text(
                    stringResource(R.string.parent_status_running_since, DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY).format(Instant.ofEpochMilli(running.startEpochMillis).atZone(ZoneId.of(timeZone)))),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            LinearProgressIndicator(progress = { status.usedFraction() }, color = color, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.parent_status_used, minutesText(Duration.ofMillis(status.usedMillis).toMinutesRoundedUp()), minutesText(status.allowance.totalMinutes)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.parent_status_breakdown,
                    minutesText(status.allowance.templateMinutes),
                    signedMinutesText(status.allowance.adjustmentMinutes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (knownSince != null) {
                val zone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
                Text(
                    stringResource(R.string.parent_status_known_since, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Instant.ofEpochMilli(knownSince).atZone(zone))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The bar "Offline · nur Ansicht" on top of every Eltern-Bildschirm while the server cannot be reached; [onRetry] tries again. */
@Composable
internal fun OfflineBar(onRetry: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.offline_bar),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** "Heute anpassen": Stepper (±5 min), Vorschau des neuen Tageskontingents, Buchen und "Letzte zurücknehmen". */
@Composable
private fun AdjustTodayCard(state: ParentHomeUiState, viewModel: ParentHomeViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.parent_adjust_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { viewModel.stepAdjustment(-ParentHomeViewModel.ADJUSTMENT_STEP) }, enabled = state.canAct) {
                    Text(stringResource(R.string.parent_adjust_minus))
                }
                Text(signedMinutesText(state.pendingAdjustment), style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = { viewModel.stepAdjustment(ParentHomeViewModel.ADJUSTMENT_STEP) }, enabled = state.canAct) {
                    Text(stringResource(R.string.parent_adjust_plus))
                }
            }
            state.previewAllowanceMinutes?.let {
                Text(stringResource(R.string.parent_adjust_preview, minutesText(it)), style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = viewModel::bookAdjustment, enabled = state.canBook) { Text(stringResource(R.string.parent_adjust_book)) }
            state.undoableAdjustmentMinutes?.let {
                TextButton(onClick = viewModel::undoLastAdjustment, enabled = state.canAct) {
                    Text(stringResource(R.string.parent_adjust_undo, signedMinutesText(it)))
                }
            }
            if (state.actionFailed) {
                Text(stringResource(R.string.parent_adjust_failed), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Share of the Tageskontingent already used, 0..1. A Kontingent of 0 counts as fully used. */
internal fun ChildStatus.usedFraction(): Float =
    if (allowance.totalMinutes == 0) 1f else (usedMillis / (allowance.totalMinutes * 60_000f)).coerceIn(0f, 1f)
