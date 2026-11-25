package io.github.drusin.kidscreentime.ui.parent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.drusin.kidscreentime.AppContainer

/**
 * The screens of a Elterngerät: the Startseite and, on top of it, "Gerät koppeln", "Einstellungen & Geräte" and the "Wochenvorlage" dialog. There is no NavHost: a flag
 * opens the pairing screen, and every opening gets a fresh ViewModel through the counter in its key.
 */
@Composable
fun ParentRoot(container: AppContainer) {
    val home = viewModel<ParentHomeViewModel>(factory = viewModelFactory {
        initializer { ParentHomeViewModel(container.parentRepository, container.pushHandler.signals) }
    })
    var pairingOpenings by rememberSaveable { mutableIntStateOf(0) }
    var pairingOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var templateOpen by rememberSaveable { mutableStateOf(false) }
    if (pairingOpen) {
        PairNewDeviceScreen(
            viewModel = viewModel<PairNewDeviceViewModel>(key = "pair-new-device-$pairingOpenings", factory = viewModelFactory {
                initializer { PairNewDeviceViewModel(container.parentRepository, container.monotonicClock) }
            }),
            onClose = {
                pairingOpen = false
                home.refresh()
            },
        )
    } else if (settingsOpen) {
        ParentSettingsScreen(home, onClose = { settingsOpen = false }, onPairDevice = {
            pairingOpenings++
            pairingOpen = true
        })
    } else if (templateOpen) {
        WeeklyTemplateScreen(home, onClose = { templateOpen = false })
    } else {
        ParentHomeScreen(home, onOpenWeeklyTemplate = { templateOpen = true }, onOpenSettings = { settingsOpen = true })
    }
}
