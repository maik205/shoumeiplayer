// Dependency-resolution notes. The serialization-plugin fallback was not needed because
// AGP 9 built-in Kotlin + kotlin.plugin.serialization
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
//     (core-ktx 1.18.0 -> 36, lifecycle 2.10.0 -> 35), keeping the
//     compileSdk { release(36) { minorApiLevel = 1 } } block untouched.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

data class ShoumeiVersion(
    val name: String,
    val code: Int,
)

fun parseShoumeiVersion(value: String): ShoumeiVersion {
    val match = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")
        .matchEntire(value)
        ?: error("Shoumei versions must use MAJOR.MINOR.PATCH; received '$value'")
    val (major, minor, patch) = match.destructured.toList().map(String::toLong)
    require(minor <= 999 && patch <= 999) {
        "Minor and patch components must each be between 0 and 999"
    }
    val code = major * 1_000_000L + minor * 1_000L + patch
    require(code in 1..2_100_000_000L) {
        "Version '$value' maps to invalid Android versionCode $code"
    }
    return ShoumeiVersion(name = value, code = code.toInt())
}

val releaseTag = providers.environmentVariable("SHOUMEI_RELEASE_TAG")
    .orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
val releaseVersion = releaseTag?.let { tag ->
    require(Regex("""^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""").matches(tag)) {
        "Release tags must use vMAJOR.MINOR.PATCH; received '$tag'"
    }
    parseShoumeiVersion(tag.removePrefix("v"))
}
val developmentVersion = parseShoumeiVersion(
    providers.gradleProperty("shoumei.versionName").getOrElse("0.1.0"),
)
val appVersion = releaseVersion ?: developmentVersion
val mpvVersion = providers.gradleProperty("shoumei.mpvVersion").get()
parseShoumeiVersion(mpvVersion)

val signingEnvironment = mapOf(
    "path" to providers.environmentVariable("SHOUMEI_KEYSTORE_PATH").orNull,
    "storePassword" to providers.environmentVariable("SHOUMEI_KEYSTORE_PASSWORD").orNull,
    "keyAlias" to providers.environmentVariable("SHOUMEI_KEY_ALIAS").orNull,
    "keyPassword" to providers.environmentVariable("SHOUMEI_KEY_PASSWORD").orNull,
)
val hasAnySigningValue = signingEnvironment.values.any { !it.isNullOrBlank() }
val hasCompleteSigningConfiguration = signingEnvironment.values.all { !it.isNullOrBlank() }
require(!hasAnySigningValue || hasCompleteSigningConfiguration) {
    "Release signing requires SHOUMEI_KEYSTORE_PATH, SHOUMEI_KEYSTORE_PASSWORD, " +
        "SHOUMEI_KEY_ALIAS, and SHOUMEI_KEY_PASSWORD together"
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
        versionCode = appVersion.code
        versionName = if (releaseVersion != null) appVersion.name else "${appVersion.name}-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "RELEASE_TAG", "\"${releaseTag.orEmpty()}\"")
        buildConfigField("String", "MPV_VERSION", "\"$mpvVersion\"")
    }

    signingConfigs {
        if (hasCompleteSigningConfiguration) {
            create("release") {
                storeFile = file(signingEnvironment.getValue("path")!!)
                storePassword = signingEnvironment.getValue("storePassword")
                keyAlias = signingEnvironment.getValue("keyAlias")
                keyPassword = signingEnvironment.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            if (hasCompleteSigningConfiguration) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    bundle {
        language {
            // AppLocaleManager switches languages at runtime; keep every locale in the base APK.
            enableSplit = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main") {
        // The approved redesign artwork is bundled as an Android asset so onboarding has a
        // deliberate offline backdrop before a Jellyfin server is available.
        assets.directories.add(rootProject.file("prototype/assets").absolutePath)
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:player"))
    implementation(project(":core:jellyfin"))
    implementation(project(":core:data"))
    implementation(project(":feature:player"))
    implementation(project(":core:designsystem-tv"))
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
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
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
    testImplementation(testFixtures(project(":core:data")))
    // android.jar's org.json is a stub that returns defaults under unitTests.isReturnDefaultValues,
    // which would make MpvTrackList's track-list parsing silently produce empty results in tests.
    // A real implementation on the unit-test classpath shadows the stub.
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
