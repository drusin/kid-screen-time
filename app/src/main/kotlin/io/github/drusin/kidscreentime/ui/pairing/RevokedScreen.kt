package io.github.drusin.kidscreentime.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.drusin.kidscreentime.R

/** Shown after the server entkoppelt this device (`401`): says why the app stopped working, then leads back to "Gerät koppeln". */
@Composable
fun RevokedScreen(onAcknowledge: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.revoked_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.revoked_hint), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onAcknowledge) { Text(stringResource(R.string.revoked_pair_again)) }
        }
    }
}
