package io.github.drusin.kidscreentime.ui.child

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.drusin.kidscreentime.AppContainer
import io.github.drusin.kidscreentime.alarm.DebugTools

/**
 * The screens of a Kindgerät: the home screen and, on top of it, the Einrichtungs-Checkliste (symbol in the app bar or
 * tap on the red banner). No NavHost; a flag opens the Checkliste. Every return to the app (also from the system
 * settings the Checkliste links to) syncs, which checks the setup again and tells the server.
 */
@Composable
fun ChildRoot(container: AppContainer) {
    val home = viewModel<ChildHomeViewModel>(factory = viewModelFactory {
        initializer { ChildHomeViewModel(container.childRepository, container.wallClock, container.setupChecklist) }
    })
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { home.onResume() }
    var checklistOpen by rememberSaveable { mutableStateOf(false) }
    if (checklistOpen) {
        BackHandler { checklistOpen = false }
        SetupChecklistScreen(
            viewModel<SetupChecklistViewModel>(factory = viewModelFactory { initializer { SetupChecklistViewModel(container.setupChecklist) } }),
            onClose = { checklistOpen = false },
        )
    } else {
        ChildHomeScreen(home, onOpenChecklist = { checklistOpen = true }, debugTools = { DebugTools(container) })
    }
}
