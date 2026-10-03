import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Client-safe configuration only. Values come from android/hydrosense.properties,
// falling back to the public VITE_* values in the repository's .env (the same
// ones the web app ships in its bundle). Server secrets are never read here.
fun clientConfig(): Map<String, String> {
    val out = mutableMapOf("API_BASE_URL" to "https://hydrosense-beta.vercel.app/", "SUPABASE_URL" to "", "SUPABASE_ANON_KEY" to "")
    val local = rootProject.file("hydrosense.properties")
    if (local.exists()) {
        val p = Properties().apply { local.inputStream().use { load(it) } }
        out.keys.toList().forEach { k -> p.getProperty(k)?.takeIf { it.isNotBlank() }?.let { out[k] = it.trim() } }
    }
    val env = rootProject.file("../.env")
    if (env.exists()) {
        val allowed = mapOf("VITE_SUPABASE_URL" to "SUPABASE_URL", "VITE_SUPABASE_ANON_KEY" to "SUPABASE_ANON_KEY")
        env.readLines().forEach { line ->
            val key = line.substringBefore("=").trim()
            val target = allowed[key] ?: return@forEach
            val value = line.substringAfter("=").trim().trim('"', '\'')
            if (out[target].isNullOrBlank() && value.isNotBlank()) out[target] = value
        }
    }
    return out
}

android {
    namespace = "com.hydrosense.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "com.hydrosense.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        clientConfig().forEach { (k, v) -> buildConfigField("String", k, "\"$v\"") }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // Forward -Dhydrosense.live=true to opt in to the read-only live backend tests.
            it.systemProperty("hydrosense.live", System.getProperty("hydrosense.live") ?: "false")
            it.maxParallelForks = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
