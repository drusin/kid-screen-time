package io.github.drusin.kidscreentime.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.drusin.kidscreentime.AppContainer
import io.github.drusin.kidscreentime.api.DeviceRole
import io.github.drusin.kidscreentime.pairing.DeviceState
import io.github.drusin.kidscreentime.ui.pairing.PairDeviceScreen
import io.github.drusin.kidscreentime.ui.pairing.PairDeviceViewModel
import io.github.drusin.kidscreentime.ui.pairing.RevokedScreen
import io.github.drusin.kidscreentime.ui.child.ChildRoot
import io.github.drusin.kidscreentime.ui.parent.ParentRoot

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
            scanner = container.qrScannerFactory(LocalContext.current),
        )
        DeviceState.Revoked -> RevokedScreen(onAcknowledge = container.pairingRepository::acknowledgeRevoked)
        is DeviceState.Paired -> when (state.device.role) {
            DeviceRole.PARENT -> ParentRoot(container)
            DeviceRole.CHILD -> ChildRoot(container)
        }
    }
}
