package de.djouhri.cockpit.data.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schaltet den oeffentlichen Offline-Demo-Modus. Ist er aktiv, liefern die
 * Repositories statische Beispieldaten ([DemoData]) und es geht KEIN Request ins
 * Netz - so kann die App ohne Gateway/Pairing (z. B. aus einem Portfolio heraus)
 * durchgeklickt werden. Der Zustand ist bewusst nur im Speicher: ein Neustart
 * fuehrt zurueck auf den Pairing-Screen.
 */
@Singleton
class DemoModeManager @Inject constructor() {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    val isActive: Boolean get() = _active.value

    fun enable() { _active.value = true }
    fun disable() { _active.value = false }
}
