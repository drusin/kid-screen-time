package io.github.drusin.kidscreentime.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.ui.EverySecond
import io.github.drusin.kidscreentime.ui.theme.stateColor
import io.github.drusin.kidscreentime.ui.toClockText
import io.github.drusin.kidscreentime.domain.bildschirmzeit.Zustand

/** "Zeit ist um!" with the Überziehung, the button "Stopp" and "Ton aus · klingelt in 5 min wieder". Closes itself when the Alarm is over. */
@Composable
fun AlarmScreen(viewModel: AlarmViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EverySecond(viewModel::tick)
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    val red = stateColor(Zustand.UEBERZIEHUNG)
    Surface(modifier = Modifier.fillMaxSize(), color = red, contentColor = Color.White) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
            state.stand?.let {
                Text(
                    stringResource(R.string.alarm_overrun, it.ueberziehung.toClockText()),
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            Button(
                onClick = viewModel::stop,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = red),
            ) {
                Text(stringResource(R.string.child_action_stop), style = MaterialTheme.typography.headlineMedium)
            }
            OutlinedButton(onClick = viewModel::silenceOnce, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text(stringResource(R.string.alarm_silence), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}
