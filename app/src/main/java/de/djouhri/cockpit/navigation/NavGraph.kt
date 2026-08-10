package de.djouhri.cockpit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.djouhri.cockpit.ui.inbox.InboxScreen
import de.djouhri.cockpit.ui.overview.OverviewScreen
import de.djouhri.cockpit.ui.services.ServiceDetailScreen
import de.djouhri.cockpit.ui.services.ServicesScreen
import de.djouhri.cockpit.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object OverviewRoute
@Serializable object ServicesRoute
@Serializable object InboxRoute
@Serializable object SettingsRoute
@Serializable data class ServiceDetailRoute(val host: String, val name: String)

private data class TopLevelDest(
    val label: String,
    val route: Any,
    val icon: ImageVector,
)

private val topLevelDests = listOf(
    TopLevelDest("Uebersicht", OverviewRoute, Icons.Filled.Dashboard),
    TopLevelDest("Dienste", ServicesRoute, Icons.Filled.Storage),
    TopLevelDest("Inbox", InboxRoute, Icons.Filled.Inbox),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitMainScreen(
    demoActive: Boolean = false,
    sessionExpired: Boolean = false,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isTopLevel = topLevelDests.any { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
    }

    val title = when {
        currentDestination?.hasRoute(SettingsRoute::class) == true -> "Einstellungen"
        currentDestination?.hasRoute(ServiceDetailRoute::class) == true ->
            backStackEntry?.toRoute<ServiceDetailRoute>()?.name ?: "Dienst"
        currentDestination?.hierarchy?.any { it.hasRoute(ServicesRoute::class) } == true -> "Dienste"
        currentDestination?.hierarchy?.any { it.hasRoute(InboxRoute::class) } == true -> "Inbox"
        else -> "Cockpit"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                        }
                    }
                },
                actions = {
                    if (isTopLevel) {
                        IconButton(onClick = {
                            navController.navigate(SettingsRoute) { launchSingleTop = true }
                        }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    topLevelDests.forEach { dest ->
                        NavigationBarItem(
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(dest.route::class)
                            } == true,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (demoActive) {
                CockpitBanner(
                    text = "DEMO - Beispieldaten, keine Verbindung zum Gateway",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (sessionExpired && !demoActive) {
                CockpitBanner(
                    text = "Sitzung abgelaufen - bitte in den Einstellungen neu koppeln",
                    container = MaterialTheme.colorScheme.errorContainer,
                    onContainer = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            NavHost(
                navController = navController,
                startDestination = OverviewRoute,
            ) {
                composable<OverviewRoute> {
                    OverviewScreen(
                        onOpenServices = {
                            navController.navigate(ServicesRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable<ServicesRoute> {
                    ServicesScreen(
                        onOpenDetail = { host, name ->
                            navController.navigate(ServiceDetailRoute(host, name))
                        },
                    )
                }
                composable<ServiceDetailRoute> { entry ->
                    val route = entry.toRoute<ServiceDetailRoute>()
                    ServiceDetailScreen(host = route.host, name = route.name)
                }
                composable<InboxRoute> {
                    InboxScreen()
                }
                composable<SettingsRoute> {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun CockpitBanner(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
) {
    Surface(color = container, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = onContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
