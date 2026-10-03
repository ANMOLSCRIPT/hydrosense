package com.hydrosense.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hydrosense.app.BuildConfig
import com.hydrosense.app.data.BADGES
import com.hydrosense.app.ui.AboutViewModel
import com.hydrosense.app.ui.Nav
import com.hydrosense.app.ui.components.*
import com.hydrosense.app.ui.repository

@Composable
fun AboutScreen(nav: Nav, notify: (String) -> Unit) {
    val repo = repository()
    val vm: AboutViewModel = viewModel { AboutViewModel(repo) }
    val context = LocalContext.current
    val mode by repo.mode.collectAsStateWithLifecycle()
    val pending by repo.pending.collectAsStateWithLifecycle()
    val contributions by repo.contributions.collectAsStateWithLifecycle()
    val health by vm.health.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.health.load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandMark(52)
            Column {
                Text("HydroSense", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                Text("Sense. Understand. Act.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("HydroSense combines low-cost water sensing and citizen observations to help communities understand changes in urban freshwater environments.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Data source toggle: the same Demo / Live switch as the web app.
        HCard {
            Text("Data source", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
                listOf(Triple("demo", "Demo Mode", Icons.Outlined.Science), Triple("live", "Live Mode", Icons.Outlined.Sensors)).forEach { (key, label, icon) ->
                    val on = mode == key
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable(role = Role.RadioButton) { if (!on) { repo.setMode(key); notify(if (key == "demo") "Demo Mode: using simulated data." else "Live Mode: connected to the HydroSense sensor network.") } }
                        .padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(18.dp), tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(if (mode == "demo") "Using simulated data, not real environmental measurements." else "Connected to the HydroSense sensor network. Readings sent by sensors appear here within seconds.",
                Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (pending.isNotEmpty()) HCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("${pending.size} report${if (pending.size == 1) "" else "s"} waiting to send", style = MaterialTheme.typography.titleSmall)
                    Text(pending.joinToString { it.siteName }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { vm.retryPending { sent -> notify(if (sent > 0) "$sent report${if (sent == 1) "" else "s"} sent." else "Still offline. We'll keep trying.") } }) { Text("Send now") }
            }
        }

        HCard {
            Text("Your contribution", style = MaterialTheme.typography.titleMedium)
            Text("$contributions water observation${if (contributions == 1) "" else "s"} shared from this device", Modifier.padding(top = 4.dp, bottom = 10.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipFlow(BADGES.map { "${it.emoji} ${it.name}" to (contributions < it.at) })
            Text("Kept only on this device. No account is needed.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle("Monitoring states")
        HCard {
            listOf("stable" to "Readings match the usual pattern.", "attention" to "A modest or slow drift from usual.", "unusual" to "A clear, sustained change from usual.", "alert" to "A large, sustained change from usual.").forEach { (s, text) ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusDot(s, 34.dp)
                    Column { Text(statusStyle(s).label, style = MaterialTheme.typography.titleSmall); Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Text("These are HydroSense monitoring states, not certified water-safety classifications.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle("How it works")
        Disclosure("What the sensor measures") {
            Text("Each HydroSense node is an ESP32 with a TDS probe. TDS (total dissolved solids) estimates how much dissolved material is in the water, in parts per million. The node filters its samples and sends one reading over Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Disclosure("How a change is detected") {
            Text("Every site is compared only with its own history. HydroSense looks at how far readings are from the site's baseline, whether that is beyond normal variation, whether it has lasted across several readings, and how quickly it happened. A single odd reading is never enough.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Disclosure("AI-assisted interpretation") {
            Text("Assessments come from HydroSense's rule-based engine, which combines sensor evidence with citizen observations from the last 72 hours. " +
                (if (health.data?.llmEnhancement == true) "A language model rewrites the explanation in plain words; it cannot change the result."
                else "The optional language-model wording is not enabled on this server, so explanations are generated by fixed rules.") +
                " This is decision support, not a certified diagnosis.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Disclosure("Privacy") {
            Text("No account is needed. When you submit an observation we store the site, your answers, an optional comment and an optional photo. Observations and photos are public. Location is used only on this device to sort nearby sites and is never sent.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Disclaimer()

        HCard(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.API_BASE_URL))) } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HydroSense on the web", style = MaterialTheme.typography.titleSmall)
                    Text(BuildConfig.API_BASE_URL.removePrefix("https://").trimEnd('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text("Version ${BuildConfig.VERSION_NAME} · Server ${health.data?.let { "${it.status} (${it.store})" } ?: "checking…"}\nMap data © OpenStreetMap contributors · Manrope font, SIL Open Font License\nBuilt for the OneAquaHealth IEEE Hackathon",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
    }
}
