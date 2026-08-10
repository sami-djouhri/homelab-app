package de.djouhri.cockpit.ui.services

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import de.djouhri.cockpit.data.repository.OpsRepository
import de.djouhri.cockpit.ui.components.ErrorMessage
import de.djouhri.cockpit.ui.components.StatusDot
import de.djouhri.cockpit.ui.components.serviceStatusColor
import de.djouhri.cockpit.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServicesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val services: List<ServiceSummary> = emptyList(),
)

/** Ein flaches Anzeige-Element: entweder eine Host-Ueberschrift oder ein Container. */
sealed interface ServiceRow {
    data class Header(val host: String, val up: Int, val total: Int) : ServiceRow
    data class Item(val service: ServiceSummary) : ServiceRow
}

private fun ServicesUiState.rows(): List<ServiceRow> =
    services
        .sortedWith(compareBy({ it.host }, { it.name }))
        .groupBy { it.host }
        .flatMap { (host, list) ->
            buildList {
                add(ServiceRow.Header(host, list.count { it.isRunning }, list.size))
                list.forEach { add(ServiceRow.Item(it)) }
            }
        }

@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServicesUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = !refresh && it.services.isEmpty(), refreshing = refresh, error = null)
            }
            opsRepository.services().fold(
                onSuccess = { services ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = null, services = services)
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, refreshing = false, error = error.userMessage()) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    onOpenDetail: (host: String, name: String) -> Unit,
    viewModel: ServicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("Lade Dienste…") }

            state.error != null && state.services.isEmpty() ->
                ErrorMessage(message = state.error!!, onRetry = { viewModel.load() })

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.rows()) { row ->
                    when (row) {
                        is ServiceRow.Header -> Text(
                            "${row.host} · ${row.up}/${row.total}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        is ServiceRow.Item -> ServiceListItem(row.service, onOpenDetail)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceListItem(
    service: ServiceSummary,
    onOpenDetail: (String, String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(service.host, service.name) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = serviceStatusColor(service))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    service.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(service.status)
                        service.health?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
