import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.library)
}

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

val mpvNativeBundleVersion = providers.gradleProperty("shoumei.mpvNativeBundleVersion")
val mpvNativeBundleUrl = providers.gradleProperty("shoumei.mpvNativeBundleUrl")
val mpvNativeBundleSha256 = providers.gradleProperty("shoumei.mpvNativeBundleSha256")
val generatedMpvNativeRoot = layout.buildDirectory.dir("generated/mpvNative")
val generatedMpvJniLibs = generatedMpvNativeRoot.map { directory -> directory.dir("jniLibs") }

val provisionMpvNativeLibraries = tasks.register("provisionMpvNativeLibraries") {
    group = "build setup"
    description = "Downloads and verifies the pinned official mpv Android libraries."

    inputs.property("bundleVersion", mpvNativeBundleVersion)
    inputs.property("bundleUrl", mpvNativeBundleUrl)
    inputs.property("bundleSha256", mpvNativeBundleSha256)
    outputs.dir(generatedMpvNativeRoot)

    doLast {
        val version = mpvNativeBundleVersion.get()
        val url = mpvNativeBundleUrl.get()
        val expectedSha256 = mpvNativeBundleSha256.get().lowercase()
        require(expectedSha256.matches(Regex("[0-9a-f]{64}"))) {
            "shoumei.mpvNativeBundleSha256 must be a lowercase SHA-256 digest"
        }

        val archiveName = URI(url).path.substringAfterLast('/')
        require(archiveName.isNotBlank()) { "Native bundle URL must end with a file name: $url" }
        val cacheDirectory = File(
            gradle.gradleUserHomeDir,
            "caches/shoumei-player/mpv-native/$version",
        )
        val archive = File(cacheDirectory, archiveName)

        if (!archive.isFile || archive.sha256() != expectedSha256) {
            cacheDirectory.mkdirs()
            val temporary = File(cacheDirectory, "$archiveName.part")
            temporary.delete()

            logger.lifecycle("Downloading mpv native bundle $version")
            val connection = URI(url).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 180_000
                setRequestProperty("User-Agent", "Shoumei-Player-Gradle")
            }
            connection.getInputStream().buffered().use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }

            val actualSha256 = temporary.sha256()
            check(actualSha256 == expectedSha256) {
                temporary.delete()
                "Native bundle checksum mismatch: expected $expectedSha256, received $actualSha256"
            }
            Files.move(
                temporary.toPath(),
                archive.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        val actualSha256 = archive.sha256()
        check(actualSha256 == expectedSha256) {
            "Cached native bundle checksum mismatch: expected $expectedSha256, received $actualSha256"
        }

        val outputRoot = generatedMpvNativeRoot.get().asFile
        check(!outputRoot.exists() || outputRoot.deleteRecursively()) {
            "Could not clear generated native directory: $outputRoot"
        }
        outputRoot.mkdirs()
        val canonicalOutputRoot = outputRoot.canonicalFile.toPath()

        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(outputRoot, entry.name).canonicalFile
                check(target.toPath().startsWith(canonicalOutputRoot)) {
                    "Native bundle contains an invalid path: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.outputStream().buffered().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }

        val requiredLibraries = listOf(
            "libavcodec.so",
            "libavdevice.so",
            "libavfilter.so",
            "libavformat.so",
            "libavutil.so",
            "libc++_shared.so",
            "libmpv.so",
            "libswresample.so",
            "libswscale.so",
        )
        for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
            for (library in requiredLibraries) {
                val nativeLibrary = generatedMpvJniLibs.get().file("$abi/$library").asFile
                check(nativeLibrary.isFile && nativeLibrary.length() > 0L) {
                    "Native bundle is missing $abi/$library"
                }
            }
        }
    }
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
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DMPV_JNI_LIBS_DIR=${generatedMpvJniLibs.get().asFile.invariantSeparatorsPath}"
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

    sourceSets {
        getByName("main").jniLibs.directories.add(
            generatedMpvJniLibs.get().asFile.invariantSeparatorsPath,
        )
    }

    // ANDROID_STL=c++_shared makes CMake copy its own libc++_shared.so into the native build
    // output alongside the pinned NDK r29 copy in the provisioned bundle. The bundle copy is
    // needed by libmpv and FFmpeg; AGP requires an explicit conflict policy for the duplicate.
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
    androidTestImplementation(libs.androidx.test.runner)
}

tasks.configureEach {
    val needsMpvNativeLibraries =
        name == "preBuild" ||
            name.startsWith("configureCMake") ||
            (name.startsWith("merge") &&
                (name.endsWith("JniLibFolders") || name.endsWith("NativeLibs")))
    if (needsMpvNativeLibraries) {
        dependsOn(provisionMpvNativeLibraries)
    }
}
