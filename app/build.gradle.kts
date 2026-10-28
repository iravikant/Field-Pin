import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Optional overrides live in local.properties (git-ignored). See local.properties.example.
// Reading through `providers` keeps the file tracked as a configuration-cache input.
val localPropsText: String? =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
val localProps = Properties().apply { localPropsText?.let { load(StringReader(it)) } }

fun localProp(key: String, default: String): String = localProps.getProperty(key, default).trim()

// Google Maps key: read from local.properties (git-ignored), never from source. The project still
// builds without it; the map then shows a "key missing" notice instead of tiles.
val mapsApiKey = localProp("MAPS_API_KEY", "")

android {
    namespace = "com.fieldpin"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.fieldpin"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "MAPS_KEY_CONFIGURED", "${mapsApiKey.isNotBlank()}")
        // Tile server for the saved offline map (XYZ template). Google's own tiles may not be
        // stored, so offline uses OpenStreetMap-style tiles. Swap for a provider whose terms
        // allow offline use in production (self-hosted, MapTiler, Stadia, ...).
        buildConfigField(
            "String", "TILE_URL_TEMPLATE",
            "\"${localProp("TILE_URL_TEMPLATE", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")}\"",
        )
    }

    buildTypes {
        debug {
            // Base URLs are build configuration, never source code. The in-app mock server
            // only uses it as its identity; a real backend client would target it.
            buildConfigField("String", "API_BASE_URL", "\"${localProp("API_BASE_URL_DEBUG", "https://mock.debug.fieldpin.test/v1/")}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${localProp("API_BASE_URL_RELEASE", "https://mock.fieldpin.test/v1/")}\"")
            // No keystore is committed, so the demo release APK is signed with the debug key.
            signingConfig = signingConfigs.getByName("debug")
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.play.services.location)
    implementation(libs.maps.compose) // Google Maps SDK for Android + Compose wrapper
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
