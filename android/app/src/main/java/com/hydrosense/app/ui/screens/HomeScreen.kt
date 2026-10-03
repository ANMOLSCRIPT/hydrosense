package com.hydrosense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hydrosense.app.data.Site
import com.hydrosense.app.ui.HomeViewModel
import com.hydrosense.app.ui.Nav
import com.hydrosense.app.ui.components.*
import com.hydrosense.app.ui.repository
import com.hydrosense.app.ui.theme.Aqua400
import com.hydrosense.app.ui.theme.LocalStatusColors
import com.hydrosense.app.ui.theme.Navy900
import com.hydrosense.app.ui.theme.Navy950

val STATUS_ORDER = listOf("alert", "unusual", "attention", "stable", "learning", "no_data")
fun pollInterval(mode: String) = if (mode == "live") 6_000L else 30_000L

@Composable
fun BrandMark(size: Int = 40) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3).dp)).background(Navy900), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.WaterDrop, null, Modifier.size((size * 0.55).dp), tint = Aqua400)
    }
}

@Composable
fun ModeChip(mode: String, onClick: () -> Unit) {
    val live = mode == "live"
    val color = if (live) LocalStatusColors.current.ok else MaterialTheme.colorScheme.primary
    Row(
        Modifier.clip(CircleShape).background(if (live) LocalStatusColors.current.okSoft else MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (live) Icons.Outlined.Sensors else Icons.Outlined.Science, null, Modifier.size(16.dp), tint = color)
        Text(if (live) "Live" else "Demo data", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun HomeScreen(nav: Nav) {
    val repo = repository()
    val vm: HomeViewModel = viewModel { HomeViewModel(repo) }
    val mode by repo.mode.collectAsStateWithLifecycle()
    val stats by vm.stats.state.collectAsStateWithLifecycle()
    val sites by vm.sites.state.collectAsStateWithLifecycle()
    val alerts by vm.alerts.state.collectAsStateWithLifecycle()
    val pending by repo.pending.collectAsStateWithLifecycle()
    Poll(mode, pollInterval(mode)) { vm.refresh() }

    Column(Modifier.fillMaxSize()) {
        OfflineBanner(sites.offline)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.width(10.dp))
                Text("HydroSense", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                ModeChip(mode) { nav.tab("about") }
            }

            // Hero
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Navy900).padding(22.dp)) {
                Text("Sense. Understand. Act.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("See how the water near you is doing, and share what you notice. Sensors and citizens, one clear picture.",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9DCE8))
                Spacer(Modifier.height(18.dp))
                Button(onClick = { nav.tab("explore") }, Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Aqua400, contentColor = Navy950)) {
                    Icon(Icons.Outlined.Map, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Explore Water", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { nav.tab("report") }, Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Report an Observation", style = MaterialTheme.typography.titleSmall)
                }
            }

            if (pending.isNotEmpty()) {
                HCard(onClick = { nav.tab("about") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.CloudSync, null, tint = LocalStatusColors.current.watch)
                        Column { Text("${pending.size} report${if (pending.size == 1) "" else "s"} waiting to send", style = MaterialTheme.typography.titleSmall)
                            Text("Saved on this device. They will be sent when you're back online.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            // Snapshot
            val s = stats.data
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Monitoring sites", s?.sites?.toString() ?: "…", Modifier.weight(1f))
                    Tile("Active sensors", s?.activeSensors?.toString() ?: "…", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Community observations", s?.observations?.toString() ?: "…", Modifier.weight(1f))
                    Tile("Potential anomalies", s?.potentialAnomalies?.toString() ?: "…", Modifier.weight(1f),
                        valueColor = if ((s?.potentialAnomalies ?: 0) > 0) LocalStatusColors.current.change else MaterialTheme.colorScheme.onSurface)
                }
            }

            // Sites
            SectionTitle("How the water is doing", eyebrow = "Monitoring sites")
            when {
                sites.data != null -> {
                    val sorted = sites.data!!.sortedBy { STATUS_ORDER.indexOf(it.status) }
                    if (sorted.isEmpty()) MessageState(Icons.Outlined.Map, "No sites yet", "Monitoring sites will appear here.")
                    sorted.forEach { SiteRow(it) { nav.site(it.id) } }
                }
                sites.error != null -> ErrorState(sites.error!!, onRetry = { nav.tab("home") })
                else -> repeat(3) { SkeletonCard(84.dp) }
            }

            // Alerts
            val active = alerts.data.orEmpty().filter { it.status == "active" }
            if (active.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionTitle("Changes worth knowing about", Modifier.weight(1f), eyebrow = "Recent alerts")
                    TextButton(onClick = { nav.tab("alerts") }) { Text("See all") }
                }
                active.take(2).forEach { a ->
                    HCard(onClick = { nav.tab("alerts") }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatusDot(severityStatus(a.severity), 40.dp)
                            Column {
                                Text(a.title, style = MaterialTheme.typography.titleSmall)
                                Text(a.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(timeAgo(a.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // What HydroSense does
            SectionTitle("What HydroSense does", eyebrow = "In short")
            HCard {
                Explain(Icons.Outlined.Sensors, "Measures", "A low-cost sensor in the water takes readings around the clock.")
                Spacer(Modifier.height(12.dp))
                Explain(Icons.Outlined.Groups, "Listens", "People nearby add what they can see: colour, algae, waste, wildlife.")
                Spacer(Modifier.height(12.dp))
                Explain(Icons.Outlined.Insights, "Explains", "It compares new information with what is normal for each place and flags sustained changes.")
            }
            Disclaimer()
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Explain(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column { Text(title, style = MaterialTheme.typography.titleSmall); Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SiteRow(site: Site, onClick: () -> Unit) {
    HCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusDot(site.status)
            Column(Modifier.weight(1f)) {
                Text(site.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(site.locality.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(site.headline, style = MaterialTheme.typography.labelMedium, color = statusStyle(site.status).color)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(ppm(site.latestTds), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                Text("ppm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Open ${site.name}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
