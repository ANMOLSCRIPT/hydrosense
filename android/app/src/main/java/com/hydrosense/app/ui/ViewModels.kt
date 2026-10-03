package com.hydrosense.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrosense.app.HydroSenseApp
import com.hydrosense.app.data.Alert
import com.hydrosense.app.data.Analytics
import com.hydrosense.app.data.Assessment
import com.hydrosense.app.data.Health
import com.hydrosense.app.data.Loaded
import com.hydrosense.app.data.NewObservation
import com.hydrosense.app.data.Observation
import com.hydrosense.app.data.PhotoUploadException
import com.hydrosense.app.data.Repository
import com.hydrosense.app.data.Series
import com.hydrosense.app.data.Site
import com.hydrosense.app.data.Stats
import com.hydrosense.app.data.SubmitResult
import com.hydrosense.app.data.friendlyError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UiState<T>(val data: T? = null, val loading: Boolean = true, val error: String? = null, val offline: Boolean = false)

/** Loads one piece of data and keeps the last good value while refreshing. */
class Loader<T>(private val fetch: suspend () -> Loaded<T>) {
    private val _state = MutableStateFlow(UiState<T>())
    val state: StateFlow<UiState<T>> = _state

    suspend fun load() {
        _state.update { it.copy(loading = it.data == null, error = null) }
        try {
            val result = fetch()
            _state.value = UiState(result.data, loading = false, offline = result.fromCache)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.update { it.copy(loading = false, error = friendlyError(e)) }
        }
    }
}

suspend fun loadAll(vararg loaders: Loader<*>) = coroutineScope { loaders.map { async { it.load() } }.awaitAll() }

@Composable
fun repository(): Repository = (LocalContext.current.applicationContext as HydroSenseApp).repository

class HomeViewModel(val repo: Repository) : ViewModel() {
    val stats = Loader { repo.stats() }
    val sites = Loader { repo.sites() }
    val alerts = Loader { repo.alerts() }
    suspend fun refresh() { loadAll(stats, sites, alerts); runCatching { repo.syncPending() } }
}

class ExploreViewModel(val repo: Repository) : ViewModel() {
    val sites = Loader { repo.sites() }
    private val _spark = MutableStateFlow<Map<String, Series>>(emptyMap())
    val spark: StateFlow<Map<String, Series>> = _spark
    suspend fun refresh() = sites.load()
    fun loadSpark(siteId: String) = viewModelScope.launch {
        runCatching { repo.series(siteId, "24h").data }.onSuccess { s -> _spark.update { it + (siteId to s) } }
    }
}

class SiteViewModel(val repo: Repository, val siteId: String) : ViewModel() {
    val range = MutableStateFlow("7d")
    val site = Loader { repo.site(siteId) }
    val series = Loader { repo.series(siteId, range.value) }
    val assessment = Loader { repo.assessment(siteId) }
    val observations = Loader { repo.observations(siteId, 12) }
    val alerts = Loader { repo.alerts(siteId) }
    val analytics = Loader { repo.analytics(siteId) }
    suspend fun refresh() = loadAll(site, series, assessment, observations, alerts)
    suspend fun refreshAnalytics() = loadAll(site, series, analytics, assessment, alerts)
    fun setRange(value: String) { range.value = value; viewModelScope.launch { series.load() } }
}

class AlertsViewModel(val repo: Repository) : ViewModel() {
    val alerts = Loader { repo.alerts() }
    suspend fun refresh() = alerts.load()
}

class AboutViewModel(val repo: Repository) : ViewModel() {
    val health = Loader { repo.health() }
    fun retryPending(onDone: (Int) -> Unit) = viewModelScope.launch { onDone(runCatching { repo.syncPending() }.getOrDefault(0)) }
}

data class ReportDraft(
    val site: Site? = null,
    val waterClarity: String? = null, val algae: String? = null, val waste: String? = null,
    val odor: String? = null, val aquaticLife: String? = null,
    val photo: File? = null, val comment: String = "",
) {
    /** Unanswered questions are sent as "not sure", which the API already supports. */
    fun toObservation() = NewObservation(
        siteId = site!!.id, waterClarity = waterClarity ?: "not_sure", algae = algae ?: "not_sure", waste = waste ?: "not_sure",
        odor = odor ?: "not_sure", aquaticLife = aquaticLife ?: "not_sure", comment = comment.trim().ifBlank { null },
    )
    val answered: Int get() = listOf(waterClarity, algae, waste, odor, aquaticLife).count { it != null }
}

sealed interface SubmitState {
    data object Idle : SubmitState
    data class Working(val stage: String, val progress: Float?) : SubmitState
    data class PhotoFailed(val message: String) : SubmitState
    data class Failed(val message: String) : SubmitState
    data class Sent(val observation: Observation, val contributions: Int) : SubmitState
    data class Queued(val pending: Int) : SubmitState
}

class ReportViewModel(val repo: Repository) : ViewModel() {
    val sites = Loader { repo.sites() }
    val draft = MutableStateFlow(ReportDraft())
    val step = MutableStateFlow(0)
    val submit = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val photoBusy = MutableStateFlow(false)
    val photoError = MutableStateFlow<String?>(null)

    fun preselect(siteId: String?) = viewModelScope.launch {
        sites.load()
        val match = sites.state.value.data?.firstOrNull { it.id == siteId } ?: return@launch
        if (draft.value.site == null) { draft.update { it.copy(site = match) }; step.value = 1 }
    }
    fun update(change: (ReportDraft) -> ReportDraft) = draft.update(change)
    fun go(to: Int) { step.value = to.coerceIn(0, LAST_STEP) }

    fun setPhoto(context: Context, uri: Uri) = viewModelScope.launch {
        photoBusy.value = true; photoError.value = null
        try {
            val file = repo.compressPhoto(context.applicationContext, uri)
            draft.value.photo?.delete()
            draft.update { it.copy(photo = file) }
        } catch (e: Exception) {
            photoError.value = if (e is PhotoUploadException) e.message else "We couldn't use that photo. Please try a different one."
        } finally { photoBusy.value = false }
    }
    fun clearPhoto() { draft.value.photo?.delete(); draft.update { it.copy(photo = null) } }

    fun send(includePhoto: Boolean = true) = viewModelScope.launch {
        val d = draft.value
        if (d.site == null) return@launch
        submit.value = SubmitState.Working("Preparing", null)
        try {
            when (val r = repo.submitReport(d.toObservation(), d.site.name, d.photo, includePhoto) { stage, p -> submit.value = SubmitState.Working(stage, p) }) {
                is SubmitResult.Sent -> submit.value = SubmitState.Sent(r.observation, r.contributions)
                is SubmitResult.Queued -> submit.value = SubmitState.Queued(r.pending)
            }
        } catch (e: PhotoUploadException) {
            submit.value = SubmitState.PhotoFailed(friendlyError(e))
        } catch (e: Exception) {
            submit.value = SubmitState.Failed(friendlyError(e))
        }
    }
    fun dismissError() { submit.value = SubmitState.Idle }
    fun reset() { draft.value = ReportDraft(); step.value = 0; submit.value = SubmitState.Idle; photoError.value = null }

    companion object { const val LAST_STEP = 4 }
}
