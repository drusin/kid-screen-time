package io.github.drusin.kidscreentime.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.drusin.kidscreentime.R

/** Eltern-Startseite, still empty: only the hint that no Kind is paired yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(viewModel: ParentHomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.showNoChildHint) {
                Text(stringResource(R.string.parent_home_no_child_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.parent_home_no_child_hint), style = MaterialTheme.typography.bodyLarge)
            } else {
                state.children.forEach { Text(it.name, style = MaterialTheme.typography.titleLarge) }
            }
            if (state.unavailable) {
                Text(stringResource(R.string.parent_home_unavailable), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
