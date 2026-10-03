package com.hydrosense.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/** Data plus whether it came from the on-device cache because the network failed. */
data class Loaded<T>(val data: T, val fromCache: Boolean = false)

class PhotoUploadException(message: String, val retryable: Boolean) : Exception(message)

sealed interface SubmitResult {
    data class Sent(val observation: Observation, val contributions: Int) : SubmitResult
    /** No connection: the report is stored on the device and will be sent later. */
    data class Queued(val pending: Int) : SubmitResult
}

/** Turns exceptions into messages a citizen can act on. Never shows a stack trace. */
fun friendlyError(e: Throwable): String = when (e) {
    is PhotoUploadException -> e.message ?: "We couldn't upload your photo."
    is HttpException -> {
        val detail = runCatching {
            val body = e.response()?.errorBody()?.string().orEmpty()
            (HydroJson.decodeFromString(ApiError.serializer(), body).detail as? JsonPrimitive)?.content
        }.getOrNull()
        detail ?: if (e.code() == 422) "Some of the information looks incomplete. Please check and try again."
        else "HydroSense is having trouble right now. Please try again in a moment."
    }
    is IOException -> "You appear to be offline. Check your connection and try again."
    else -> "Something went wrong. Please try again."
}

class Repository(
    private val api: HydroSenseApi,
    private val http: OkHttpClient,
    private val cacheDir: File,
    private val filesDir: File,
    private val prefs: KeyValueStore,
    private val supabaseUrl: String,
    private val supabaseAnonKey: String,
) {
    // ---- Demo / Live mode (same toggle as the web app) ----------------------
    private val _mode = MutableStateFlow(if (prefs.getString(KEY_MODE) == "live") "live" else "demo")
    val mode: StateFlow<String> = _mode
    fun setMode(mode: String) {
        prefs.putString(KEY_MODE, mode)
        _mode.value = mode
    }

    // ---- Reads, with a small on-device cache for offline use ---------------
    suspend fun stats() = cached("stats-${mode.value}") { api.stats(mode.value) }
    suspend fun sites() = cached("sites-${mode.value}", ListSerializer(Site.serializer())) { api.sites(mode.value) }
    suspend fun site(id: String) = cached("site-$id") { api.site(id) }
    suspend fun series(id: String, range: String) = cached("series-$id-$range") { api.series(id, range) }
    suspend fun analytics(id: String) = cached("analytics-$id") { api.analytics(id) }
    suspend fun assessment(id: String) = cached("assessment-$id") { api.assessment(id) }
    suspend fun health() = cached("health") { api.health() }
    suspend fun alerts(siteId: String? = null) =
        cached("alerts-${siteId ?: mode.value}", ListSerializer(Alert.serializer())) { api.alerts(mode.value, siteId) }
    suspend fun observations(siteId: String? = null, limit: Int = 20) =
        cached("observations-${siteId ?: mode.value}", ListSerializer(Observation.serializer())) { api.observations(mode.value, siteId, limit) }

    private suspend inline fun <reified T> cached(key: String, noinline fetch: suspend () -> T): Loaded<T> =
        cached(key, serializer<T>(), fetch)

    private suspend fun <T> cached(key: String, ser: kotlinx.serialization.KSerializer<T>, fetch: suspend () -> T): Loaded<T> =
        withContext(Dispatchers.IO) {
            val file = File(cacheDir, "api-${key.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")
            try {
                val fresh = fetch()
                runCatching { file.writeText(HydroJson.encodeToString(ser, fresh)) }
                Loaded(fresh)
            } catch (e: Exception) {
                // No connection, or an unreadable reply (for example a Wi-Fi sign-in page):
                // fall back to the last good copy saved on the device.
                if (e !is IOException && e !is kotlinx.serialization.SerializationException) throw e
                val old = runCatching { HydroJson.decodeFromString(ser, file.readText()) }.getOrNull() ?: throw e
                Loaded(old, fromCache = true)
            }
        }

    // ---- Photo upload to the existing Supabase Storage bucket --------------
    val photosEnabled: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    /** Shrinks a phone photo (longest side 1600 px, JPEG) and fixes its rotation. */
    suspend fun compressPhoto(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode returns no bitmap by design, so only the stream itself is checked here.
        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull()
            ?: throw PhotoUploadException("We couldn't open that photo.", retryable = false)
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw PhotoUploadException("That file doesn't look like a photo.", retryable = false)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw PhotoUploadException("That file doesn't look like a photo.", retryable = false)
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = File(filesDir, "pending").apply { mkdirs() }.resolve("${UUID.randomUUID()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        out
    }

    /**
     * Uploads with the public anon key, exactly like the web app. Row Level
     * Security on the bucket only allows inserts under citizen/, so this key
     * cannot overwrite, delete or read anything private.
     */
    suspend fun uploadPhoto(file: File, siteId: String, onProgress: (Float) -> Unit = {}): Pair<String, String> =
        withContext(Dispatchers.IO) {
            if (!photosEnabled) throw PhotoUploadException("Photo upload isn't available in this build.", retryable = false)
            val path = "citizen/$siteId/${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.jpg"
            val request = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/storage/v1/object/$BUCKET/$path")
                .header("apikey", supabaseAnonKey)
                .header("Authorization", "Bearer $supabaseAnonKey")
                .header("cache-control", "31536000")
                .post(ProgressBody(file, "image/jpeg".toMediaType(), onProgress))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PhotoUploadException(
                        "We couldn't upload your photo. You can try again or send the report without it.",
                        retryable = response.code >= 500 || response.code == 429,
                    )
                }
            }
            "${supabaseUrl.trimEnd('/')}/storage/v1/object/public/$BUCKET/$path" to path
        }

    // ---- Submitting a citizen report ---------------------------------------
    private val _pending = MutableStateFlow(loadPending())
    val pending: StateFlow<List<PendingReport>> = _pending
    private val syncLock = Mutex()

    private val _contributions = MutableStateFlow(prefs.getInt(KEY_CONTRIBUTIONS))
    val contributions: StateFlow<Int> = _contributions

    /**
     * Sends a report through the same API endpoint as the web app. If the device
     * is offline the report is kept on the device and sent later, so it is never
     * silently lost.
     */
    suspend fun submitReport(
        draft: NewObservation, siteName: String, photo: File?, includePhoto: Boolean = true,
        onStage: (String, Float?) -> Unit = { _, _ -> },
    ): SubmitResult {
        return try {
            var body = draft
            if (photo != null && includePhoto) {
                onStage("Uploading photo", 0f)
                val (url, path) = uploadPhoto(photo, draft.siteId) { onStage("Uploading photo", it) }
                body = draft.copy(imageUrl = url, imagePath = path)
            }
            onStage("Sending your observation", null)
            val saved = withContext(Dispatchers.IO) { api.addObservation(body) }
            photo?.delete()
            SubmitResult.Sent(saved, bumpContributions())
        } catch (e: IOException) {
            queue(PendingReport(UUID.randomUUID().toString(), draft, siteName, photo?.takeIf { includePhoto }?.absolutePath, System.currentTimeMillis()))
            SubmitResult.Queued(_pending.value.size)
        }
    }

    /** Tries to send every stored report. Returns how many were delivered. */
    suspend fun syncPending(): Int = syncLock.withLock {
        var sent = 0
        for (report in _pending.value.toList()) {
            try {
                var body = report.observation
                val photo = report.photoFile?.let(::File)?.takeIf { it.exists() }
                if (photo != null && photosEnabled) {
                    val (url, path) = uploadPhoto(photo, body.siteId)
                    body = body.copy(imageUrl = url, imagePath = path)
                }
                withContext(Dispatchers.IO) { api.addObservation(body) }
                photo?.delete()
                remove(report.id)
                bumpContributions()
                sent++
            } catch (e: IOException) {
                break // still offline: keep everything for the next attempt
            } catch (e: PhotoUploadException) {
                if (e.retryable) break
                // The photo can never be uploaded: send the observation without it.
                runCatching { withContext(Dispatchers.IO) { api.addObservation(report.observation) } }
                    .onSuccess { remove(report.id); bumpContributions(); sent++ }
            } catch (e: HttpException) {
                if (e.code() in 400..499 && e.code() != 429) remove(report.id) // rejected for good: do not retry forever
                else break
            }
        }
        sent
    }

    private fun bumpContributions(): Int {
        val next = _contributions.value + 1
        prefs.putInt(KEY_CONTRIBUTIONS, next)
        _contributions.value = next
        return next
    }

    private val pendingFile get() = File(filesDir, "pending-reports.json")
    private fun loadPending(): List<PendingReport> = runCatching {
        HydroJson.decodeFromString(ListSerializer(PendingReport.serializer()), pendingFile.readText())
    }.getOrDefault(emptyList())

    private fun savePending(list: List<PendingReport>) {
        pendingFile.writeText(HydroJson.encodeToString(ListSerializer(PendingReport.serializer()), list))
        _pending.value = list
    }
    private fun queue(report: PendingReport) = savePending(_pending.value + report)
    private fun remove(id: String) = savePending(_pending.value.filterNot { it.id == id })

    companion object {
        const val BUCKET = "observation-photos"
        const val MAX_SIDE = 1600
        private const val KEY_MODE = "mode"
        private const val KEY_CONTRIBUTIONS = "contributions"
    }
}

/** Minimal preferences abstraction so the repository can be unit tested on the JVM. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String): Int
    fun putInt(key: String, value: Int)
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("hydrosense", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getInt(key: String): Int = prefs.getInt(key, 0)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
}

class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String) = map[key] as? String
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getInt(key: String) = map[key] as? Int ?: 0
    override fun putInt(key: String, value: Int) { map[key] = value }
}

/** Request body that reports upload progress from 0 to 1. */
private class ProgressBody(private val file: File, private val type: MediaType, private val onProgress: (Float) -> Unit) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        val total = file.length().coerceAtLeast(1)
        var written = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                written += read
                onProgress(written.toFloat() / total)
            }
        }
    }
}

data class Badge(val name: String, val emoji: String, val at: Int)
val BADGES = listOf(Badge("First Observation", "💧", 1), Badge("Water Watcher", "🌊", 3), Badge("Community Contributor", "🌱", 10))
