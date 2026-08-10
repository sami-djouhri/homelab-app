package de.djouhri.cockpit.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.cockpit.BuildConfig
import de.djouhri.cockpit.data.local.SettingsStore
import de.djouhri.cockpit.data.model.cockpit.AppVersion
import de.djouhri.cockpit.data.repository.DashboardRepository
import de.djouhri.cockpit.data.repository.PairingRepository
import de.djouhri.cockpit.security.SessionState
import de.djouhri.cockpit.ui.theme.StatusDown
import de.djouhri.cockpit.ui.theme.StatusUp
import de.djouhri.cockpit.util.UpdateUrl
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
    val checkingUpdate: Boolean = false,
    val updateAvailable: AppVersion? = null,
    val updateChecked: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val dashboardRepository: DashboardRepository,
    private val settingsStore: SettingsStore,
    private val sessionState: SessionState,
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

    /**
     * Loest die APK-URL gegen das gekoppelte Gateway auf und validiert die
     * Herkunft (siehe [UpdateUrl]). Fremd-Hosts / Schema-Downgrades werden
     * abgelehnt, bevor die URL ans System uebergeben wird.
     */
    fun resolveUpdateUrl(apkUrl: String): Result<String> =
        UpdateUrl.resolve(sessionState.gatewayBaseUrl(), apkUrl)

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

    fun checkUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(checkingUpdate = true, error = null, updateChecked = false) }
            dashboardRepository.latestVersion().fold(
                onSuccess = { version ->
                    val newer = version.versionCode > BuildConfig.VERSION_CODE
                    _state.update {
                        it.copy(
                            checkingUpdate = false,
                            updateChecked = true,
                            updateAvailable = if (newer) version else null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(checkingUpdate = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun unpair() {
        viewModelScope.launch { pairingRepository.unpair() }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val requireActionConfirm by viewModel.requireActionConfirm.collectAsState()
    val context = LocalContext.current
    var confirmUnpair by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Geraet", style = MaterialTheme.typography.titleMedium)
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
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { viewModel.checkUpdate() },
                    enabled = !state.checkingUpdate,
                ) { Text(if (state.checkingUpdate) "Pruefe…" else "Auf Updates pruefen") }

                val update = state.updateAvailable
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
                        Button(onClick = {
                            updateError = null
                            viewModel.resolveUpdateUrl(update.apkUrl).fold(
                                onSuccess = { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                onFailure = { updateError = "Update abgelehnt: ${it.message}" },
                            )
                        }) { Text("Update herunterladen") }
                        updateError?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = StatusDown, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.updateChecked -> {
                        Spacer(Modifier.height(6.dp))
                        Text("Aktuell.", style = MaterialTheme.typography.bodySmall)
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
                            "Schreibende Aktionen (Start/Stop/Restart) zusaetzlich per " +
                                "Biometrie oder Geraete-PIN bestaetigen.",
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
        ) { Text("Geraet entkoppeln") }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Entkoppeln?") },
            text = { Text("JWT und Zertifikate werden geloescht. Ein erneutes Pairing ist danach noetig.") },
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
