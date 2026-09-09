plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

composeCompiler {
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))
}

android {
    namespace = "com.maik205.shoumeiplayer.core.designsystem.tv"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28
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
    }
}

dependencies {
    api(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
