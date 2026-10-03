package com.hydrosense.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hydrosense.app.ui.screens.AboutScreen
import com.hydrosense.app.ui.screens.AlertsScreen
import com.hydrosense.app.ui.screens.AnalyticsScreen
import com.hydrosense.app.ui.screens.ExploreScreen
import com.hydrosense.app.ui.screens.HomeScreen
import com.hydrosense.app.ui.screens.ReportScreen
import com.hydrosense.app.ui.screens.SiteScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("explore", "Explore", Icons.Outlined.Map, Icons.Filled.Map),
    Tab("report", "Report", Icons.Outlined.Edit, Icons.Outlined.Edit),
    Tab("alerts", "Alerts", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    Tab("about", "About", Icons.Outlined.Info, Icons.Filled.Info),
)

/** Navigation actions shared by every screen. */
class Nav(private val controller: NavHostController) {
    fun tab(route: String) = controller.navigate(route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun site(id: String) = controller.navigate("site/$id")
    /** Opens a site after a finished report, clearing the report flow so its tab starts fresh next time. */
    fun siteAfterReport(id: String) = controller.navigate("site/$id") { popUpTo(controller.graph.findStartDestination().id) }
    fun analytics(id: String) = controller.navigate("analytics/$id")
    fun explore(siteId: String? = null) = controller.navigate(if (siteId != null) "explore?site=$siteId" else "explore") { launchSingleTop = true }
    fun report(siteId: String? = null) = controller.navigate(if (siteId != null) "report?site=$siteId" else "report") { launchSingleTop = true }
    fun back() { controller.popBackStack() }
}

@Composable
fun AppRoot() {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) } }
    val entry by controller.currentBackStackEntryAsState()
    val current = entry?.destination?.route?.substringBefore("?")?.substringBefore("/") ?: "home"
    val tabRoute = when (current) { "site", "analytics" -> "explore"; else -> current }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                TABS.forEach { tab ->
                    val selected = tabRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.tab(tab.route) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null, Modifier.size(24.dp)) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(controller, startDestination = "home") {
                composable("home") { HomeScreen(nav) }
                composable("explore?site={site}", arguments = listOf(navArgument("site") { type = NavType.StringType; nullable = true })) {
                    ExploreScreen(nav, it.arguments?.getString("site"))
                }
                composable("site/{id}") { SiteScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("analytics/{id}") { AnalyticsScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("report?site={site}", arguments = listOf(navArgument("site") { type = NavType.StringType; nullable = true })) {
                    ReportScreen(nav, it.arguments?.getString("site"), notify)
                }
                composable("alerts") { AlertsScreen(nav) }
                composable("about") { AboutScreen(nav, notify) }
            }
        }
    }
}
