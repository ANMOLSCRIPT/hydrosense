package com.hydrosense.app

import com.hydrosense.app.data.createApi
import com.hydrosense.app.data.createHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Read-only checks against the real, deployed HydroSense backend: the same API
 * and Supabase data the web app uses. Run with -Dhydrosense.live=true.
 * Nothing is written, so production data is never changed by these tests.
 */
class LiveBackendTest {
    private val api = createApi(System.getProperty("hydrosense.api") ?: "https://hydrosense-beta.vercel.app/", createHttpClient())
    private fun live() = assumeTrue("set -Dhydrosense.live=true to run", System.getProperty("hydrosense.live") == "true")

    @Test fun `api is reachable and backed by supabase`() = runBlocking {
        live()
        val health = api.health()
        assertEquals("ok", health.status)
        assertEquals("supabase", health.store)
    }

    @Test fun `sites, measurements, analytics, assessment and alerts load for Water Site 04`() = runBlocking {
        live()
        val sites = api.sites("demo")
        assertEquals(6, sites.size)
        val site04 = sites.first { it.id == "DEMO-04" }
        assertEquals("Water Site 04", site04.name)
        assertNotNull(site04.latestTds)
        assertNotNull(api.site("DEMO-04").citizen)
        for (range in listOf("24h", "7d", "30d")) assertTrue(api.series("DEMO-04", range).points.size >= 20)
        val analytics = api.analytics("DEMO-04")
        assertNotNull(analytics.baselineTds)
        assertTrue(analytics.scoreParts.isNotEmpty())
        val assessment = api.assessment("DEMO-04")
        assertTrue(assessment.explanation.isNotBlank())
        assertTrue(api.alerts("demo").any { it.siteId == "DEMO-04" })
        assertTrue(api.observations("demo", "DEMO-04", 5).isNotEmpty())
        assertTrue(api.sites("live").any { it.id == "SITE-001" })
    }
}
