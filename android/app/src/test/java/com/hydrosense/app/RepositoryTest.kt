package com.hydrosense.app

import com.hydrosense.app.data.MemoryStore
import com.hydrosense.app.data.NewObservation
import com.hydrosense.app.data.PhotoUploadException
import com.hydrosense.app.data.Repository
import com.hydrosense.app.data.SubmitResult
import com.hydrosense.app.data.createApi
import com.hydrosense.app.data.createHttpClient
import com.hydrosense.app.data.friendlyError
import com.hydrosense.app.ui.Loader
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Repository behaviour against a fake server: caching, errors, upload and the offline queue. */
class RepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val sitesJson = """[{"id":"DEMO-01","name":"Water Site 01","latitude":1.0,"longitude":2.0,"status":"stable"}]"""
    private val obsJson = """{"id":"o1","site_id":"DEMO-01","site_name":"Water Site 01","timestamp":"2026-10-03T07:00:00+00:00","comment":"hi"}"""
    private val draft = NewObservation("DEMO-01", "clear", "no", "no", "none", "yes", comment = "hi")

    @Before fun setUp() { server = MockWebServer().apply { start() }; dir = Files.createTempDirectory("hs").toFile() }
    @After fun tearDown() { runCatching { server.shutdown() }; dir.deleteRecursively() }

    private fun repo(store: MemoryStore = MemoryStore()): Repository {
        val http = createHttpClient()
        val base = server.url("/").toString()
        return Repository(createApi(base, http), http, File(dir, "cache").apply { mkdirs() }, File(dir, "files").apply { mkdirs() }, store, base.trimEnd('/'), "anon-key")
    }

    @Test fun `sites are fetched in the current mode and cached for offline use`() = runTest {
        val r = repo()
        server.enqueue(MockResponse().setBody(sitesJson))
        val fresh = r.sites()
        assertFalse(fresh.fromCache)
        assertEquals("/api/sites?mode=demo", server.takeRequest().path)

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val cached = r.sites()
        assertTrue(cached.fromCache)
        assertEquals("Water Site 01", cached.data.single().name)
    }

    @Test fun `mode switch is persisted and changes the query`() = runTest {
        val store = MemoryStore()
        val r = repo(store)
        r.setMode("live")
        server.enqueue(MockResponse().setBody("[]"))
        r.sites()
        assertEquals("/api/sites?mode=live", server.takeRequest().path)
        assertEquals("live", repo(store).mode.value)
    }

    @Test fun `loader exposes a friendly error and keeps no stale data`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"We couldn't find a water site called 'X'."}"""))
        val r = repo()
        val loader = Loader { r.site("X") }
        loader.load()
        assertNull(loader.state.value.data)
        assertEquals("We couldn't find a water site called 'X'.", loader.state.value.error)
        assertFalse(loader.state.value.loading)
    }

    @Test fun `report with photo uploads to the citizen folder with the anon key then posts the observation`() = runTest {
        val r = repo()
        val photo = File(dir, "p.jpg").apply { writeBytes(ByteArray(2048) { 7 }) }
        server.enqueue(MockResponse().setBody("""{"Key":"observation-photos/x"}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody(obsJson))
        val progress = mutableListOf<Float>()
        val result = r.submitReport(draft, "Water Site 01", photo) { _, p -> p?.let(progress::add) }

        val upload = server.takeRequest()
        assertTrue(upload.path!!.startsWith("/storage/v1/object/observation-photos/citizen/DEMO-01/"))
        assertEquals("Bearer anon-key", upload.getHeader("Authorization"))
        assertEquals(2048L, upload.bodySize)
        val post = server.takeRequest()
        assertEquals("/api/observations", post.path)
        val body = post.body.readUtf8()
        assertTrue(body.contains("/storage/v1/object/public/observation-photos/citizen/DEMO-01/"))
        assertTrue(result is SubmitResult.Sent)
        assertEquals(1, (result as SubmitResult.Sent).contributions)
        assertEquals(1f, progress.last(), 0.001f)
        assertFalse(photo.exists())
    }

    @Test fun `rejected photo surfaces as a photo error so the user can retry or skip it`() = runTest {
        val r = repo()
        val photo = File(dir, "p.jpg").apply { writeBytes(ByteArray(10)) }
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        val error = runCatching { r.submitReport(draft, "Water Site 01", photo) }.exceptionOrNull()
        assertTrue(error is PhotoUploadException)
        assertNotNull(friendlyError(error!!))
        // Sending without the photo then succeeds.
        server.enqueue(MockResponse().setResponseCode(201).setBody(obsJson))
        assertTrue(r.submitReport(draft, "Water Site 01", photo, includePhoto = false) is SubmitResult.Sent)
    }

    @Test fun `offline report is queued, survives a restart and is sent later`() = runTest {
        val store = MemoryStore()
        val r = repo(store)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val result = r.submitReport(draft, "Water Site 01", null)
        assertTrue(result is SubmitResult.Queued)
        assertEquals(1, r.pending.value.size)

        val restarted = repo(store)                       // new instance reads the queue from disk
        assertEquals(1, restarted.pending.value.size)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(0, restarted.syncPending())          // still offline: nothing lost
        assertEquals(1, restarted.pending.value.size)

        server.enqueue(MockResponse().setResponseCode(201).setBody(obsJson))
        assertEquals(1, restarted.syncPending())
        assertTrue(restarted.pending.value.isEmpty())
        assertEquals(1, restarted.contributions.value)
    }

    @Test fun `validation error is shown in plain language`() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail":[{"loc":["body","algae"],"msg":"bad"}]}"""))
        val error = runCatching { repo().submitReport(draft, "Water Site 01", null) }.exceptionOrNull()!!
        assertEquals("Some of the information looks incomplete. Please check and try again.", friendlyError(error))
    }
}
