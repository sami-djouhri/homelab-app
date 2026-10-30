package de.djouhri.tardis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.local.SettingsStore
import de.djouhri.tardis.security.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class RootTarget { LOADING, PAIRING, TARDIS }

/** Entscheidet, ob die App das Pairing oder das Tardis zeigt (inkl. Demo-Modus). */
@HiltViewModel
class RootViewModel @Inject constructor(
    settingsStore: SettingsStore,
    demoModeManager: DemoModeManager,
    sessionState: SessionState,
) : ViewModel() {

    val target: StateFlow<RootTarget> =
        combine(settingsStore.isPaired, demoModeManager.active) { paired, demo ->
            when {
                demo || paired -> RootTarget.TARDIS
                else -> RootTarget.PAIRING
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootTarget.LOADING)

    val demoActive: StateFlow<Boolean> = demoModeManager.active

    val sessionExpired: StateFlow<Boolean> = sessionState.sessionExpired
}
