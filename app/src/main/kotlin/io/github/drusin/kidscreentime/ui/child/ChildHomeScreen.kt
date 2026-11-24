package io.github.drusin.kidscreentime.ui.child

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Tagesstand
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand
import io.github.drusin.kidscreentime.ui.EverySecond
import io.github.drusin.kidscreentime.ui.minutesText
import io.github.drusin.kidscreentime.ui.signedMinutesText
import io.github.drusin.kidscreentime.ui.stateWord
import io.github.drusin.kidscreentime.ui.theme.stateColor
import io.github.drusin.kidscreentime.ui.toClockText
import io.github.drusin.kidscreentime.ui.toMinutesRoundedUp
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Kindgerät screen, Variante A "Ring": greeting, ring with remaining time and state word, Tageskontingent, used time. No settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildHomeScreen(viewModel: ChildHomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EverySecond(viewModel::tick)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(state.childName?.let { stringResource(R.string.child_greeting, it) } ?: stringResource(R.string.app_name))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            val stand = state.stand
            when {
                stand != null -> {
                    Ring(stand)
                    Allowance(stand)
                }
                state.loading -> CircularProgressIndicator()
                else -> {
                    Text(stringResource(R.string.child_no_snapshot), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Button(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
                }
            }
            if (state.offline && stand != null) {
                Text(
                    stringResource(R.string.child_offline_notice, state.snapshotAt?.let(::formatTime).orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Ring(stand: Tagesstand) {
    val color = stateColor(stand.zustand)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val total = stand.tageskontingent.gesamt.toMillis()
    val progress = if (total == 0L) 0f else (stand.restzeit.toMillis().toFloat() / total).coerceIn(0f, 1f)
    val overrun = stand.zustand == Zustand.UEBERZIEHUNG
    Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 24.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, if (overrun) 360f else 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (overrun) "+" + stand.ueberziehung.toClockText() else stand.restzeit.toClockText(),
                style = MaterialTheme.typography.displayLarge,
            )
            Text(text = stateWord(stand.zustand), style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}

@Composable
private fun Allowance(stand: Tagesstand) {
    val allowance = stand.tageskontingent
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.child_allowance_today, minutesText(allowance.gesamt.toMinutesRoundedUp())),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(
                R.string.child_allowance_breakdown,
                minutesText(allowance.vorlage.toMinutesRoundedUp()),
                signedMinutesText(allowance.anpassungen.toMinutes().toInt()),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.child_used_today, minutesText(stand.verbrauch.toMinutesRoundedUp())),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun formatTime(at: ZonedDateTime): String = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY).format(at)
