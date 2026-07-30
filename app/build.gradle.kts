// M0 build-gate deviations from docs/plan.md M0.1/M0.2 (Risk 1 serialization-plugin
// fallback was NOT needed — AGP 9 built-in Kotlin + kotlin.plugin.serialization
// registered fine). Two unrelated dependency-resolution issues required version
// bumps in gradle/libs.versions.toml instead:
//  1. kotlin 2.2.10 -> 2.4.0: transitive deps (coil3 3.5.0's kotlin-stdlib
//     requirement via its own POM, and ktor 3.5.1 -> kotlinx-io -> kotlinx-serialization
//     -json-io 1.11.0) resolved kotlin-stdlib to 2.4.0 on the runtime classpath,
//     which the 2.2.0 compiler could not read (metadata version mismatch). Bumping
//     the project's Kotlin version to 2.4.0 (still >= AGP 9.2.1's built-in-Kotlin
//     minimum of 2.2.10) resolved it without touching coil/ktor versions.
//  2. coreKtx 1.19.0 -> 1.18.0, lifecycle 2.11.0 -> 2.10.0: both 1.19.0/2.11.0
//     ship AAR metadata requiring compileSdk >= 37, which does not exist yet in
//     the SDK Manager remote repo (only android-36.1 is installable). Downgraded
//     to the last release lines whose AAR metadata declares minCompileSdk <= 36
//     (core-ktx 1.18.0 -> 36, lifecycle 2.10.0 -> 35), keeping the plan's
//     compileSdk { release(36) { minorApiLevel = 1 } } block untouched.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.maik205.shoumeiplayer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.maik205.shoumeiplayer"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main") {
        // The approved redesign artwork is bundled as an Android asset so onboarding has a
        // deliberate offline backdrop before a Jellyfin server is available.
        assets.srcDir(rootProject.file("prototype/assets"))
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":mpvroid"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // M-B9: Icons.Filled.VideoLibrary (Libraries rail item, §3.1) is not in the core set.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    // android.jar's org.json is a stub that returns defaults under unitTests.isReturnDefaultValues,
    // which would make MpvTrackList's track-list parsing silently produce empty results in tests.
    // A real implementation on the unit-test classpath shadows the stub.
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
