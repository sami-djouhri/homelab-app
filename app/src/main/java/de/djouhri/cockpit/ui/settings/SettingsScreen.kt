package de.djouhri.cockpit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.cockpit.data.local.SettingsStore
import de.djouhri.cockpit.data.repository.PairingRepository
import de.djouhri.cockpit.ui.theme.StatusDown
import de.djouhri.cockpit.ui.theme.StatusUp
import de.djouhri.cockpit.ui.update.UpdateViewModel
import de.djouhri.cockpit.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val deviceId: String? = null,
    val connectionResult: String? = null,
    val connectionOk: Boolean = false,
    val checkingConnection: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    val requireActionConfirm: StateFlow<Boolean> = settingsStore.requireActionConfirm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        viewModelScope.launch {
            _state.update { it.copy(deviceId = settingsStore.getDeviceId()) }
        }
    }

    fun setRequireActionConfirm(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setRequireActionConfirm(enabled) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.update { it.copy(checkingConnection = true, connectionResult = null, error = null) }
            pairingRepository.testConnection().fold(
                onSuccess = { status ->
                    _state.update { it.copy(checkingConnection = false, connectionOk = true, connectionResult = "Erreichbar ($status)") }
                },
                onFailure = { error ->
                    _state.update { it.copy(checkingConnection = false, connectionOk = false, connectionResult = error.userMessage()) }
                },
            )
        }
    }

    fun unpair() {
        viewModelScope.launch { pairingRepository.unpair() }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    val requireActionConfirm by viewModel.requireActionConfirm.collectAsState()
    var confirmUnpair by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Gerät", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Gekoppelt · ID ${state.deviceId?.take(12) ?: "–"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    enabled = !state.checkingConnection,
                ) { Text(if (state.checkingConnection) "Teste…" else "Verbindung testen") }
                state.connectionResult?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.connectionOk) StatusUp else StatusDown,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Version", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    updateState.installierteVersion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Die App sieht alle sechs Stunden selbst nach und meldet sich oben, " +
                        "wenn etwas bereitliegt. Hier kann man es erzwingen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { updateViewModel.pruefe() },
                    enabled = !updateState.prueftGerade,
                ) { Text(if (updateState.prueftGerade) "Prüfe…" else "Jetzt nachsehen") }

                val update = updateState.angeboten
                when {
                    update != null -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Neu: ${update.versionName} (${update.versionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusUp,
                        )
                        update.changelog.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        update.sha256.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "SHA-256: $it",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        // Die Knoepfe sind dieselben wie im Banner oben, und sie
                        // teilen sich dessen Zustand: was hier geladen wird, gilt
                        // dort als geladen.
                        when {
                            updateState.laedtGerade ->
                                Text("Wird geladen…", style = MaterialTheme.typography.bodySmall)
                            updateState.bereit != null ->
                                Button(onClick = { updateViewModel.installieren() }) { Text("Installieren") }
                            else ->
                                Button(onClick = { updateViewModel.holen() }) { Text("Herunterladen") }
                        }
                        updateState.fehler?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = StatusDown, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    updateState.geprueft -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            updateState.fehler ?: "Aktuell.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateState.fehler != null) StatusDown else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sicherheit", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Schreibende Aktionen (Start/Stop/Restart) zusätzlich per " +
                                "Biometrie oder Geräte-PIN bestätigen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = requireActionConfirm,
                        onCheckedChange = { viewModel.setRequireActionConfirm(it) },
                    )
                }
            }
        }

        state.error?.let {
            Text(it, color = StatusDown, style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedButton(
            onClick = { confirmUnpair = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Gerät entkoppeln") }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Entkoppeln?") },
            text = { Text("JWT und Zertifikate werden gelöscht. Ein erneutes Pairing ist danach nötig.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    viewModel.unpair()
                }) { Text("Entkoppeln") }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Abbrechen") } },
        )
    }
}
