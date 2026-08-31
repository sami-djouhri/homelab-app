package de.djouhri.cockpit.ui.overview

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.cockpit.data.model.cockpit.HostHealth
import de.djouhri.cockpit.data.model.cockpit.HostRollup
import de.djouhri.cockpit.data.model.cockpit.InboxCounts
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import de.djouhri.cockpit.data.repository.DashboardRepository
import de.djouhri.cockpit.data.repository.OpsRepository
import de.djouhri.cockpit.ui.components.ErrorMessage
import de.djouhri.cockpit.ui.components.StatusDot
import de.djouhri.cockpit.ui.components.serviceStatusColor
import de.djouhri.cockpit.ui.components.usageColor
import de.djouhri.cockpit.ui.theme.StatusDown
import de.djouhri.cockpit.ui.theme.StatusIdle
import de.djouhri.cockpit.ui.theme.StatusUp
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
    val hosts: List<HostRollup> = emptyList(),
    /** Nur die Container, die wirklich auffaellig sind. Nachgeladen, nicht geraten. */
    val probleme: List<ServiceSummary> = emptyList(),
    val health: HostHealth? = null,
    val inbox: InboxCounts = InboxCounts(),
) {
    val erreichbar: List<HostRollup> get() = hosts.filter { it.erreichbar }
    val nichtErreichbar: List<HostRollup> get() = hosts.filterNot { it.erreichbar }

    val gesamt: Int get() = erreichbar.sumOf { it.gesamt ?: 0 }
    val laufend: Int get() = erreichbar.sumOf { it.laufend ?: 0 }
    val bewusstAus: Int get() = erreichbar.sumOf { it.bewusstAus ?: 0 }
    val problemZahl: Int get() = erreichbar.sumOf { it.problemZahl }

    /**
     * Gestoppte Container, ueber die sich nichts sagen laesst (Remote-Hosts
     * fuehren keine control-map-Erwartung). Bewusst eine eigene Zahl statt
     * stillschweigend unter „laeuft" oder „Problem" verbucht.
     */
    val unbewertet: Int get() = erreichbar.sumOf { it.unbewertet ?: 0 }
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
                    loading = !refresh && it.hosts.isEmpty(),
                    refreshing = refresh,
                    error = null,
                )
            }
            val (hostsResult, summaryResult) = coroutineScope {
                val a = async { opsRepository.hosts() }
                val b = async { dashboardRepository.summary() }
                a.await() to b.await()
            }

            hostsResult.fold(
                onSuccess = { hosts ->
                    val summary = summaryResult.getOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            hosts = hosts,
                            health = summary?.health,
                            inbox = summary?.inboxCounts ?: it.inbox,
                        )
                    }
                    ladeProbleme(hosts)
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

    /**
     * Holt die Containerliste nur fuer die Hosts, die im Zaehlstand ueberhaupt
     * etwas melden. Im Normalfall (nichts auffaellig) faellt damit jeder
     * weitere Aufruf weg; vorher zog die Uebersicht bei jedem Aufruf alle
     * Container, um am Ende „0 Probleme" anzuzeigen.
     */
    private fun ladeProbleme(hosts: List<HostRollup>) {
        val betroffen = hosts.filter { it.erreichbar && it.problemZahl > 0 }
        if (betroffen.isEmpty()) {
            _state.update { it.copy(probleme = emptyList()) }
            return
        }
        viewModelScope.launch {
            val listen = coroutineScope {
                betroffen.map { h -> async { opsRepository.services(h.name).getOrNull().orEmpty() } }
                    .map { it.await() }
            }
            _state.update {
                it.copy(probleme = listen.flatten().filter { s -> s.istProblem }.sortedBy { s -> s.name })
            }
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
            state.error != null && state.hosts.isEmpty() -> {
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
                    if (state.probleme.isNotEmpty()) ProblemCard(state.probleme, onOpenServices)
                    state.health?.let { HostMetricsCard(it) }
                    InboxSummaryCard(state.inbox)
                }
            }
        }
    }
}

@Composable
private fun ContainerRollupCard(state: OverviewUiState, onOpenServices: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenServices() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Container", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            // Die grosse Zahl ist der Befundstand, nicht die Laufzahl: „82 von
            // 93" beantwortet nicht, ob etwas zu tun ist. Genau daran hing der
            // alte Fehler, denn elf der elf gemeldeten „Probleme" waren Absicht.
            Text(
                if (state.problemZahl == 0) "nichts auffaellig" else "${state.problemZahl} auffaellig",
                style = MaterialTheme.typography.headlineMedium,
                color = if (state.problemZahl == 0) StatusUp else StatusDown,
            )
            // Die Nebenzahlen stehen bewusst getrennt: „bewusst aus" ist kein
            // Zwischenzustand von „laeuft", und „ohne Erwartung" ist kein
            // Zwischenzustand von „Problem".
            val nebenzahlen = buildList {
                add("${state.gesamt} Container")
                add("${state.laufend} laufen")
                if (state.bewusstAus > 0) add("${state.bewusstAus} bewusst aus")
                if (state.unbewertet > 0) add("${state.unbewertet} ohne Erwartung")
            }
            Text(
                nebenzahlen.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            state.erreichbar.forEach { HostZeile(it) }
            state.nichtErreichbar.forEach { host ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = StatusIdle)
                    Spacer(Modifier.width(8.dp))
                    Text(host.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    // Kein „0/0": ueber einen unerreichbaren Host ist keine
                    // Aussage moeglich, und eine Null waere eine.
                    Text(
                        "nicht erreichbar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HostZeile(host: HostRollup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = if (host.problemZahl == 0) StatusUp else StatusDown)
        Spacer(Modifier.width(8.dp))
        Text(host.name, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        // Kein „82/93": der Bruch las sich als „elf laufen nicht", obwohl alle
        // elf bewusst aus sind. Jede Zahl bekommt hier ihr eigenes Wort.
        val teile = buildList {
            add("${host.laufend ?: 0} laufen")
            host.bewusstAus?.takeIf { it > 0 }?.let { add("$it aus") }
            host.unbewertet?.takeIf { it > 0 }?.let { add("$it offen") }
            if (host.problemZahl > 0) add("${host.problemZahl} auffaellig")
        }
        Text(
            teile.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (host.problemZahl > 0) StatusDown else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProblemCard(probleme: List<ServiceSummary>, onOpenServices: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenServices() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Auffaellig (${probleme.size})",
                style = MaterialTheme.typography.titleMedium,
                color = StatusDown,
            )
            Spacer(Modifier.height(6.dp))
            probleme.take(8).forEach { svc ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = serviceStatusColor(svc))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(svc.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${svc.host} · ${svc.zustandsText()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (probleme.size > 8) {
                Text(
                    "… und ${probleme.size - 8} weitere",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
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
private fun MetricRow(label: String, value: String, valueColor: Color) {
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
