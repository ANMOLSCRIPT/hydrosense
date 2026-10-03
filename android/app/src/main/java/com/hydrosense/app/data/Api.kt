package com.hydrosense.app.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** The existing HydroSense REST API (FastAPI). The same endpoints the web app calls. */
interface HydroSenseApi {
    @GET("api/health") suspend fun health(): Health
    @GET("api/stats") suspend fun stats(@Query("mode") mode: String): Stats
    @GET("api/sites") suspend fun sites(@Query("mode") mode: String): List<Site>
    @GET("api/sites/{id}") suspend fun site(@Path("id") id: String): Site
    @GET("api/sites/{id}/measurements") suspend fun series(@Path("id") id: String, @Query("range") range: String): Series
    @GET("api/sites/{id}/analytics") suspend fun analytics(@Path("id") id: String): Analytics
    @GET("api/observations") suspend fun observations(
        @Query("mode") mode: String, @Query("site_id") siteId: String? = null, @Query("limit") limit: Int = 30,
    ): List<Observation>
    @POST("api/observations") suspend fun addObservation(@Body body: NewObservation): Observation
    @GET("api/alerts") suspend fun alerts(@Query("mode") mode: String, @Query("site_id") siteId: String? = null): List<Alert>
    @GET("api/assessment/{id}") suspend fun assessment(@Path("id") id: String): Assessment
}

val HydroJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

fun createApi(baseUrl: String, client: OkHttpClient): HydroSenseApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(HydroJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(HydroSenseApi::class.java)
