package com.hydrosense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hydrosense.app.data.Alert
import com.hydrosense.app.data.Assessment
import com.hydrosense.app.data.Observation
import com.hydrosense.app.ui.Nav
import com.hydrosense.app.ui.SiteViewModel
import com.hydrosense.app.ui.components.*
import com.hydrosense.app.ui.repository
import com.hydrosense.app.ui.theme.Aqua400
import com.hydrosense.app.ui.theme.LocalStatusColors
import com.hydrosense.app.ui.theme.Navy900
import com.hydrosense.app.ui.theme.Navy950
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt

private val RANGES = listOf("24h" to "24 hours", "7d" to "7 days", "30d" to "30 days")
private val TREND_WORDS = mapOf("increasing" to "Rising", "decreasing" to "Falling", "stable" to "Steady")

@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        trailing()
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun RangePicker(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
        RANGES.forEach { (key, label) ->
            val on = key == selected
            Text(label, Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                .clickable { onSelect(key) }.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SiteScreen(nav: Nav, siteId: String) {
    val repo = repository()
    val vm: SiteViewModel = viewModel(key = "site-$siteId") { SiteViewModel(repo, siteId) }
    val mode by repo.mode.collectAsStateWithLifecycle()
    val site by vm.site.state.collectAsStateWithLifecycle()
    val series by vm.series.state.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val assessment by vm.assessment.state.collectAsStateWithLifecycle()
    val observations by vm.observations.state.collectAsStateWithLifecycle()
    val alerts by vm.alerts.state.collectAsStateWithLifecycle()
    Poll(siteId, pollInterval(mode)) { vm.refresh() }
    val s = site.data

    Column(Modifier.fillMaxSize()) {
        TopBar(s?.name ?: "Water site", nav::back) { if (s?.isDemo == true) DemoTag() }
        OfflineBanner(site.offline)
        if (s == null) {
            if (site.error != null) ErrorState(site.error!!, onRetry = { nav.back() })
            else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { SkeletonCard(150.dp); SkeletonCard(90.dp); SkeletonCard(220.dp) }
            return@Column
        }
        val style = statusStyle(s.status)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(listOfNotNull(s.locality, s.description).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // How is this site doing?
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(style.soft).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("HOW IS THIS SITE DOING?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusDot(s.status)
                    Column {
                        Text(s.citizen?.headline ?: s.headline, style = MaterialTheme.typography.headlineSmall, color = style.color)
                        Text(s.citizen?.summary ?: s.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                s.citizen?.let { c ->
                    Disclosure("Why?") {
                        Text(c.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        c.detail?.let {
                            Spacer(Modifier.height(10.dp))
                            Disclosure("Show data") {
                                Text(it, style = MaterialTheme.typography.titleSmall)
                                Text("ppm measures dissolved solids (TDS). It is one indicator among many.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Disclosure("What should I do?") { Text(c.action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // Latest measurement
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Latest reading", ppm(s.latestTds), Modifier.weight(1f), unit = "ppm")
                Tile("Recent change", TREND_WORDS[s.trend] ?: "—", Modifier.weight(1f), valueColor = style.color)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Updated ${timeAgo(s.latestAt)}" + (s.deviationPercent?.let { if (abs(it) < 5) " · in line with the usual level" else " · ${signed(it)}% compared with usual" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Trend
            HCard {
                Text("How readings have changed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                RangePicker(range, vm::setRange)
                Spacer(Modifier.height(12.dp))
                if (series.data == null && series.loading) SkeletonCard(210.dp)
                else TrendChart(series.data?.points.orEmpty(), range, s.baselineTds)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { nav.analytics(siteId) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Insights, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Detailed analytics")
                }
            }

            assessment.data?.let { AssessmentCard(it) }

            // Recommended action
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Navy900).padding(18.dp)) {
                Text("Recommended action", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(assessment.data?.recommendation ?: s.citizen?.action.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9DCE8))
                Spacer(Modifier.height(14.dp))
                Button(onClick = { nav.report(siteId) }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Aqua400, contentColor = Navy950)) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Report what you see")
                }
            }

            val active = alerts.data.orEmpty().filter { it.status == "active" }
            if (active.isNotEmpty()) {
                SectionTitle("Alerts at this site")
                active.forEach { AlertCard(it, onOpenSite = null) }
            }

            SectionTitle("Citizen observations")
            val obs = observations.data
            when {
                obs == null -> SkeletonCard()
                obs.isEmpty() -> HCard { MessageState(Icons.Outlined.Groups, "No observations here yet", "If you are near this site, a quick report helps everyone understand it better.") }
                else -> obs.forEach { ObservationCard(it, showSite = false) }
            }
            Disclaimer()
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun factorIcon(icon: String): ImageVector = when (icon) {
    "trending-up" -> Icons.AutoMirrored.Outlined.TrendingUp; "trending-down" -> Icons.AutoMirrored.Outlined.TrendingDown
    "leaf" -> Icons.Outlined.Grass; "trash" -> Icons.Outlined.Delete; "cloud" -> Icons.Outlined.Cloud; "wind" -> Icons.Outlined.Air
    else -> Icons.Outlined.ShowChart
}

/** The existing HydroSense assessment, shown as decision support. */
@Composable
fun AssessmentCard(a: Assessment, showEvidence: Boolean = false) {
    HCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("What HydroSense thinks", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        StatusBadge(riskStatus(a.riskLevel), label = a.assessment)
        Spacer(Modifier.height(10.dp))
        Text(a.explanation, style = MaterialTheme.typography.bodyMedium)
        if (showEvidence) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CONFIDENCE", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${a.confidence}%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            }
            LinearProgressIndicator(progress = { a.confidence / 100f }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.primaryContainer)
            Text("How well the available evidence supports this assessment. It is not a probability of pollution.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.factors.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("What contributed?", style = MaterialTheme.typography.titleSmall)
                a.factors.forEach { f ->
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            Icon(factorIcon(f.icon), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column { Text(f.label, style = MaterialTheme.typography.titleSmall); Text(f.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            (if (a.source == "llm") "AI-assisted interpretation: wording by a language model from HydroSense evidence. "
            else "AI-assisted interpretation from HydroSense's rule-based engine, using sensor readings and citizen observations. ") +
                "Decision support only: a potential anomaly is not confirmed pollution.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ObservationCard(o: Observation, showSite: Boolean = true) {
    HCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (showSite) Text(o.siteName, style = MaterialTheme.typography.titleSmall)
                Text(timeAgo(o.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (o.isDemo) DemoTag() else Text("Community", Modifier.clip(CircleShape).background(LocalStatusColors.current.okSoft).padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall, color = LocalStatusColors.current.ok)
        }
        o.imageUrl?.let {
            Spacer(Modifier.height(10.dp))
            AsyncImage(it, "Photo shared by a citizen", Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(10.dp))
        ChipFlow(observationChips(o.waterClarity, o.algae, o.waste, o.odor, o.aquaticLife))
        o.comment?.let { Spacer(Modifier.height(8.dp)); Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(chips: List<Pair<String, Boolean>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { (text, concern) ->
            Text(text, Modifier.clip(CircleShape).background(if (concern) LocalStatusColors.current.watchSoft else MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

fun evidenceText(a: Alert, key: String): String = (a.evidence[key] as? JsonPrimitive)?.content?.takeIf { it != "null" } ?: "—"

@Composable
fun AlertCard(alert: Alert, onOpenSite: (() -> Unit)?) {
    val status = if (alert.status == "resolved") "stable" else severityStatus(alert.severity)
    HCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusDot(status, 40.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(status, label = if (alert.status == "resolved") "Resolved" else statusStyle(status).label)
                    Text(timeAgo(alert.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(alert.title, style = MaterialTheme.typography.titleMedium)
                Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (alert.status == "resolved" && alert.resolutionNote != null)
                    Text("${alert.resolutionNote} · ${timeAgo(alert.resolvedAt)}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.ok)
            }
        }
        Spacer(Modifier.height(12.dp))
        Disclosure("Understand what changed") {
            alert.detail?.let { Text("Why? $it HydroSense compares each reading with what is normal for this particular site.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }
            alert.recommendation?.let { Text("What should I do? $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(10.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Reading", evidenceText(alert, "current_tds").toDoubleOrNull()?.roundToInt()?.toString() ?: "—", Modifier.weight(1f), unit = "ppm")
                Tile("Usual", evidenceText(alert, "baseline_tds").toDoubleOrNull()?.roundToInt()?.toString() ?: "—", Modifier.weight(1f), unit = "ppm")
                Tile("Difference", signed(evidenceText(alert, "deviation_percent").toDoubleOrNull()) + "%", Modifier.weight(1f))
            }
            if (onOpenSite != null) { Spacer(Modifier.height(10.dp)); TextButton(onClick = onOpenSite) { Text("See ${alert.siteName}") } }
        }
    }
}

// ---------------------------------------------------------------------------
// Analytics
// ---------------------------------------------------------------------------
private val PARTS = listOf("magnitude" to ("Size of the change" to 55), "significance" to ("Beyond normal variation" to 15),
    "persistence" to ("How long it has lasted" to 20), "dynamics" to ("Speed of change" to 10))

@Composable
fun AnalyticsScreen(nav: Nav, siteId: String) {
    val repo = repository()
    val vm: SiteViewModel = viewModel(key = "analytics-$siteId") { SiteViewModel(repo, siteId) }
    val mode by repo.mode.collectAsStateWithLifecycle()
    val site by vm.site.state.collectAsStateWithLifecycle()
    val series by vm.series.state.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val analytics by vm.analytics.state.collectAsStateWithLifecycle()
    val assessment by vm.assessment.state.collectAsStateWithLifecycle()
    val alerts by vm.alerts.state.collectAsStateWithLifecycle()
    Poll(siteId, pollInterval(mode)) { vm.refreshAnalytics() }
    val a = analytics.data

    Column(Modifier.fillMaxSize()) {
        TopBar("Analytics · ${site.data?.name ?: ""}", nav::back) { if (site.data?.isDemo == true) DemoTag() }
        OfflineBanner(analytics.offline)
        if (a == null) {
            if (analytics.error != null) ErrorState(analytics.error!!, onRetry = { nav.back() })
            else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { SkeletonCard(90.dp); SkeletonCard(240.dp); SkeletonCard(160.dp) }
            return@Column
        }
        val style = statusStyle(a.state)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Current", ppm(a.currentTds), Modifier.weight(1f), unit = "ppm")
                Tile("Baseline", ppm(a.baselineTds), Modifier.weight(1f), unit = "ppm")
                Tile("Deviation", signed(a.deviationPercent, 1) + "%", Modifier.weight(1f), valueColor = style.color)
            }
            HCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TDS trend", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    StatusBadge(a.state)
                }
                Spacer(Modifier.height(10.dp))
                RangePicker(range, vm::setRange)
                Spacer(Modifier.height(12.dp))
                if (series.data == null && series.loading) SkeletonCard(220.dp)
                else TrendChart(series.data?.points.orEmpty(), range, a.baselineTds, usualBand = false, anomalies = a.anomalies, height = 220.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Trend (7 days)", TREND_WORDS[a.trend] ?: "—", Modifier.weight(1f))
                Tile("Rate of change", signed(a.rateOfChange, 1), Modifier.weight(1f), unit = "ppm/h")
            }
            HCard {
                Text("Anomaly score", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${a.anomalyScore}", style = MaterialTheme.typography.displaySmall, color = style.color)
                    Text(" / 100", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { a.anomalyScore / 100f }, Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), color = style.color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Text("Below 20 stable · 20 to 44 needs attention · 45 to 84 unusual change · 85+ active alert", Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                PARTS.forEach { (key, meta) ->
                    val v = a.scoreParts[key] ?: 0.0
                    Row(Modifier.padding(top = 8.dp)) {
                        Text(meta.first, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${String.format("%.1f", v)} / ${meta.second}", style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(progress = { (v / meta.second).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.primaryContainer)
                }
                Spacer(Modifier.height(12.dp))
                Text("Persistence: ${(a.persistence * 100).roundToInt()}% of recent readings agree. A single noisy reading never raises an alert.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Disclosure("How is this calculated?") { Text(a.methodology, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            assessment.data?.let { AssessmentCard(it, showEvidence = true) }
            SectionTitle("Alert history")
            val list = alerts.data.orEmpty()
            if (list.isEmpty()) HCard { Text("No alerts have been recorded for this site.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            list.forEach { al ->
                HCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(al.title, style = MaterialTheme.typography.titleSmall)
                            Text(dateTime(al.timestamp) + (al.resolvedAt?.let { " → resolved ${dateTime(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusBadge(if (al.status == "resolved") "stable" else severityStatus(al.severity), label = if (al.status == "resolved") "Resolved" else "${al.severity} severity")
                    }
                }
            }
            Disclaimer()
            Spacer(Modifier.height(4.dp))
        }
    }
}
