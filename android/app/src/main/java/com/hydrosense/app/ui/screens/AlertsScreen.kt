package com.hydrosense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hydrosense.app.ui.AlertsViewModel
import com.hydrosense.app.ui.Nav
import com.hydrosense.app.ui.components.*
import com.hydrosense.app.ui.repository

@Composable
fun AlertsScreen(nav: Nav) {
    val repo = repository()
    val vm: AlertsViewModel = viewModel { AlertsViewModel(repo) }
    val mode by repo.mode.collectAsStateWithLifecycle()
    val state by vm.alerts.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Poll(mode, pollInterval(mode)) { vm.refresh() }
    val all = state.data.orEmpty()
    val active = all.filter { it.status == "active" }
    val resolved = all.filter { it.status == "resolved" }

    Column(Modifier.fillMaxSize()) {
        OfflineBanner(state.offline)
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            SectionTitle("Changes worth knowing about", eyebrow = "Alerts")
            Text("An alert means readings stayed different from what is usual at a site. It is worth a closer look, not confirmed pollution.",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TabRow(tab, containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.padding(top = 8.dp)) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Happening now" + if (active.isNotEmpty()) " (${active.size})" else "") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Resolved" + if (resolved.isNotEmpty()) " (${resolved.size})" else "") })
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val list = if (tab == 0) active else resolved
            when {
                state.data == null && state.error != null -> ErrorState(state.error!!, onRetry = { nav.tab("alerts") })
                state.data == null -> repeat(2) { SkeletonCard(150.dp) }
                list.isEmpty() -> MessageState(Icons.Outlined.NotificationsOff, if (tab == 0) "Nothing unusual right now" else "No resolved alerts yet",
                    if (tab == 0) "All monitored sites are behaving as they usually do." else "When an alert is resolved it will be listed here.")
                else -> list.forEach { a -> AlertCard(a, onOpenSite = { nav.site(a.siteId) }) }
            }
            Disclaimer()
        }
    }
}
