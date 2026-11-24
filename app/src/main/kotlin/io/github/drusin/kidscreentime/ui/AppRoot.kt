package io.github.drusin.kidscreentime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.drusin.kidscreentime.AppContainer
import io.github.drusin.kidscreentime.R
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.ui.pairing.PairDeviceScreen
import io.github.drusin.kidscreentime.ui.pairing.PairDeviceViewModel
import io.github.drusin.kidscreentime.ui.parent.ParentHomeScreen
import io.github.drusin.kidscreentime.ui.parent.ParentHomeViewModel

/**
 * Navigation: no NavHost. The root screen follows the [DeviceState] (not paired, Elterngerät, Kindgerät), because
 * the role is fixed by the Kopplung. Screens inside a role (later tickets) navigate within their own state.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val deviceState by container.pairingRepository.state.collectAsStateWithLifecycle()
    when (val state = deviceState) {
        DeviceState.Unpaired -> PairDeviceScreen(
            viewModel(factory = viewModelFactory {
                initializer { PairDeviceViewModel(container.pairingRepository, container.defaultDeviceName) }
            }),
        )
        is DeviceState.Paired -> when (state.device.role) {
            DeviceRole.PARENT -> ParentHomeScreen(
                viewModel(factory = viewModelFactory {
                    initializer { ParentHomeViewModel(container.parentRepository) }
                }),
            )
            // The Kindgerät screens come with ticket 04.
            DeviceRole.CHILD -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.child_device_placeholder), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
