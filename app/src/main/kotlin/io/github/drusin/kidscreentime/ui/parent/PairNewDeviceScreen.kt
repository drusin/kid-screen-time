package io.github.drusin.kidscreentime.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.ui.EverySecond
import io.github.drusin.kidscreentime.ui.QrCode
import io.github.drusin.kidscreentime.ui.toClockText

/** "Gerät koppeln" at the Elterngerät: role, (Kind name), then the Kopplungscode as QR code and text with countdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairNewDeviceScreen(viewModel: PairNewDeviceViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EverySecond(viewModel::tick)
    val back = { if (state.role != null && state.code == null) viewModel.backToRoleChoice() else onClose() }
    BackHandler(onBack = back)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_new_device_title)) },
                navigationIcon = { TextButton(onClick = back) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val code = state.code
            when {
                state.loading -> CircularProgressIndicator()
                state.unavailable -> Text(stringResource(R.string.parent_home_unavailable), color = MaterialTheme.colorScheme.error)
                code != null -> CodeView(code, viewModel, onClose)
                state.role == null -> RoleChoice(state, viewModel)
                else -> ChildDetails(state, viewModel)
            }
            state.error?.let {
                Text(
                    stringResource(if (it == CreateCodeError.REJECTED) R.string.pair_new_device_rejected else R.string.pair_new_device_unavailable),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RoleChoice(state: PairNewDeviceUiState, viewModel: PairNewDeviceViewModel) {
    Text(stringResource(R.string.pair_new_device_choose_role), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
    Button(onClick = { viewModel.onRoleSelected(DeviceRole.CHILD) }, modifier = Modifier.fillMaxWidth()) {
        Text(state.child?.let { stringResource(R.string.pair_new_device_child_device_for, it.name) } ?: stringResource(R.string.pair_new_device_child_device))
    }
    state.replacesDeviceName?.let { ReplaceHint(it) }
    OutlinedButton(onClick = { viewModel.onRoleSelected(DeviceRole.PARENT) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pair_new_device_parent_device))
    }
}

@Composable
private fun ChildDetails(state: PairNewDeviceUiState, viewModel: PairNewDeviceViewModel) {
    if (state.needsChildName) {
        Text(stringResource(R.string.pair_new_device_child_name_hint), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = state.newChildName,
            onValueChange = viewModel::onNewChildNameChange,
            label = { Text(stringResource(R.string.pair_new_device_child_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            stringResource(R.string.pair_new_device_child_device_for, state.child?.name.orEmpty()),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.replacesDeviceName?.let { ReplaceHint(it) }
    Button(onClick = viewModel::createCode, enabled = state.canCreateChildCode, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pair_new_device_create_code))
    }
}

@Composable
private fun ReplaceHint(deviceName: String) {
    Text(
        stringResource(R.string.pair_new_device_replace_hint, deviceName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeView(code: ShownCode, viewModel: PairNewDeviceViewModel, onClose: () -> Unit) {
    Text(
        if (code.role == DeviceRole.CHILD) stringResource(R.string.pair_new_device_code_for_child, code.childName.orEmpty())
        else stringResource(R.string.pair_new_device_code_for_parent),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth(),
    )
    code.replacesDeviceName?.let { ReplaceHint(it) }
    if (!code.expired) {
        QrCode(code.qrContent, Modifier.widthIn(max = 320.dp))
        Text(code.code, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        Text(stringResource(R.string.pair_new_device_scan_or_type), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.pair_new_device_countdown, code.remaining.toClockText()), style = MaterialTheme.typography.titleMedium)
    } else {
        Text(stringResource(R.string.pair_new_device_expired), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        Button(onClick = viewModel::newCode, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pair_new_device_new_code)) }
    }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
}
