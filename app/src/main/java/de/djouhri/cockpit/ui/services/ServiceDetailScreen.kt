package de.djouhri.cockpit.ui.services

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.cockpit.data.local.SettingsStore
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import de.djouhri.cockpit.data.repository.OpsRepository
import de.djouhri.cockpit.ui.components.StatusDot
import de.djouhri.cockpit.ui.components.serviceStatusColor
import de.djouhri.cockpit.ui.security.rememberDeviceAuth
import de.djouhri.cockpit.ui.theme.StatusDown
import de.djouhri.cockpit.ui.theme.StatusUp
import de.djouhri.cockpit.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceDetailUiState(
    val host: String = "",
    val name: String = "",
    val loading: Boolean = true,
    val actionInProgress: String? = null,
    val status: String? = null,
    val health: String? = null,
    val image: String? = null,
    val logs: String = "",
    val lines: Int = 200,
    val error: String? = null,
    val message: String? = null,
) {
    val service: ServiceSummary
        get() = ServiceSummary(host = host, name = name, status = status ?: "unknown", health = health)
}

@HiltViewModel
class ServiceDetailViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ServiceDetailUiState())
    val state = _state.asStateFlow()

    /** Ob schreibende Aktionen zusaetzlich lokal bestaetigt werden muessen. */
    val requireConfirm: StateFlow<Boolean> = settingsStore.requireActionConfirm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var initialized = false

    fun init(host: String, name: String) {
        if (initialized) return
        initialized = true
        _state.update { it.copy(host = host, name = name) }
        refresh()
    }

    fun refresh() {
        val host = _state.value.host
        val name = _state.value.name
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val svc = opsRepository.services(host).getOrNull()?.firstOrNull { it.name == name }
            val logsResult = opsRepository.logs(host, name, _state.value.lines)
            _state.update {
                it.copy(
                    loading = false,
                    status = svc?.status ?: it.status,
                    health = svc?.health,
                    image = svc?.image ?: it.image,
                    logs = logsResult.getOrNull()?.logs ?: it.logs,
                    error = logsResult.exceptionOrNull()?.userMessage(),
                )
            }
        }
    }

    fun setLines(lines: Int) {
        if (lines == _state.value.lines) return
        _state.update { it.copy(lines = lines) }
        refresh()
    }

    fun performAction(action: String) {
        val host = _state.value.host
        val name = _state.value.name
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = action, message = null, error = null) }
            opsRepository.action(host, name, action).fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            actionInProgress = null,
                            status = result.status ?: it.status,
                            message = "Aktion '$action' ausgefuehrt",
                        )
                    }
                    refresh()
                },
                onFailure = { error ->
                    _state.update { it.copy(actionInProgress = null, error = error.userMessage()) }
                },
            )
        }
    }
}

@Composable
fun ServiceDetailScreen(
    host: String,
    name: String,
    viewModel: ServiceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val requireConfirm by viewModel.requireConfirm.collectAsState()
    val deviceAuth = rememberDeviceAuth()
    LaunchedEffect(host, name) { viewModel.init(host, name) }

    var confirm by remember { mutableStateOf<String?>(null) }

    // Einheitlicher Einstieg fuer start/stop/restart: bevorzugt die lokale
    // Geraetebestaetigung (Biometrie/PIN); ist keine verfuegbar, faellt es auf
    // den textuellen Bestaetigungsdialog zurueck.
    val requestAction: (String) -> Unit = { action ->
        if (requireConfirm && deviceAuth?.canAuthenticate() == true) {
            deviceAuth.authenticate(
                title = "Aktion bestaetigen",
                subtitle = "'$action' fuer '${state.name}'",
                onSuccess = { viewModel.performAction(action) },
            )
        } else {
            confirm = action
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = serviceStatusColor(state.service))
                    Spacer(Modifier.width(10.dp))
                    Text(state.name, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append(state.status ?: "unbekannt")
                        state.health?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Host: ${state.host}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.image?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Aktionen
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Aktionen", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (state.actionInProgress != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Fuehre '${state.actionInProgress}' aus…")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { requestAction("start") }) { Text("Start") }
                        OutlinedButton(onClick = { requestAction("restart") }) { Text("Restart") }
                        OutlinedButton(onClick = { requestAction("stop") }) { Text("Stop") }
                    }
                }
                state.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = StatusUp, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Logs
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Logs", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    listOf(100, 200, 500).forEach { n ->
                        FilterChip(
                            selected = state.lines == n,
                            onClick = { viewModel.setLines(n) },
                            label = { Text("$n") },
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.loading && state.logs.isBlank()) {
                    Text("Lade Logs…", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        state.logs.ifBlank { "(keine Logs)" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }

        state.error?.let {
            Text(it, color = StatusDown, style = MaterialTheme.typography.bodyMedium)
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${action.replaceFirstChar { it.uppercase() }}?") },
            text = { Text("Container '${state.name}' wirklich $action?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    viewModel.performAction(action)
                }) { Text("Ja") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Abbrechen") }
            },
        )
    }
}
