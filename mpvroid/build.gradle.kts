plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.maik205.mpvroid"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-std=c++17"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        baseline = file("lint-baseline.xml")
    }

    // ANDROID_STL=c++_shared makes CMake copy its own libc++_shared.so into the native build
    // output alongside the prebuilt one already checked into jniLibs/ (needed by the prebuilt
    // libmpv/FFmpeg .so files) -- both are the same NDK r29 STL build, so either is fine to keep;
    // AGP just refuses to silently pick one without this. Only surfaces on a from-scratch build
    // (mergeReleaseNativeLibs on a clean checkout), which local incremental builds never hit.
    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.junit)
}
