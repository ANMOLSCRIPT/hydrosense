package com.hydrosense.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hydrosense.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class StatusStyle(val label: String, val icon: ImageVector, val color: Color, val soft: Color)

/** Colour + icon + label for each HydroSense monitoring state (never colour alone). */
@Composable
fun statusStyle(status: String): StatusStyle {
    val c = LocalStatusColors.current
    return when (status) {
        "stable" -> StatusStyle("Stable", Icons.Outlined.CheckCircle, c.ok, c.okSoft)
        "attention" -> StatusStyle("Needs attention", Icons.Outlined.Visibility, c.watch, c.watchSoft)
        "unusual" -> StatusStyle("Unusual change", Icons.Outlined.Waves, c.change, c.changeSoft)
        "alert" -> StatusStyle("Active alert", Icons.Outlined.WarningAmber, c.danger, c.dangerSoft)
        "learning" -> StatusStyle("Still learning", Icons.Outlined.HourglassEmpty, c.idle, c.idleSoft)
        else -> StatusStyle("Waiting for data", Icons.Outlined.HelpOutline, c.idle, c.idleSoft)
    }
}

fun severityStatus(severity: String) = when (severity) { "high" -> "alert"; "medium" -> "unusual"; else -> "attention" }
fun riskStatus(risk: String) = when (risk) { "elevated" -> "alert"; "potential_stress" -> "unusual"; "watch" -> "attention"; "stable" -> "stable"; else -> "learning" }

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier, label: String? = null) {
    val s = statusStyle(status)
    Row(
        modifier.clip(CircleShape).background(s.soft).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(s.icon, null, Modifier.size(14.dp), tint = s.color)
        Text(label ?: s.label, style = MaterialTheme.typography.labelMedium, color = s.color, maxLines = 1)
    }
}

@Composable
fun StatusDot(status: String, size: Dp = 44.dp) {
    val s = statusStyle(status)
    Box(Modifier.size(size).clip(CircleShape).background(s.color), contentAlignment = Alignment.Center) {
        Icon(s.icon, null, Modifier.size(size * 0.5f), tint = Color.White)
    }
}

@Composable
fun DemoTag(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Outlined.Science, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Demo data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun HCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    val base = modifier.fillMaxWidth()
    Surface(
        modifier = if (onClick != null) base.clip(shape).clickable(role = Role.Button, onClick = onClick) else base,
        shape = shape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp,
    ) { Column(Modifier.padding(padding), content = content) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    Column(modifier) {
        if (eyebrow != null) Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Progressive disclosure: simple by default, detail on demand. */
@Composable
fun Disclosure(title: String, modifier: Modifier = Modifier, initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    Surface(modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Outlined.ExpandMore, if (open) "Collapse" else "Expand", Modifier.rotate(if (open) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(open) { Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = content) }
        }
    }
}

@Composable
fun SkeletonCard(height: Dp = 96.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun MessageState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) =
    MessageState(Icons.Outlined.CloudOff, "We couldn't load this right now", message, modifier, "Try again", onRetry)

/** Shown when the screen is displaying cached data because the network is unavailable. */
@Composable
fun OfflineBanner(visible: Boolean) {
    AnimatedVisibility(visible) {
        Row(Modifier.fillMaxWidth().background(LocalStatusColors.current.watchSoft).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = LocalStatusColors.current.watch)
            Text("You're offline. Showing the last data saved on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

const val DISCLAIMER = "Important: HydroSense is an environmental monitoring and decision-support prototype. TDS is only one indicator and cannot by itself determine overall water quality, pollution, ecosystem health, or drinking-water safety. HydroSense alerts indicate changes that may warrant further observation or field verification."

@Composable
fun Tile(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = valueColor, maxLines = 1)
            if (unit != null) { Spacer(Modifier.width(4.dp)); Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp)) }
        }
    }
}

/** Re-runs [block] every [intervalMs] while the calling screen is on screen. */
@Composable
fun Poll(key: Any?, intervalMs: Long, block: suspend () -> Unit) {
    LaunchedEffect(key, intervalMs) {
        while (true) { block(); delay(intervalMs) }
    }
}

// ---- formatting -----------------------------------------------------------
fun parseInstant(iso: String?): Instant? = iso?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

fun timeAgo(iso: String?): String {
    val t = parseInstant(iso) ?: return "never"
    val s = Duration.between(t, Instant.now()).seconds.coerceAtLeast(0)
    fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        s < 45 -> "just now"
        s < 3600 -> plural((s / 60.0).roundToInt().toLong().coerceAtLeast(1), "minute")
        s < 86400 -> plural((s / 3600.0).roundToInt().toLong(), "hour")
        s < 86400 * 14 -> plural((s / 86400.0).roundToInt().toLong(), "day")
        else -> DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault()).format(t)
    }
}

fun dateTime(iso: String?): String = parseInstant(iso)?.let { DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault()).format(it) } ?: "—"
fun ppm(v: Double?): String = v?.roundToInt()?.toString() ?: "—"
fun signed(v: Double?, digits: Int = 0): String = v?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + String.format("%.${digits}f", kotlin.math.abs(it)) } ?: "—"

/** What a citizen reported, as friendly chips (same wording as the web app). */
fun observationChips(clarity: String, algae: String, waste: String, odor: String, life: String): List<Pair<String, Boolean>> {
    val chips = mutableListOf<Pair<String, Boolean>>() // text, isConcern
    when (clarity) { "clear" -> chips += "💧 Clear water" to false; "slightly_cloudy" -> chips += "🌫 Slightly cloudy" to true; "very_cloudy" -> chips += "🌁 Very cloudy" to true }
    if (algae == "yes") chips += "🌿 Algae reported" to true
    if (waste == "yes") chips += "🗑 Floating waste" to true
    if (odor == "mild") chips += "👃 Mild smell" to true
    if (odor == "strong") chips += "👃 Strong smell" to true
    if (life == "yes") chips += "🐟 Aquatic life seen" to false
    if (chips.isEmpty()) chips += "📝 Observation shared" to false
    return chips
}
