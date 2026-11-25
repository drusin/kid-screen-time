package io.github.drusin.kidscreentime.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.api.ParentChildSnapshot
import io.github.drusin.kidscreentime.ui.minutesText
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Moves [minutes] by [delta] and keeps the result within 0 to 24 hours. */
internal fun stepped(minutes: Int, delta: Int): Int = (minutes + delta).coerceIn(0, ParentHomeViewModel.MAX_ADJUSTMENT)

/** One row "label, −, value, +" for a value in minutes. */
@Composable
private fun MinutesStepperRow(label: String, minutes: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(stepped(minutes, -ParentHomeViewModel.SETTINGS_STEP)) }, enabled = enabled) { Text(stringResource(R.string.parent_adjust_minus)) }
        Text(minutesText(minutes), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange(stepped(minutes, ParentHomeViewModel.SETTINGS_STEP)) }, enabled = enabled) { Text(stringResource(R.string.parent_adjust_plus)) }
    }
}

/** The row "Wochenvorlage" of the Startseite: shows the template valid tomorrow and opens the full-screen dialog. */
@Composable
internal fun WeeklyTemplateRow(child: ParentChildSnapshot, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.weekly_template_title), style = MaterialTheme.typography.titleMedium)
            val template = child.weeklyTemplateTomorrow ?: child.weeklyTemplate
            if (template != null) {
                Text(
                    DayOfWeek.entries.zip(template.minutes).joinToString(", ") { (day, minutes) -> day.getDisplayName(TextStyle.SHORT, Locale.GERMANY) + " " + minutes },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Full-screen dialog "Wochenvorlage": Mo–So at once; a change is valid from tomorrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyTemplateScreen(viewModel: ParentHomeViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val child = state.child
    val saved = child?.weeklyTemplateTomorrow ?: child?.weeklyTemplate
    var draft by rememberSaveable { mutableStateOf(saved?.minutes?.toIntArray() ?: IntArray(7)) }
    LaunchedEffect(Unit) { viewModel.clearActionFailed() }
    BackHandler(onBack = onClose)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekly_template_title)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.offline) OfflineBar(onRetry = viewModel::refresh)
            Text(stringResource(R.string.weekly_template_from_tomorrow), style = MaterialTheme.typography.bodyLarge)
            DayOfWeek.entries.forEachIndexed { index, day ->
                MinutesStepperRow(day.getDisplayName(TextStyle.FULL, Locale.GERMANY), draft[index], state.canAct) { value ->
                    draft = draft.copyOf().also { it[index] = value }
                    viewModel.clearActionFailed()
                }
            }
            Button(
                onClick = { viewModel.saveWeeklyTemplate(draft.toList(), onDone = onClose) },
                enabled = child != null && state.canAct && saved?.minutes != draft.toList(),
            ) { Text(stringResource(R.string.settings_save)) }
            if (state.actionFailed) Text(stringResource(R.string.parent_adjust_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * "Einstellungen & Geräte" (the ⋮ menu): the rules of the Kind and the "Geräte" section with the Geräteliste (unpair) and
 * the way to pair a further device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentSettingsScreen(viewModel: ParentHomeViewModel, onPairDevice: () -> Unit, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val child = state.child
    LaunchedEffect(Unit) { viewModel.clearActionFailed() }
    BackHandler(onBack = onClose)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.offline) OfflineBar(onRetry = viewModel::refresh)
            if (child != null) RulesSection(child, state.canAct, state.actionFailed, viewModel)
            Text(stringResource(R.string.settings_devices), style = MaterialTheme.typography.titleLarge)
            DevicesSection(state, viewModel)
            Button(onClick = onPairDevice, enabled = state.canAct) { Text(stringResource(R.string.pair_device_title)) }
        }
    }
}

/** The Geräteliste: name, role, Kopplungsdatum, "zuletzt gesehen" and "Entkoppeln" (not for the last Elterngerät). */
@Composable
private fun DevicesSection(state: ParentHomeUiState, viewModel: ParentHomeViewModel) {
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val zone = remember(state.timeZone) { state.timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY) }
    val dateTimeFormat = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    state.devices.forEach { device ->
        val own = device.id == state.ownDeviceId
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    device.name + if (own) " " + stringResource(R.string.devices_this_device) else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (device.role == DeviceRole.PARENT) R.string.devices_role_parent else R.string.devices_role_child),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.devices_paired_at, dateFormat.format(Instant.ofEpochMilli(device.pairedAtEpochMillis).atZone(zone))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    device.lastSeenEpochMillis?.let { stringResource(R.string.devices_last_seen, dateTimeFormat.format(Instant.ofEpochMilli(it).atZone(zone))) }
                        ?: stringResource(R.string.devices_never_seen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.canUnpair(device)) {
                    OutlinedButton(onClick = { confirming = device.id }) { Text(stringResource(R.string.devices_unpair)) }
                } else if (device.role == DeviceRole.PARENT && state.canAct) {
                    Text(stringResource(R.string.devices_last_parent_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (state.actionFailed) Text(stringResource(R.string.parent_adjust_failed), color = MaterialTheme.colorScheme.error)
    val target = state.devices.firstOrNull { it.id == confirming }
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.devices_unpair_confirm_title, target.name)) },
            text = {
                Text(
                    stringResource(
                        when {
                            target.id == state.ownDeviceId -> R.string.devices_unpair_confirm_own
                            target.role == DeviceRole.CHILD -> R.string.devices_unpair_confirm_child
                            else -> R.string.devices_unpair_confirm_other
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { confirming = null; viewModel.unpairDevice(target.id) }) { Text(stringResource(R.string.devices_unpair)) } },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.devices_cancel)) } },
        )
    }
}

@Composable
private fun RulesSection(child: ParentChildSnapshot, enabled: Boolean, failed: Boolean, viewModel: ParentHomeViewModel) {
    // The input starts from the saved rules; a Resync-Signal while typing does not overwrite it.
    var name by rememberSaveable { mutableStateOf(child.name) }
    var warning by rememberSaveable { mutableStateOf(child.warningThresholdMinutes ?: 5) }
    var overrun by rememberSaveable { mutableStateOf(child.overrunLimitMinutes ?: 60) }
    val changed = name.trim() != child.name || warning != child.warningThresholdMinutes || overrun != child.overrunLimitMinutes
    Text(stringResource(R.string.settings_rules), style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it; viewModel.clearActionFailed() },
        label = { Text(stringResource(R.string.settings_child_name)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    MinutesStepperRow(stringResource(R.string.settings_warning_threshold), warning, enabled) { warning = it; viewModel.clearActionFailed() }
    MinutesStepperRow(stringResource(R.string.settings_overrun_limit), overrun, enabled) { overrun = it; viewModel.clearActionFailed() }
    Text(stringResource(R.string.settings_rules_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = { viewModel.saveRules(name, warning, overrun) }, enabled = changed && name.isNotBlank() && enabled) {
        Text(stringResource(R.string.settings_save))
    }
    if (failed) Text(stringResource(R.string.parent_adjust_failed), color = MaterialTheme.colorScheme.error)
}
