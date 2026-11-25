package io.github.drusin.kidscreentime.ui.child

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.api.SetupItem
import io.github.drusin.kidscreentime.push.PushSetup
import io.github.drusin.kidscreentime.setup.SetupChecklist
import io.github.drusin.kidscreentime.setup.SetupChecklistState
import io.github.drusin.kidscreentime.setup.SetupHint
import io.github.drusin.kidscreentime.setup.SetupSettings
import io.github.drusin.kidscreentime.ui.hintRes
import io.github.drusin.kidscreentime.ui.labelRes
import kotlinx.coroutines.flow.StateFlow

/** State of the Einrichtungs-Checkliste screen: what is missing and which hints were ticked off. The check itself runs with every sync. */
class SetupChecklistViewModel(private val checklist: SetupChecklist) : ViewModel() {
    val state: StateFlow<SetupChecklistState> = checklist.state

    fun setHintChecked(hint: SetupHint, checked: Boolean) = checklist.setHintChecked(hint, checked)
}

/**
 * The Einrichtungs-Checkliste: every prüfbare Punkt with its state (missing ones red with the link into the system
 * settings), below the hints to tick off. Coming back from the settings, `ChildRoot` syncs, which checks again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupChecklistScreen(viewModel: SetupChecklistViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)
            SetupItem.entries.forEach { item ->
                val missing = item in state.missing
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(item.labelRes()), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (missing) R.string.setup_item_missing else R.string.setup_item_ok),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(item.hintRes()), style = MaterialTheme.typography.bodyMedium)
                        if (missing) {
                            if (item == SetupItem.PUSH_DISTRIBUTOR) {
                                Button(onClick = { activity?.let(PushSetup::register) }) { Text(stringResource(R.string.setup_choose_distributor)) }
                            } else {
                                Button(onClick = { SetupSettings.open(context, item) }) { Text(stringResource(R.string.setup_open_settings)) }
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.setup_hints_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.setup_hints_intro), style = MaterialTheme.typography.bodyMedium)
            SetupHint.entries.forEach { hint ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = hint in state.checkedHints, onCheckedChange = { viewModel.setHintChecked(hint, it) })
                    Text(
                        stringResource(
                            when (hint) {
                                SetupHint.NEVER_SLEEPING_APPS -> R.string.setup_hint_never_sleeping_apps
                                SetupHint.DO_NOT_DISTURB_ALARMS -> R.string.setup_hint_do_not_disturb_alarms
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
