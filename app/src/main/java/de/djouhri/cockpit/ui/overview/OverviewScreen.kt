package de.djouhri.cockpit.ui.overview

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.cockpit.data.model.cockpit.HostHealth
import de.djouhri.cockpit.data.model.cockpit.InboxCounts
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import de.djouhri.cockpit.data.repository.DashboardRepository
import de.djouhri.cockpit.data.repository.OpsRepository
import de.djouhri.cockpit.ui.components.ErrorMessage
import de.djouhri.cockpit.ui.components.StatusDot
import de.djouhri.cockpit.ui.components.usageColor
import de.djouhri.cockpit.ui.theme.StatusDown
import de.djouhri.cockpit.ui.theme.StatusUp
import de.djouhri.cockpit.ui.theme.StatusWarn
import de.djouhri.cockpit.util.formatBytes
import de.djouhri.cockpit.util.formatPercent
import de.djouhri.cockpit.util.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OverviewUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val services: List<ServiceSummary> = emptyList(),
    val health: HostHealth? = null,
    val inbox: InboxCounts = InboxCounts(),
) {
    val total: Int get() = services.size
    val up: Int get() = services.count { it.isRunning && !it.isUnhealthy }
    val problems: List<ServiceSummary>
        get() = services.filter { !it.isRunning || it.isUnhealthy }
            .sortedBy { it.name }
    val hosts: List<String> get() = services.map { it.host }.distinct().sorted()
}

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OverviewUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = !refresh && it.services.isEmpty(),
                    refreshing = refresh,
                    error = null,
                )
            }
            val (servicesResult, summaryResult) = coroutineScope {
                val a = async { opsRepository.services() }
                val b = async { dashboardRepository.summary() }
                a.await() to b.await()
            }

            servicesResult.fold(
                onSuccess = { services ->
                    val summary = summaryResult.getOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            services = services,
                            health = summary?.health,
                            inbox = summary?.inboxCounts ?: it.inbox,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = error.userMessage(),
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenServices: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("Lade Status…") }
            }
            state.error != null && state.services.isEmpty() -> {
                ErrorMessage(message = state.error!!, onRetry = { viewModel.load() })
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ContainerRollupCard(state, onOpenServices)
                    state.health?.let { HostMetricsCard(it) }
                    InboxSummaryCard(state.inbox)
                }
            }
        }
    }
}

@Composable
private fun ContainerRollupCard(state: OverviewUiState, onOpenServices: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Container", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.up} / ${state.total} laufen",
                style = MaterialTheme.typography.headlineMedium,
                color = if (state.problems.isEmpty()) StatusUp else StatusWarn,
            )
            Text(
                "Hosts: ${state.hosts.joinToString(", ").ifBlank { "–" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.problems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Auffaellig (${state.problems.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusDown,
                )
                Spacer(Modifier.height(4.dp))
                state.problems.take(6).forEach { svc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(color = if (svc.isUnhealthy) StatusWarn else StatusDown)
                        Spacer(Modifier.width(8.dp))
                        Text(svc.name, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            svc.health ?: svc.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.problems.size > 6) {
                    Text(
                        "… und ${state.problems.size - 6} weitere",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HostMetricsCard(health: HostHealth) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Host-System", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            MetricRow("CPU", health.cpuPercent.formatPercent(), usageColor(health.cpuPercent))
            val memLabel = if (health.memoryUsed != null && health.memoryTotal != null) {
                "${health.memoryUsed.formatBytes()} / ${health.memoryTotal.formatBytes()}"
            } else {
                health.memoryPercent.formatPercent()
            }
            MetricRow("RAM", memLabel, usageColor(health.memoryPercent))
            MetricRow("Disk", health.diskPercent.formatPercent(), usageColor(health.diskPercent))
            health.cpuTemp?.let { MetricRow("Temp", "%.0f °C".format(it), usageColor(it)) }
            val load = listOfNotNull(health.load1m, health.load5m, health.load15m)
            if (load.isNotEmpty()) {
                MetricRow(
                    "Load",
                    load.joinToString(" ") { "%.2f".format(it) },
                    MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = valueColor,
        )
    }
}

@Composable
private fun InboxSummaryCard(counts: InboxCounts) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Inbox", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Offen: ${counts.open}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "Zurueckgestellt: ${counts.snoozed}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
