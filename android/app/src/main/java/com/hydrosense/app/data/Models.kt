package com.hydrosense.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Kotlin mirrors of the JSON returned by the existing HydroSense API.
// These map onto the same Supabase tables the web app uses
// (sites, devices, measurements, observations, alerts, assessments).

@Serializable
data class Site(
    val id: String,
    val name: String,
    val locality: String? = null,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("is_demo") val isDemo: Boolean = false,
    val status: String = "no_data",
    @SerialName("status_label") val statusLabel: String = "",
    val headline: String = "",
    val summary: String = "",
    @SerialName("latest_tds") val latestTds: Double? = null,
    @SerialName("latest_at") val latestAt: String? = null,
    @SerialName("baseline_tds") val baselineTds: Double? = null,
    @SerialName("deviation_percent") val deviationPercent: Double? = null,
    @SerialName("anomaly_score") val anomalyScore: Int = 0,
    @SerialName("active_alerts") val activeAlerts: Int = 0,
    // Present only on the single-site endpoint
    val citizen: CitizenCopy? = null,
    val trend: String? = null,
    val devices: List<Device> = emptyList(),
    @SerialName("observation_count") val observationCount: Int? = null,
)

@Serializable
data class CitizenCopy(
    val label: String = "",
    val headline: String = "",
    val summary: String = "",
    val why: String = "",
    val detail: String? = null,
    val action: String = "",
)

@Serializable
data class Device(
    @SerialName("device_id") val deviceId: String,
    @SerialName("site_id") val siteId: String,
    val status: String = "offline",
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val health: String = "",
)

@Serializable
data class SeriesPoint(val t: String, val tds: Double, val min: Double = tds, val max: Double = tds, val n: Int = 1)

@Serializable
data class Series(
    @SerialName("site_id") val siteId: String,
    val range: String,
    @SerialName("bucket_seconds") val bucketSeconds: Int = 3600,
    @SerialName("baseline_tds") val baselineTds: Double? = null,
    val points: List<SeriesPoint> = emptyList(),
)

@Serializable
data class AnomalyPoint(val t: String, val tds: Double, @SerialName("deviation_percent") val deviationPercent: Double = 0.0)

@Serializable
data class Analytics(
    @SerialName("site_id") val siteId: String = "",
    val state: String = "no_data",
    @SerialName("current_tds") val currentTds: Double? = null,
    @SerialName("baseline_tds") val baselineTds: Double? = null,
    val spread: Double? = null,
    @SerialName("deviation_percent") val deviationPercent: Double? = null,
    @SerialName("z_score") val zScore: Double? = null,
    val trend: String = "unknown",
    @SerialName("trend_percent_per_day") val trendPercentPerDay: Double? = null,
    @SerialName("rate_of_change_ppm_per_hour") val rateOfChange: Double? = null,
    @SerialName("rolling_mean_24h") val rollingMean24h: Double? = null,
    @SerialName("rolling_std_24h") val rollingStd24h: Double? = null,
    val persistence: Double = 0.0,
    @SerialName("anomaly_score") val anomalyScore: Int = 0,
    @SerialName("score_parts") val scoreParts: Map<String, Double> = emptyMap(),
    @SerialName("history_days") val historyDays: Double = 0.0,
    @SerialName("latest_at") val latestAt: String? = null,
    val stale: Boolean = false,
    val anomalies: List<AnomalyPoint> = emptyList(),
    val methodology: String = "",
)

@Serializable
data class Observation(
    val id: String = "",
    @SerialName("site_id") val siteId: String,
    @SerialName("site_name") val siteName: String = "",
    val timestamp: String = "",
    @SerialName("water_clarity") val waterClarity: String = "not_sure",
    val algae: String = "not_sure",
    val waste: String = "not_sure",
    val odor: String = "not_sure",
    @SerialName("aquatic_life") val aquaticLife: String = "not_sure",
    @SerialName("image_url") val imageUrl: String? = null,
    val comment: String? = null,
    @SerialName("is_demo") val isDemo: Boolean = false,
)

/** Body of POST /api/observations: identical to what the web app sends. */
@Serializable
data class NewObservation(
    @SerialName("site_id") val siteId: String,
    @SerialName("water_clarity") val waterClarity: String,
    val algae: String,
    val waste: String,
    val odor: String,
    @SerialName("aquatic_life") val aquaticLife: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    val comment: String? = null,
)

@Serializable
data class Alert(
    val id: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("site_name") val siteName: String = "",
    val timestamp: String = "",
    val type: String = "",
    val severity: String = "low",
    val title: String = "",
    val message: String = "",
    val detail: String? = null,
    val recommendation: String? = null,
    val evidence: Map<String, JsonElement> = emptyMap(),
    val status: String = "active",
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolution_note") val resolutionNote: String? = null,
    @SerialName("is_demo") val isDemo: Boolean = false,
)

@Serializable
data class Factor(val key: String = "", val icon: String = "", val label: String = "", val detail: String = "")

@Serializable
data class AssessmentSnapshot(
    val timestamp: String = "",
    @SerialName("risk_level") val riskLevel: String = "unknown",
    val label: String = "",
    val confidence: Int = 0,
    @SerialName("anomaly_score") val anomalyScore: Int = 0,
)

@Serializable
data class Assessment(
    @SerialName("site_id") val siteId: String,
    @SerialName("site_name") val siteName: String = "",
    val timestamp: String = "",
    @SerialName("risk_level") val riskLevel: String = "unknown",
    val assessment: String = "",
    val confidence: Int = 0,
    @SerialName("anomaly_score") val anomalyScore: Int = 0,
    @SerialName("contributing_factors") val factors: List<Factor> = emptyList(),
    val explanation: String = "",
    val recommendation: String = "",
    val evidence: Map<String, JsonElement> = emptyMap(),
    /** "rules" = deterministic engine, "llm" = wording from a language model. */
    val source: String = "rules",
    @SerialName("llm_enabled") val llmEnabled: Boolean = false,
    val disclaimer: String = "",
    val history: List<AssessmentSnapshot> = emptyList(),
)

@Serializable
data class Stats(
    val mode: String = "demo",
    val sites: Int = 0,
    @SerialName("active_sensors") val activeSensors: Int = 0,
    val observations: Int = 0,
    @SerialName("potential_anomalies") val potentialAnomalies: Int = 0,
)

@Serializable
data class Health(
    val status: String = "",
    val store: String = "",
    val database: String = "",
    @SerialName("llm_enhancement") val llmEnhancement: Boolean = false,
    val version: String = "",
)

@Serializable
data class ApiError(val detail: JsonElement? = null)

/** A report the user finished but that could not be sent yet. */
@Serializable
data class PendingReport(
    val id: String,
    val observation: NewObservation,
    val siteName: String,
    val photoFile: String? = null,
    val createdAt: Long,
)
