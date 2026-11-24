package io.github.drusin.kidscreentime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.drusin.kidscreentime.R

/** Placeholder start screen. The real pairing flow comes with ticket 03. */
@Composable
fun PairDeviceScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            Text(text = stringResource(R.string.pair_device_title), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.pair_device_hint), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
