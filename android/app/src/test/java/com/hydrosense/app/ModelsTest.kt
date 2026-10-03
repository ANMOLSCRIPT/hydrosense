package com.hydrosense.app

import com.hydrosense.app.data.Alert
import com.hydrosense.app.data.Assessment
import com.hydrosense.app.data.HydroJson
import com.hydrosense.app.data.NewObservation
import com.hydrosense.app.data.Site
import com.hydrosense.app.ui.ReportDraft
import com.hydrosense.app.ui.components.observationChips
import com.hydrosense.app.ui.components.riskStatus
import com.hydrosense.app.ui.components.severityStatus
import com.hydrosense.app.ui.components.signed
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Kotlin models must accept exactly what the existing API returns. */
class ModelsTest {
    @Test fun `site list payload parses, including nulls and unknown fields`() {
        val json = """[{"id":"DEMO-04","name":"Water Site 04","locality":"Okhla","description":null,"latitude":28.563,"longitude":77.304,
            "is_demo":true,"status":"unusual","status_label":"Unusual change","headline":"Something has changed","summary":"Recent readings are higher.",
            "latest_tds":341.3,"latest_at":"2026-10-03T07:00:00+00:00","baseline_tds":241,"deviation_percent":41.6,"anomaly_score":78,"active_alerts":1,"future_field":true},
            {"id":"SITE-001","name":"Live","latitude":28.6,"longitude":77.0,"is_demo":false,"status":"no_data","status_label":"Waiting for data",
            "headline":"Waiting","summary":"","latest_tds":null,"latest_at":null,"baseline_tds":null,"deviation_percent":null,"anomaly_score":0,"active_alerts":0}]"""
        val sites = HydroJson.decodeFromString(ListSerializer(Site.serializer()), json)
        assertEquals(2, sites.size)
        assertEquals(341.3, sites[0].latestTds!!, 0.001)
        assertTrue(sites[0].isDemo)
        assertNull(sites[1].latestTds)
        assertNull(sites[1].citizen)
    }

    @Test fun `alert evidence with mixed value types parses`() {
        val json = """{"id":"a1","site_id":"DEMO-04","site_name":"Water Site 04","timestamp":"2026-10-02T21:00:00+00:00","type":"unusual_change",
            "severity":"medium","title":"Something changed","message":"Higher than usual.","detail":null,"recommendation":"Check.",
            "evidence":{"current_tds":335.8,"baseline_tds":240.7,"trend":"increasing","persistence":1.0,"z_score":null},"status":"active","resolved_at":null}"""
        val alert = HydroJson.decodeFromString(Alert.serializer(), json)
        assertEquals("medium", alert.severity)
        assertEquals(5, alert.evidence.size)
    }

    @Test fun `assessment reports its real source`() {
        val json = """{"site_id":"DEMO-04","risk_level":"potential_stress","assessment":"Potential ecosystem stress","confidence":82,"anomaly_score":78,
            "contributing_factors":[{"key":"algae","icon":"leaf","label":"Algae reported","detail":"2 recent report(s).","weight":12}],
            "explanation":"x","recommendation":"y","evidence":{"algae":true,"current_tds":341},"source":"rules","llm_enabled":false}"""
        val a = HydroJson.decodeFromString(Assessment.serializer(), json)
        assertEquals("rules", a.source)
        assertFalse(a.llmEnabled)
        assertEquals("unusual", riskStatus(a.riskLevel))
    }

    @Test fun `report body matches the API contract used by the web app`() {
        val site = Site(id = "DEMO-01", name = "Water Site 01", latitude = 0.0, longitude = 0.0)
        val body = ReportDraft(site = site, waterClarity = "clear", algae = "no", comment = "  hello  ").toObservation()
        val json = HydroJson.encodeToString(NewObservation.serializer(), body)
        assertTrue(json.contains("\"site_id\":\"DEMO-01\""))
        assertTrue(json.contains("\"water_clarity\":\"clear\""))
        assertTrue(json.contains("\"waste\":\"not_sure\""))       // unanswered questions default to not_sure
        assertTrue(json.contains("\"comment\":\"hello\""))
        assertFalse(json.contains("image_url"))                   // nulls are omitted
    }

    @Test fun `plain language helpers`() {
        assertEquals("alert", severityStatus("high"))
        assertEquals("attention", severityStatus("low"))
        assertEquals("+42", signed(41.6))
        assertEquals("−3.5", signed(-3.5, 1))
        val chips = observationChips("slightly_cloudy", "yes", "no", "none", "yes").map { it.first }
        assertEquals(listOf("🌫 Slightly cloudy", "🌿 Algae reported", "🐟 Aquatic life seen"), chips)
    }
}
