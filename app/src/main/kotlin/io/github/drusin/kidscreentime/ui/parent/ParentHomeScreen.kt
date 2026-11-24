package io.github.drusin.kidscreentime.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import io.github.drusin.kidscreentime.api.ChildState
import io.github.drusin.kidscreentime.api.ChildStatus
import io.github.drusin.kidscreentime.api.ParentChildSnapshot
import io.github.drusin.kidscreentime.ui.minutesText
import io.github.drusin.kidscreentime.ui.signedMinutesText
import io.github.drusin.kidscreentime.ui.stateWord
import io.github.drusin.kidscreentime.ui.theme.stateColor
import java.time.Duration
import io.github.drusin.kidscreentime.ui.toMinutesRoundedUp

/** Eltern-Startseite: the Status-Karte of the Kind (or the hint that none is paired yet) and the way to pair a device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(viewModel: ParentHomeViewModel, onPairDevice: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { TextButton(onClick = onPairDevice) { Text(stringResource(R.string.pair_device_title)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.showNoChildHint) {
                Text(stringResource(R.string.parent_home_no_child_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.parent_home_no_child_hint), style = MaterialTheme.typography.bodyLarge)
            } else {
                state.children.forEach { StatusCard(it) }
            }
            if (state.unavailable) {
                Text(stringResource(R.string.parent_home_unavailable), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Status-Karte: remaining time, progress bar and the breakdown of the Tageskontingent, as the server calculated it. */
@Composable
private fun StatusCard(child: ParentChildSnapshot) {
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
        }
    }
}

/** Share of the Tageskontingent already used, 0..1. A Kontingent of 0 counts as fully used. */
internal fun ChildStatus.usedFraction(): Float =
    if (allowance.totalMinutes == 0) 1f else (usedMillis / (allowance.totalMinutes * 60_000f)).coerceIn(0f, 1f)
