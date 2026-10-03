package com.hydrosense.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hydrosense.app.data.Site
import com.hydrosense.app.ui.ExploreViewModel
import com.hydrosense.app.ui.Nav
import com.hydrosense.app.ui.components.*
import com.hydrosense.app.ui.repository
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val LEGEND = listOf("stable", "attention", "unusual", "alert")
private fun statusArgb(status: String): Int = when (status) {
    "stable" -> 0xFF1C8F5F; "attention" -> 0xFFC18A00; "unusual" -> 0xFFD9601F; "alert" -> 0xFFC53030; else -> 0xFF5C6F80
}.toInt()
private fun statusGlyph(status: String) = when (status) { "stable" -> "✓"; "attention" -> "•"; "unusual" -> "≈"; "alert" -> "!"; else -> "?" }

/** Marker = coloured disc + white ring + glyph, so state never depends on colour alone. */
private fun markerDrawable(context: Context, status: String, selected: Boolean): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val size = ((if (selected) 54 else 42) * d).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap); val c = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (selected) { paint.color = 0x5535B6C9; canvas.drawCircle(c, c, c, paint) }
    val r = (if (selected) 21 else 19) * d
    paint.color = 0x33000000; canvas.drawCircle(c, c + 1.5f * d, r, paint)
    paint.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(c, c, r, paint)
    paint.color = statusArgb(status); canvas.drawCircle(c, c, r - 3 * d, paint)
    paint.color = 0xFFFFFFFF.toInt(); paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = 19 * d
    canvas.drawText(statusGlyph(status), c, c - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDrawable(context.resources, bitmap)
}

@Composable
fun ExploreScreen(nav: Nav, initialSite: String?) {
    val repo = repository()
    val vm: ExploreViewModel = viewModel { ExploreViewModel(repo) }
    val mode by repo.mode.collectAsStateWithLifecycle()
    val sites by vm.sites.state.collectAsStateWithLifecycle()
    val spark by vm.spark.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf(initialSite) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    Poll(mode, pollInterval(mode)) { vm.refresh() }

    val visible = sites.data.orEmpty().filter { filter == null || it.status == filter }
    val selected = sites.data.orEmpty().firstOrNull { it.id == selectedId }
    LaunchedEffect(selectedId) { selectedId?.let(vm::loadSpark) }

    Box(Modifier.fillMaxSize()) {
        if (sites.data == null && sites.error != null) {
            ErrorState(sites.error!!, onRetry = { nav.explore() }, Modifier.align(Alignment.Center))
        } else {
            SiteMap(visible, selectedId, onSelect = { selectedId = it.id }, Modifier.fillMaxSize())
        }

        // Filter chips double as the legend: colour + icon + label.
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            OfflineBanner(sites.offline)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEGEND.forEach { s ->
                    val style = statusStyle(s); val on = filter == s
                    Row(Modifier.clip(CircleShape).background(if (on) style.color else MaterialTheme.colorScheme.surface)
                        .clickable { filter = if (on) null else s; selectedId = null }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(style.icon, null, Modifier.size(16.dp), tint = if (on) Color.White else style.color)
                        Text(style.label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Text("HydroSense monitoring states, not certified water-safety classes.",
                Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("© OpenStreetMap contributors", Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        AnimatedVisibility(selected != null, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            selected?.let { site ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 16.dp) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(site.name, style = MaterialTheme.typography.headlineSmall)
                                Text(site.locality.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (site.isDemo) DemoTag(Modifier.padding(top = 4.dp, end = 4.dp))
                            IconButton(onClick = { selectedId = null }) { Icon(Icons.Outlined.Close, "Close site summary") }
                        }
                        val style = statusStyle(site.status)
                        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(style.soft).padding(14.dp)) {
                            StatusBadge(site.status, label = site.headline)
                            Spacer(Modifier.height(6.dp))
                            Text(site.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tile("Latest reading", ppm(site.latestTds), Modifier.weight(1f), unit = "ppm")
                            Tile("Last updated", timeAgo(site.latestAt), Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Last 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Sparkline(spark[site.id]?.points.orEmpty(), style.color, Modifier.fillMaxWidth().height(40.dp))
                            }
                            Text(if (site.activeAlerts > 0) "${site.activeAlerts} active alert${if (site.activeAlerts == 1) "" else "s"}" else "No active alerts",
                                style = MaterialTheme.typography.labelMedium, color = if (site.activeAlerts > 0) style.color else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { nav.site(site.id) }, Modifier.weight(1f).height(50.dp)) { Text("Understand this site") }
                            OutlinedButton(onClick = { nav.report(site.id) }, Modifier.height(50.dp)) {
                                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Report")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteMap(sites: List<Site>, selectedId: String?, onSelect: (Site) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(28.61, 77.2))
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) map.onResume() else if (event == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); map.onDetach() }
    }
    // Dim the tiles slightly in dark mode so the bright map doesn't glare.
    LaunchedEffect(dark) {
        map.overlayManager.tilesOverlay.setColorFilter(
            if (dark) android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(0.72f, 0.72f, 0.78f, 1f) }) else null)
        map.invalidate()
    }
    var fittedFor by remember { mutableStateOf("") }
    AndroidView({ map }, modifier) { view ->
        view.overlays.removeAll { it is Marker }
        sites.forEach { site ->
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(site.latitude, site.longitude)
                icon = markerDrawable(context, site.status, site.id == selectedId)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "${site.name}: ${site.statusLabel}"
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> onSelect(site); true }
            })
        }
        val key = sites.joinToString(",") { it.id }
        if (sites.isNotEmpty() && key != fittedFor) {
            fittedFor = key
            view.post {
                if (sites.size == 1) { view.controller.setZoom(14.0); view.controller.setCenter(GeoPoint(sites[0].latitude, sites[0].longitude)) }
                else view.zoomToBoundingBox(BoundingBox.fromGeoPoints(sites.map { GeoPoint(it.latitude, it.longitude) }).increaseByScale(1.5f), false)
            }
        }
        sites.firstOrNull { it.id == selectedId }?.let { s ->
            // Keep the selected marker visible above the bottom sheet.
            view.post {
                val point = view.projection.toPixels(GeoPoint(s.latitude, s.longitude), null)
                view.controller.animateTo(view.projection.fromPixels(point.x, point.y + (view.height * 0.24f).toInt()))
            }
        }
        view.invalidate()
    }
}
