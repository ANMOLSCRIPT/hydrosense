package com.hydrosense.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.hydrosense.app.data.BADGES
import com.hydrosense.app.data.Site
import com.hydrosense.app.ui.Nav
import com.hydrosense.app.ui.ReportDraft
import com.hydrosense.app.ui.ReportViewModel
import com.hydrosense.app.ui.SubmitState
import com.hydrosense.app.ui.components.*
import com.hydrosense.app.ui.repository
import com.hydrosense.app.ui.theme.LocalStatusColors
import java.io.File

private val STEP_TITLES = listOf("Where did you observe it?", "What did you notice?", "Add a photo", "Anything else?", "Review and send")
private val YES_NO = listOf("yes" to "Yes", "no" to "No", "not_sure" to "Not sure")
private data class Question(val emoji: String, val title: String, val options: List<Pair<String, String>>, val get: (ReportDraft) -> String?, val set: (ReportDraft, String) -> ReportDraft)
private val QUESTIONS = listOf(
    Question("💧", "Water appearance", listOf("clear" to "Clear", "slightly_cloudy" to "Slightly cloudy", "very_cloudy" to "Very cloudy", "not_sure" to "Not sure"), { it.waterClarity }, { d, v -> d.copy(waterClarity = v) }),
    Question("🌿", "Algae", YES_NO, { it.algae }, { d, v -> d.copy(algae = v) }),
    Question("🗑", "Waste or debris", YES_NO, { it.waste }, { d, v -> d.copy(waste = v) }),
    Question("👃", "Unusual smell", listOf("none" to "None", "mild" to "Mild", "strong" to "Strong", "not_sure" to "Not sure"), { it.odor }, { d, v -> d.copy(odor = v) }),
    Question("🐟", "Aquatic life", YES_NO, { it.aquaticLife }, { d, v -> d.copy(aquaticLife = v) }),
)

private fun distanceKm(from: Location, site: Site): Float {
    val out = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, site.latitude, site.longitude, out)
    return out[0] / 1000f
}

@Composable
fun ReportScreen(nav: Nav, preselectedSite: String?, notify: (String) -> Unit) {
    val repo = repository()
    val vm: ReportViewModel = viewModel { ReportViewModel(repo) }
    val context = LocalContext.current
    val mode by repo.mode.collectAsStateWithLifecycle()
    val sites by vm.sites.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val step by vm.step.collectAsStateWithLifecycle()
    val submit by vm.submit.collectAsStateWithLifecycle()
    val photoBusy by vm.photoBusy.collectAsStateWithLifecycle()
    val photoError by vm.photoError.collectAsStateWithLifecycle()
    var location by remember { mutableStateOf<Location?>(null) }

    LaunchedEffect(mode, preselectedSite) { vm.preselect(preselectedSite) }
    BackHandler(enabled = step > 0 && submit is SubmitState.Idle) { vm.go(step - 1) }

    // ---- launchers: location, camera, gallery -----------------------------
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) notify("Location permission was not granted. Please choose a site from the list.")
        else {
            location = lastKnownLocation(context)
            if (location == null) notify("We couldn't find your location. Please choose a site from the list.")
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) vm.setPhoto(context, uri) else if (!saved) notify("No photo was taken.")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setPhoto(context, uri)
    }
    fun openCamera() {
        try {
            val file = File(context.cacheDir, "camera").apply { mkdirs() }.resolve("capture-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraUri = uri
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            notify("No camera app is available. You can choose a photo from your gallery instead.")
        } catch (e: Exception) {
            notify("The camera couldn't be opened. You can choose a photo from your gallery instead.")
        }
    }

    // ---- terminal states ---------------------------------------------------
    when (val s = submit) {
        is SubmitState.Sent -> { SuccessView(draft, s.contributions, queued = false, onSite = { val id = draft.site!!.id; vm.reset(); nav.siteAfterReport(id) }, onAnother = vm::reset); return }
        is SubmitState.Queued -> { SuccessView(draft, 0, queued = true, onSite = { vm.reset(); nav.tab("home") }, onAnother = vm::reset); return }
        else -> Unit
    }
    if (submit is SubmitState.PhotoFailed) {
        AlertDialog(onDismissRequest = vm::dismissError, title = { Text("Photo not uploaded") }, text = { Text((submit as SubmitState.PhotoFailed).message) },
            confirmButton = { TextButton(onClick = { vm.send(includePhoto = true) }) { Text("Try again") } },
            dismissButton = { TextButton(onClick = { vm.send(includePhoto = false) }) { Text("Send without photo") } })
    }
    if (submit is SubmitState.Failed) {
        AlertDialog(onDismissRequest = vm::dismissError, title = { Text("Not sent yet") }, text = { Text((submit as SubmitState.Failed).message) },
            confirmButton = { TextButton(onClick = { vm.send() }) { Text("Try again") } }, dismissButton = { TextButton(onClick = vm::dismissError) { Text("Close") } })
    }

    Column(Modifier.fillMaxSize()) {
        // Progress header
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.go(step - 1) }, enabled = step > 0) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous step", tint = if (step > 0) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            }
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(progress = { (step + 1) / 5f }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.primaryContainer)
                Text("Step ${step + 1} of 5" + (draft.site?.takeIf { step > 0 }?.let { " · ${it.name}" } ?: ""), Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedContent(step, Modifier.weight(1f), label = "report-step") { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(STEP_TITLES[current], style = MaterialTheme.typography.headlineMedium)
                when (current) {
                    0 -> {
                        Text("Pick the water site you're at. It takes less than a minute from here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, Modifier.fillMaxWidth().height(50.dp)) {
                            Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (location != null) "Sorted by distance from you" else "Use my location")
                        }
                        when {
                            sites.data == null && sites.error != null -> ErrorState(sites.error!!, onRetry = { vm.preselect(null) })
                            sites.data == null -> repeat(3) { SkeletonCard(72.dp) }
                            else -> {
                                val ordered = location?.let { l -> sites.data!!.sortedBy { distanceKm(l, it) } } ?: sites.data!!
                                ordered.forEach { site ->
                                    val on = draft.site?.id == site.id
                                    Surface(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(role = Role.RadioButton) { vm.update { it.copy(site = site) }; vm.go(1) },
                                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(site.name, style = MaterialTheme.typography.titleMedium)
                                                Text(site.locality.orEmpty() + (location?.let { " · ${String.format("%.1f", distanceKm(it, site))} km away" } ?: ""),
                                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            }
                                            StatusBadge(site.status)
                                        }
                                    }
                                }
                                TextButton(onClick = { nav.explore() }) { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Choose on the map instead") }
                            }
                        }
                    }
                    1 -> {
                        Text("Tap what applies. Skip anything you're unsure about.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        QUESTIONS.forEach { q ->
                            HCard(padding = 14.dp) {
                                Text("${q.emoji}  ${q.title}", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(10.dp))
                                OptionRow(q.options, q.get(draft)) { v -> vm.update { q.set(it, v) } }
                            }
                        }
                        NextButton("Continue") { vm.go(2) }
                    }
                    2 -> {
                        Text("Optional. A picture of the water helps others see what you see. Please avoid faces.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when {
                            !repo.photosEnabled -> HCard { Text("Photo upload isn't available in this build. You can continue without one.", style = MaterialTheme.typography.bodyMedium) }
                            photoBusy -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(); Text("Preparing photo…", Modifier.padding(top = 72.dp), style = MaterialTheme.typography.bodySmall) }
                            draft.photo != null -> Box {
                                AsyncImage(draft.photo, "Your photo of the water", Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
                                FilledTonalButton(onClick = vm::clearPhoto, Modifier.align(Alignment.TopEnd).padding(10.dp)) { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remove") }
                            }
                            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PhotoSource(Icons.Outlined.PhotoCamera, "Take a photo", Modifier.weight(1f)) { openCamera() }
                                PhotoSource(Icons.Outlined.PhotoLibrary, "Choose from gallery", Modifier.weight(1f)) {
                                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            }
                        }
                        photoError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                        NextButton(if (draft.photo != null) "Continue" else "Skip") { vm.go(3) }
                    }
                    3 -> {
                        Text("Optional. Add anything you think is worth noting.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(draft.comment, { v -> vm.update { it.copy(comment = v.take(500)) } }, Modifier.fillMaxWidth().height(160.dp),
                            placeholder = { Text("For example: the water level looks lower than last week.") }, shape = MaterialTheme.shapes.large,
                            supportingText = { Text("${draft.comment.length}/500", Modifier.fillMaxWidth(), textAlign = TextAlign.End) })
                        NextButton("Review") { vm.go(4) }
                    }
                    else -> {
                        HCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                                Column { Text(draft.site?.name.orEmpty(), style = MaterialTheme.typography.titleMedium); Text(draft.site?.locality.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Spacer(Modifier.height(12.dp))
                            val o = draft.toObservation()
                            ChipFlow(observationChips(o.waterClarity, o.algae, o.waste, o.odor, o.aquaticLife))
                            draft.photo?.let { Spacer(Modifier.height(12.dp)); AsyncImage(it, "Your photo", Modifier.fillMaxWidth().height(170.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop) }
                            if (draft.comment.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text("“${draft.comment.trim()}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        val working = submit as? SubmitState.Working
                        Button(onClick = { vm.send() }, Modifier.fillMaxWidth().height(56.dp), enabled = working == null && draft.site != null) {
                            if (working != null) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)); Text(working.stage + (working.progress?.let { " ${(it * 100).toInt()}%" } ?: "…")) }
                            else { Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Send my observation", style = MaterialTheme.typography.titleSmall) }
                        }
                        working?.progress?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().clip(CircleShape)) }
                        Text("No account needed. Observations are public and anonymous.", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun NextButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, Modifier.fillMaxWidth().height(54.dp)) { Text(label, style = MaterialTheme.typography.titleSmall) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = selected == value
            Text(label, Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.RadioButton) { onSelect(value) }.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PhotoSource(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.height(150.dp).clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SuccessView(draft: ReportDraft, contributions: Int, queued: Boolean, onSite: () -> Unit, onAnother: () -> Unit) {
    val status = LocalStatusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(84.dp).clip(CircleShape).background(if (queued) status.watch else status.ok), contentAlignment = Alignment.Center) {
            Icon(if (queued) Icons.Outlined.CloudSync else Icons.Outlined.Check, null, Modifier.size(44.dp), tint = Color.White)
        }
        Text(if (queued) "Saved. We'll send it when you're back online." else "Thank you for helping monitor your local water.",
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(if (queued) "Your observation is stored safely on this device and will be sent automatically." else "Your observation has been added to HydroSense.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        val o = draft.toObservation()
        ChipFlow(observationChips(o.waterClarity, o.algae, o.waste, o.odor, o.aquaticLife))
        if (!queued) HCard {
            Text("COMMUNITY CONTRIBUTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("You've helped document", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$contributions water observation${if (contributions == 1) "" else "s"}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            ChipFlow(BADGES.map { "${it.emoji} ${it.name}" to (contributions < it.at) })
            BADGES.firstOrNull { contributions < it.at }?.let { Text("${it.at - contributions} more to reach ${it.name}.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Button(onClick = onSite, Modifier.fillMaxWidth().height(52.dp)) { Text(if (queued) "Back to home" else "See ${draft.site?.name ?: "this site"}") }
        OutlinedButton(onClick = onAnother, Modifier.fillMaxWidth().height(52.dp)) { Text("Report another") }
    }
}

@Suppress("MissingPermission")
private fun lastKnownLocation(context: Context): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return runCatching {
        manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }.getOrNull()
}
