package io.github.drusin.kidscreentime.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.qr.QrScanner
import kotlinx.coroutines.launch

/** The only screen before the Kopplung: "Gerät koppeln". */
@Composable
fun PairDeviceScreen(viewModel: PairDeviceViewModel, scanner: QrScanner) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(text = stringResource(R.string.pair_device_title), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.pair_device_hint), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { scope.launch { viewModel.onQrResult(scanner.scan()) } },
                enabled = !state.submitting && state.deviceName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pair_device_scan))
            }
            Text(text = stringResource(R.string.pair_device_or_type), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                label = { Text(stringResource(R.string.pair_device_server_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                label = { Text(stringResource(R.string.pair_device_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.deviceName,
                onValueChange = viewModel::onDeviceNameChange,
                label = { Text(stringResource(R.string.pair_device_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                Text(text = stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = viewModel::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.submitting) R.string.pair_device_submitting else R.string.pair_device_submit))
            }
        }
    }
}


private fun PairError.messageRes(): Int = when (this) {
    PairError.INVALID_SERVER_URL -> R.string.pair_error_invalid_server_url
    PairError.INVALID_CODE -> R.string.pair_error_invalid_code
    PairError.RATE_LIMITED -> R.string.pair_error_rate_limited
    PairError.SERVER_UNREACHABLE -> R.string.pair_error_server_unreachable
    PairError.CHILD_HAS_DEVICE -> R.string.pair_error_child_has_device
    PairError.INVALID_QR -> R.string.pair_error_invalid_qr
    PairError.SCANNER_UNAVAILABLE -> R.string.pair_error_scanner_unavailable
    PairError.UNEXPECTED -> R.string.pair_error_unexpected
}
