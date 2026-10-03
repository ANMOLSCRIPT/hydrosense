package com.hydrosense.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import com.hydrosense.app.data.Repository
import com.hydrosense.app.data.SharedPrefsStore
import com.hydrosense.app.data.createApi
import com.hydrosense.app.data.createHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class HydroSenseApp : Application() {
    lateinit var repository: Repository
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val http = createHttpClient()
        repository = Repository(
            api = createApi(BuildConfig.API_BASE_URL, http),
            http = http,
            cacheDir = cacheDir,
            filesDir = filesDir,
            prefs = SharedPrefsStore(this),
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
        )
        // OpenStreetMap tiles: identify the app, keep the tile cache inside app storage.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osm-tiles")
        }
        // Send any reports saved while offline as soon as a connection returns.
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { runCatching { repository.syncPending() } }
            }
        })
    }
}
