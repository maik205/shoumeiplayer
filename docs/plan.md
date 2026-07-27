# Shoumei Player — Implementation Plan

Execution plan for `docs/design.md`. Each task is self-contained: an implementer
with no other context reads §Contracts (appendix A) + its task block and can do
the work. **Do not deviate from appendix A signatures** — parallel tasks depend
on them.

Repo root: `C:\Users\maik\Documents\Projects\shoumeiplayer`. All paths below are
repo-relative. Package root `com.maik205.shoumeiplayer` lives at
`app/src/main/java/com/maik205/shoumeiplayer/`; unit tests at
`app/src/test/java/com/maik205/shoumeiplayer/`. Shorthand used below:
`src/**` = `app/src/main/java/com/maik205/shoumeiplayer/**`,
`test/**` = `app/src/test/java/com/maik205/shoumeiplayer/**`.

Verification commands (run from repo root, Windows):
- `.\gradlew.bat :app:assembleDebug` — compile
- `.\gradlew.bat :app:testDebugUnitTest` — JVM unit tests

## Wave / parallelism overview

| Wave | Tasks | Notes |
|---|---|---|
| W0a | M0.1 | sole owner of `gradle/libs.versions.toml` |
| W0b | M0.2 ∥ M0.3 | build scripts ∥ manifest/res |
| W0c | M0.4 | app + DI skeleton |
| W1a | M1.1 ∥ M1.2 ∥ M1.4 ∥ M2.1 | no cross-deps |
| W1b | M1.3 ∥ M2.2 ∥ M2.3 | |
| W1c | M1.5 ∥ M1.6 ∥ M1.7 | three separate repo files |
| W1d | M1.8 ∥ M1.9 | tests ∥ AppContainer wiring (sole owner) |
| W3a | M3.1 ∥ M3.2 | routes+NavHost ∥ components |
| W3b | M3.3 ∥ M3.4 ∥ M3.5 | three screens |
| W4a | M4.1 ∥ M4.2 ∥ M4.3 | Library ∥ Detail ∥ Search screens |
| W4b | M4.4 | NavHost extension (sole owner this wave) |
| W5a | M5.1 ∥ M5.2 | reporter ∥ player VM |
| W5b | M5.3 | player screen + OSD |
| W5c | M5.4 | NavHost extension (sole owner this wave) |
| W6a | M6.1 ∥ M6.2 ∥ M6.3 | settings ∥ 401 ∥ focus pass |
| W6b | M6.4 | final verification |

**Shared-file ownership rule:** `gradle/libs.versions.toml` → M0.1 only.
`app/build.gradle.kts` → M0.2 only (later tasks must not add deps; if one is
missing, stop and report). `src/di/AppContainer.kt` → M0.4, then M1.9, then
M2.4-equivalent folded into M1.9, then M6.1. `src/ui/navigation/NavGraph.kt` →
M3.1, then M4.4, then M5.4, then M6.1 — exactly one owner per wave.

---

# M0 — Foundation

## M0.1 — Version catalog rewrite
**Depends:** none. **Files:** `gradle/libs.versions.toml` (replace whole file).

Replace with the content below verbatim. Keep `agp = "9.2.1"` and
`kotlin = "2.2.10"`. Remove `appcompat` and `tv-foundation` (tv-foundation was
merged into tv-material/compose-foundation and is not published as stable).

```toml
[versions]
agp = "9.2.1"
kotlin = "2.2.10"
composeBom = "2026.06.01"          # -> compose ui/foundation 1.11.4, material3 1.4.0
tvMaterial = "1.1.0"
coreKtx = "1.19.0"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
navigationCompose = "2.9.8"
ktor = "3.5.1"
kotlinxSerialization = "1.9.0"
coroutines = "1.11.0"
coil = "3.5.0"
datastore = "1.2.1"
junit = "4.13.2"
androidxJunit = "1.3.0"
espresso = "3.7.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
androidx-tv-material = { group = "androidx.tv", name = "tv-material", version.ref = "tvMaterial" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { group = "io.ktor", name = "ktor-client-logging", version.ref = "ktor" }
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

**Verify:** `.\gradlew.bat :app:assembleDebug` will still fail until M0.2 — instead
run `.\gradlew.bat --stop; .\gradlew.bat help` (must succeed, proving TOML parses).
**Done:** file parses, no `appcompat`/`tvFoundation` entries remain.

## M0.2 — Build scripts
**Depends:** M0.1. **Files:** `build.gradle.kts`, `app/build.gradle.kts`,
`gradle.properties`.

Root `build.gradle.kts` plugins block:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```
**Do NOT add `org.jetbrains.kotlin.android`.** AGP 9.0+ ships built-in Kotlin
(`android.builtInKotlin` defaults to true, requires KGP ≥ 2.2.10 — we are on
2.2.10); applying the standalone Kotlin Android plugin conflicts with it. This is
why the scaffold compiles Kotlin today with only the Compose plugin applied.

`app/build.gradle.kts`:
- plugins: `android.application`, `kotlin.compose`, `kotlin.serialization`
- keep the existing `compileSdk { version = release(36) { minorApiLevel = 1 } }`
  block verbatim — it is valid AGP 9 DSL and already builds. Do not "simplify" it.
- `defaultConfig { applicationId = "com.maik205.shoumeiplayer"; minSdk = 28;
  targetSdk = 36; versionCode = 1; versionName = "1.0";
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`
- `buildTypes { debug { isMinifyEnabled = false }; release { isMinifyEnabled = false;
  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }`
- `compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }`
  (AGP 9 requires JDK 17+; local JDK is 25.) Do **not** add a top-level
  `kotlin { compilerOptions { jvmTarget … } }` block up front — under built-in
  Kotlin the extension may not exist. Only if the build fails with a
  "Inconsistent JVM-target" error, add
  `androidComponents { }`-free fallback: set `kotlin.jvmToolchain(17)` in
  `gradle.properties` via `kotlin.jvm.target.validation.mode=warning`, or flip to
  the explicit-KGP path in Risk 1.
- `buildFeatures { compose = true; buildConfig = true }`
- `testOptions { unitTests.isReturnDefaultValues = true }`
- dependencies (implementation unless noted): compose-bom platform, compose ui,
  ui-graphics, ui-tooling-preview, compose-foundation, compose-material3
  (needed for TextField/Slider/LinearProgressIndicator — tv-material has none),
  material-icons-core, tv-material,
  core-ktx, activity-compose, lifecycle-runtime-ktx, lifecycle-runtime-compose,
  lifecycle-viewmodel-compose, navigation-compose, kotlinx-serialization-json,
  kotlinx-coroutines-android, ktor-client-core, ktor-client-okhttp,
  ktor-client-content-negotiation, ktor-serialization-kotlinx-json,
  ktor-client-logging, coil-compose, coil-network-okhttp, datastore-preferences.
  `debugImplementation`: ui-tooling, ui-test-manifest.
  `testImplementation`: junit, kotlin-test, kotlinx-coroutines-test, ktor-client-mock.
  `androidTestImplementation`: compose-bom platform, ui-test-junit4, androidx-junit, espresso-core.
- **Remove** `androidx.appcompat` and `androidx.tv:tv-foundation`.

`gradle.properties`: append `android.useAndroidX=true`,
`org.gradle.parallel=true`, `org.gradle.caching=true`, and bump
`org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`.

**Verify:** `.\gradlew.bat :app:assembleDebug` — expected to fail only on
`Theme.AppCompat` in `themes.xml` (fixed by M0.3) if M0.3 has not landed; after
both, must pass. **Done:** assembleDebug green once M0.3 is merged.

## M0.3 — Manifest, network security config, resources
**Depends:** M0.1. **Files:** `app/src/main/AndroidManifest.xml`,
`app/src/main/res/xml/network_security_config.xml` (new),
`app/src/main/res/values/themes.xml`, `app/src/main/res/values/strings.xml`.

Manifest:
- add `<uses-permission android:name="android.permission.INTERNET" />` and
  `android.permission.ACCESS_NETWORK_STATE` before `<uses-feature>`.
- keep both `uses-feature` blocks (`touchscreen` / `leanback`, `required=false`).
- `<application>` gains `android:name=".ShoumeiApp"`,
  `android:networkSecurityConfig="@xml/network_security_config"`,
  `android:usesCleartextTraffic="true"`, `android:hardwareAccelerated="true"`.
- `MainActivity` gains
  `android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode"`,
  `android:launchMode="singleTask"`, `android:screenOrientation="landscape"`,
  `android:theme="@style/Theme.ShoumeiPlayer"`.

`network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

`themes.xml`: parent must not be AppCompat (dependency removed):
```xml
<resources>
    <style name="Theme.ShoumeiPlayer" parent="@android:style/Theme.DeviceDefault.NoActionBar" />
</resources>
```
`strings.xml`: keep `app_name`, add strings used by later screens:
`server_url_hint`, `connect`, `username`, `password`, `sign_in`, `sign_out`,
`continue_watching`, `next_up`, `latest_in`, `my_media`, `play`, `resume`,
`search`, `settings`, `retry`, `loading`, `audio_track`, `subtitle_track`, `off`.

**Verify:** `.\gradlew.bat :app:assembleDebug` (after M0.2). **Done:** app builds,
manifest merger reports no errors, `minSdk 28` present in merged manifest.

## M0.4 — Application, AppContainer skeleton, MainActivity
**Depends:** M0.2, M0.3. **Files (new/modify):**
`src/ShoumeiApp.kt`, `src/di/AppContainer.kt`, `src/di/LocalAppContainer.kt`,
`src/MainActivity.kt` (rewrite), `src/ui/theme/Theme.kt` (minor).

- `ShoumeiApp : Application` with `lateinit var container: AppContainer`,
  created in `onCreate()` as `AppContainer(this)`.
- `AppContainer` — appendix A.6. In this task it only holds
  `applicationContext` and a `TODO`-free empty body plus `val appName = "Shoumei Player"`,
  `val appVersion = BuildConfig.VERSION_NAME`. M1.9 fills it in.
- `LocalAppContainer` = `staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }`.
- `MainActivity`: `ComponentActivity`, `enableEdgeToEdge()` not required; body is
  ```kotlin
  setContent {
      CompositionLocalProvider(LocalAppContainer provides (application as ShoumeiApp).container) {
          ShoumeiPlayerTheme { Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) { /* NavGraph added in M3.1 */ } }
      }
  }
  ```
  Delete `Greeting`/`GreetingPreview`.
- `Theme.kt`: force dark scheme only (TV): `ShoumeiPlayerTheme(content)` with
  `darkColorScheme(...)`; drop the `isInDarkTheme` parameter's light branch usage
  but keep the parameter defaulting to `true`.

**Verify:** `.\gradlew.bat :app:assembleDebug`. **Done:** app installs and shows a
blank dark Surface.

---

# M1 — Data layer

## M1.1 — SessionStore
**Depends:** M0.4. **Files:** `src/data/session/Session.kt`,
`src/data/session/SessionStore.kt`.

Implement appendix A.1 exactly. Notes:
- DataStore instance created inside `SessionStore` via
  `PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("shoumei_session") }`.
- Keys: `server_url`, `access_token`, `user_id`, `user_name`, `device_id`.
- `deviceId()` generates `UUID.randomUUID().toString()` on first call and persists it.
- `normalizeServerUrl(raw)` — top-level `fun` in `SessionStore.kt`: trims, adds
  `http://` when no scheme, strips trailing `/`, strips a trailing `/web/index.html`
  fragment. Must be `internal` and unit-testable.
- `session: Flow<Session?>` emits null unless serverUrl+token+userId are all present.

**Verify:** `.\gradlew.bat :app:assembleDebug`. **Done:** compiles; no Android
framework calls outside the constructor (so `normalizeServerUrl` is JVM-testable).

## M1.2 — API DTOs
**Depends:** M0.4. **Files (all new, package `com.maik205.shoumeiplayer.data.api.dto`):**
`src/data/api/dto/BaseItemDto.kt`, `QueryResult.kt`, `SystemDto.kt`, `AuthDto.kt`,
`PlaybackDto.kt`, `DeviceProfileDto.kt`.

All classes `@Serializable`, PascalCase JSON names matched via `@SerialName`
(Jellyfin uses PascalCase; do **not** rely on Kotlin property casing). Every field
nullable with a `null`/empty default. Exact field lists: appendix A.2.

**Verify:** `.\gradlew.bat :app:assembleDebug`. **Done:** compiles; `BaseItemDto`
has exactly the fields listed in A.2 (no more — trimming is the point).

## M1.3 — JellyfinClient (Ktor)
**Depends:** M1.1, M1.2. **Files:** `src/data/api/JellyfinClient.kt`,
`src/data/ApiResult.kt`.

- `ApiResult`/`ApiError` per appendix A.3 (put in `src/data/ApiResult.kt`).
- `JellyfinClient(private val sessionStore: SessionStore, private val appName: String,
  private val appVersion: String, engine: HttpClientEngine? = null)`.
  The `engine` parameter exists so tests can inject `MockEngine`; when null use
  `OkHttp`.
- Ktor config: `install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true;
  isLenient = true; explicitNulls = false; encodeDefaults = true }) }`,
  `install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000 }`,
  `defaultRequest { }` is NOT used for the base URL (base URL is per-call, since it
  is user-supplied at runtime).
- `suspend fun authHeader(): String` builds
  `MediaBrowser Client="$appName", Device="Android TV", DeviceId="$deviceId", Version="$appVersion"`
  plus `, Token="$token"` when a token exists.
- Public API (appendix A.4): `get`, `post`, `postEmpty`, `resolveUrl`, `currentSession`.
  All request helpers map exceptions to `ApiResult.Failure`; HTTP 401 →
  `ApiError.Unauthorized`.
- `resolveUrl(path: String, params: Map<String, String?>): String` — builds an
  absolute URL against the stored serverUrl; used by ImageUrlBuilder and stream URLs.

**Verify:** `.\gradlew.bat :app:assembleDebug`. **Done:** compiles, no
`kotlinx.coroutines.runBlocking` anywhere.

## M1.4 — ImageUrlBuilder + Ticks utils (+ tests)
**Depends:** M0.4. **Files:** `src/data/ImageUrlBuilder.kt`, `src/util/Ticks.kt`,
`test/data/ImageUrlBuilderTest.kt`, `test/util/TicksTest.kt`.

```kotlin
object Ticks {
    const val PER_MS = 10_000L
    fun toMs(ticks: Long): Long
    fun fromMs(ms: Long): Long
    fun formatDuration(ms: Long): String   // "1:23:45" or "23:45"
}
class ImageUrlBuilder(private val serverUrlProvider: () -> String?) {
    fun primary(itemId: String, tag: String?, maxWidth: Int = 400): String?
    fun backdrop(itemId: String, tag: String?, maxWidth: Int = 1280): String?
    fun thumb(itemId: String, tag: String?, maxWidth: Int = 640): String?
    fun image(itemId: String, type: String, tag: String?, maxWidth: Int): String?
}
```
`image` returns `null` when serverUrl is null; emits
`{server}/Items/{id}/Images/{type}?maxWidth={w}&quality=90` and appends
`&tag={tag}` only when tag is non-null.

Tests: null server → null; tag present/absent; ticks round-trip
(`fromMs(toMs(t)) == t` for multiples of 10_000); `formatDuration(3_723_000) == "1:02:03"`,
`formatDuration(143_000) == "2:23"`.

**Verify:** `.\gradlew.bat :app:testDebugUnitTest`. **Done:** both test classes pass.

## M1.5 — AuthRepository
**Depends:** M1.3. **Files:** `src/data/repo/AuthRepository.kt`.
Signatures: appendix A.5. Endpoints: `GET /System/Info/Public` (validate; call
with an explicit `baseUrl` override since no server is stored yet — add an
optional `baseUrlOverride: String? = null` parameter to `JellyfinClient.get`),
`POST /Users/AuthenticateByName` body `{"Username":..,"Pw":..}`.
On success write serverUrl+token+userId+userName to `SessionStore`.
`logout()` calls `sessionStore.clearAuth()`.

**Verify:** `assembleDebug`. **Done:** compiles.

## M1.6 — LibraryRepository
**Depends:** M1.3. **Files:** `src/data/repo/LibraryRepository.kt`.
Signatures: appendix A.5. Endpoint mapping:
| Method | Endpoint | Wrapper |
|---|---|---|
| `userViews` | `GET /UserViews?userId=` | `QueryResult` |
| `resumeItems` | `GET /UserItems/Resume?userId=&limit=&mediaTypes=Video&enableUserData=true&fields=Overview` | `QueryResult` |
| `nextUp` | `GET /Shows/NextUp?userId=&limit=&enableUserData=true` | `QueryResult` |
| `latest` | `GET /Items/Latest?userId=&parentId=&limit=&enableUserData=true` | **bare array** |
| `items` | `GET /Items?userId=&parentId=&includeItemTypes=&recursive=&sortBy=&sortOrder=&filters=&startIndex=&limit=&searchTerm=&fields=Overview&enableUserData=true&imageTypeLimit=1&enableImageTypes=Primary,Backdrop,Thumb` | `QueryResult` |
| `item` | `GET /Items/{id}?userId=` | bare object |
| `seasons` | `GET /Shows/{seriesId}/Seasons?userId=&enableUserData=true` | `QueryResult` |
| `episodes` | `GET /Shows/{seriesId}/Episodes?userId=&seasonId=&enableUserData=true&fields=Overview` | `QueryResult` |
| `search` | `GET /Items?searchTerm=&recursive=true&includeItemTypes=Movie,Series,Episode&limit=` | `QueryResult` |
Array-valued query params are joined with `,`. `userId` comes from
`client.currentSession()?.userId`; if null return `ApiResult.Failure(ApiError.Unauthorized)`.

**Verify:** `assembleDebug`. **Done:** compiles; no endpoint from the table missing.

## M1.7 — PlaybackRepository + DeviceProfile
**Depends:** M1.3. **Files:** `src/data/repo/PlaybackRepository.kt`,
`src/data/api/ShoumeiDeviceProfile.kt`.

`ShoumeiDeviceProfile.build(): DeviceProfileDto` — a permissive profile:
- `Name = "Shoumei Player"`, `MaxStreamingBitrate = 400_000_000`
- `DirectPlayProfiles`: one entry per container in
  `["mkv","mp4","webm","avi","ts","mov","flv","m4v"]` with
  `Type="Video"`, `VideoCodec="h264,hevc,av1,vp9,mpeg4,mpeg2video,vc1"`,
  `AudioCodec="aac,ac3,eac3,opus,flac,mp3,dts,truehd,vorbis,pcm"`
- `TranscodingProfiles`: single fallback `{Container="ts", Type="Video",
  VideoCodec="h264", AudioCodec="aac,ac3", Protocol="hls", Context="Streaming",
  MaxAudioChannels="6", MinSegments=1, BreakOnNonKeyFrames=true}`
- `SubtitleProfiles`: `subrip`, `ass`, `ssa`, `pgssub`, `vtt` with `Method="Embed"`,
  plus `subrip`/`vtt` with `Method="External"`
- `CodecProfiles`: empty list.

`resolve(...)`: `POST /Items/{itemId}/PlaybackInfo?userId=` with body
`PlaybackInfoDto(deviceProfile, startTimeTicks, maxStreamingBitrate=400_000_000,
enableDirectPlay=true, enableDirectStream=true, enableTranscoding=true,
allowVideoStreamCopy=true, allowAudioStreamCopy=true, mediaSourceId=...)`.
Pick source: first with `SupportsDirectPlay || SupportsDirectStream`, else first.
- direct → `client.resolveUrl("/Videos/$itemId/stream", mapOf("static" to "true",
  "mediaSourceId" to src.id, "playSessionId" to playSessionId,
  "api_key" to token, "deviceId" to deviceId))`, playMethod `"DirectPlay"`.
- transcode → `client.resolveUrl(src.transcodingUrl!!)` (already a relative URL with
  query string; append `api_key` if absent), playMethod `"Transcode"`.
Map `MediaStream` entries to `PlayerTrack` (see A.7): audio = `Type=="Audio"`,
subtitle = `Type=="Subtitle"`, `id = Index`, `label = DisplayTitle ?: "$Codec ${Language.orEmpty()}"`.
Return `ResolvedPlayback` (A.5).

Reporting: `POST /Sessions/Playing`, `/Sessions/Playing/Progress`,
`/Sessions/Playing/Stopped` — bodies from `PlaybackReport` (A.5) mapped to the
DTOs from M1.2; all return `ApiResult<Unit>` and must never throw.

**Verify:** `assembleDebug`. **Done:** compiles.

## M1.8 — Repository unit tests (MockEngine)
**Depends:** M1.5, M1.6, M1.7. **Files:**
`test/data/FakeJellyfin.kt`, `test/data/AuthRepositoryTest.kt`,
`test/data/LibraryRepositoryTest.kt`, `test/data/PlaybackRepositoryTest.kt`,
`test/data/DtoDeserializationTest.kt`,
`app/src/test/resources/fixtures/*.json` (hand-written fixtures:
`system_info_public.json`, `auth_result.json`, `items_query.json`,
`playback_info.json`).

`FakeJellyfin` builds a `JellyfinClient` over Ktor `MockEngine` with a
route→(status, body) map and a fake `SessionStore`-like seam. If `SessionStore`
cannot be faked without Android, extract an interface `SessionProvider`
(`suspend fun current(): Session?`) in M1.1 — **M1.1 must define it**; `SessionStore`
implements it and `JellyfinClient` depends on `SessionProvider`.

Assertions: DTO fields deserialize from PascalCase fixtures; 401 →
`ApiError.Unauthorized`; `latest` parses a bare array; `resolve` picks the
direct-play source and builds a URL containing `static=true`.

**Verify:** `.\gradlew.bat :app:testDebugUnitTest`. **Done:** all tests green.

## M1.9 — AppContainer wiring
**Depends:** M1.5, M1.6, M1.7, M2.2, M2.3. **Files:** `src/di/AppContainer.kt`
(sole owner this wave).
Fill in every property of appendix A.6 with `by lazy`. `playerEngine` selection:
```kotlin
val playerEngine: PlayerEngine by lazy {
    if (BuildConfig.DEBUG) SimulatedPlayerEngine() else MplayerEngine()
}
```
**Verify:** `assembleDebug` + `testDebugUnitTest`. **Done:** M1 milestone gate — both green.

---

# M2 — Player shim

## M2.1 — PlayerEngine contract
**Depends:** M0.4. **Files:** `src/player/PlayerEngine.kt`,
`src/player/PlayerState.kt`, `src/player/PlayerTrack.kt`, `src/player/PlayRequest.kt`.
Copy appendix A.7 **verbatim**. No implementations in this task.
**Verify:** `assembleDebug`. **Done:** compiles.

## M2.2 — MplayerEngine stub + JNI sketch
**Depends:** M2.1. **Files:** `src/player/MplayerEngine.kt`,
`src/player/MplayerNative.kt`.
- `MplayerEngine : PlayerEngine` — backing `MutableStateFlow`s, every method logs
  via `android.util.Log.d("MplayerEngine", ...)` and carries a
  `// TODO(mplayer): bind via JNI to ../mplayer` comment.
- `load()` sets `state = PlayerState.Error("mplayer engine not yet implemented")`.
- `MplayerNative` — an `object` whose intended `external fun` surface is written as
  **commented-out** signatures so the Rust side knows what to export:
  `nativeCreate(): Long`, `nativeDestroy(handle: Long)`,
  `nativeSetSurface(handle: Long, surface: Surface?)`,
  `nativeLoad(handle: Long, url: String, headersJson: String, startMs: Long)`,
  `nativePlay/nativePause/nativeSeek(handle, ms)`,
  `nativeSelectTrack(handle: Long, trackType: Int, trackId: Int)`,
  `nativeGetPositionMs(handle: Long): Long`, `nativeGetDurationMs(handle: Long): Long`,
  `nativeGetTracksJson(handle: Long): String`, `nativeGetState(handle: Long): Int`.
  Include a commented `System.loadLibrary("mplayer_jni")` in an `init` block.
**Verify:** `assembleDebug`. **Done:** compiles with zero uncommented JNI refs.

## M2.3 — SimulatedPlayerEngine (+ test)
**Depends:** M2.1. **Files:** `src/player/SimulatedPlayerEngine.kt`,
`test/player/SimulatedPlayerEngineTest.kt`.
- Constructor: `SimulatedPlayerEngine(private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default))`.
- `load(request)`: state → `Loading`, then `Buffering`, then `Playing` after ~300 ms;
  `durationMs` = `request.durationMs ?: 45 * 60 * 1000L`; `positionMs` =
  `request.startPositionMs`; tracks = synthetic
  `[PlayerTrack(0, AUDIO, "English (AAC 5.1)", "eng", isDefault = true, selected = true),
    PlayerTrack(1, AUDIO, "Japanese (FLAC)", "jpn"),
    PlayerTrack(2, SUBTITLE, "English (SRT)", "eng"),
    PlayerTrack(3, SUBTITLE, "Off", null, selected = true)]`
  unless `request` supplies tracks (it does not — tracks are engine-provided).
- Ticker coroutine advances `positionMs` by 1000 every 1000 ms while `Playing`;
  on reaching duration → `Ended`.
- `pause/play/seekTo/selectTrack/stop/release` behave as expected;
  `setSurface` is a no-op.
- Test uses `kotlinx.coroutines.test.runTest` + `TestScope`/virtual time: assert
  `Playing` after load, position advances 5000 ms after 5 s virtual time, `pause()`
  freezes it, `seekTo(60_000)` moves it, `Ended` at duration.
**Verify:** `.\gradlew.bat :app:testDebugUnitTest`. **Done:** M2 gate — engine test green.

---

# M3 — Core UI

## M3.1 — Routes + NavGraph + start-destination gate
**Depends:** M1.9, M2.3. **Files:** `src/ui/navigation/Routes.kt`,
`src/ui/navigation/NavGraph.kt`, `src/MainActivity.kt` (add `NavGraph()` call),
`src/ui/RootViewModel.kt`.
- `Routes.kt` — appendix A.8 verbatim.
- `RootViewModel(sessionStore)` exposes
  `val start: StateFlow<StartDestination>` where
  `sealed interface StartDestination { data object Loading; data object ServerEntry; data object Login; data object Home }`
  derived from `sessionStore.session` + `sessionStore.serverUrl`.
- `NavGraph()` composable: reads `LocalAppContainer`, builds `rememberNavController()`,
  renders a centered progress indicator while `Loading`, otherwise a `NavHost` whose
  `startDestination` is the resolved route. In this task register **only**
  `ServerEntryRoute`, `LoginRoute`, `HomeRoute` — each pointing at the screens from
  M3.3/M3.4/M3.5 (implementers coordinate on the composable names listed there;
  if a screen file does not exist yet, temporarily route to `Text("TODO")` and the
  M3.3–M3.5 owners replace the placeholder in their own file only — NavGraph is
  edited once more in M4.4).
- Provide a shared helper in `NavGraph.kt`:
  `@Composable inline fun <reified VM : ViewModel> containerViewModel(noinline factory: (AppContainer) -> VM): VM`
  using `androidx.lifecycle.viewmodel.compose.viewModel` + `viewModelFactory { initializer { factory(container) } }`.
**Verify:** `assembleDebug`. **Done:** app launches to ServerEntry on a clean install.

## M3.2 — Shared TV components
**Depends:** M1.9. **Files:** `src/ui/components/MediaCard.kt`,
`src/ui/components/MediaRow.kt`, `src/ui/components/PosterImage.kt`,
`src/ui/components/ScreenScaffold.kt`, `src/ui/components/StateViews.kt`,
`src/ui/theme/Dimens.kt`.
- `Dimens`: `OverscanHorizontal = 48.dp`, `OverscanVertical = 27.dp`,
  `CardWidth = 160.dp`, `CardHeight = 240.dp`, `WideCardWidth = 280.dp`,
  `WideCardHeight = 158.dp`, `RowSpacing = 20.dp`.
- `PosterImage(url: String?, contentDescription: String?, modifier: Modifier, aspect: Float)`
  — Coil 3 `AsyncImage` with a `MaterialTheme.colorScheme.surfaceVariant` placeholder box.
- `MediaCard(item: MediaCardUi, onClick: () -> Unit, modifier: Modifier = Modifier)`
  where
  ```kotlin
  data class MediaCardUi(
      val id: String, val title: String, val subtitle: String?,
      val imageUrl: String?, val progressFraction: Float?, val wide: Boolean = false,
  )
  ```
  Uses `androidx.tv.material3.Card` (or `Surface` with `ClickableSurfaceDefaults`),
  focus-scale 1.08 via `Modifier.onFocusChanged` + `animateFloatAsState` + `graphicsLayer`,
  bottom `LinearProgressIndicator` overlay when `progressFraction != null`.
- `MediaRow(title: String, items: List<MediaCardUi>, onItemClick: (String) -> Unit)`
  — `LazyRow` with `Modifier.focusRestorer()`, `contentPadding` = overscan horizontal,
  `horizontalArrangement = spacedBy(12.dp)`.
- `ScreenScaffold(title: String? = null, content: @Composable ColumnScope.() -> Unit)` —
  applies overscan padding.
- `StateViews.kt`: `LoadingView()`, `ErrorView(message: String, onRetry: () -> Unit)`,
  `EmptyView(message: String)`.
- Also add a mapper `fun BaseItemDto.toCardUi(images: ImageUrlBuilder, wide: Boolean = false): MediaCardUi`
  in `src/ui/components/ItemMapping.kt` — subtitle = production year for movies,
  `"S{ParentIndexNumber}:E{IndexNumber}"` for episodes; progress from
  `UserData.PlayedPercentage / 100f` when in (0,100).
**Verify:** `assembleDebug`. **Done:** compiles; no `androidx.tv.foundation` imports
(that artifact is removed) — use `androidx.compose.foundation.lazy.*`.

## M3.3 — ServerEntry screen
**Depends:** M3.1, M3.2. **Files:** `src/ui/screens/serverentry/ServerEntryScreen.kt`,
`src/ui/screens/serverentry/ServerEntryViewModel.kt`.
`ServerEntryViewModel(authRepository)`: `uiState: StateFlow<ServerEntryUiState>`
(`data class ServerEntryUiState(val url: String = "", val loading: Boolean = false,
val serverName: String? = null, val error: String? = null)`),
`fun onUrlChange(String)`, `fun connect()` → `authRepository.validateServer(url)`,
on success emit a one-shot `Channel<Unit>`/`SharedFlow` `navigateToLogin`.
`ServerEntryScreen(onConnected: () -> Unit)` — `OutlinedTextField` (Compose
material3 text field is fine; tv-material has no text field), Connect button,
error text, server-name confirmation.
**Verify:** `assembleDebug`. **Done:** entering a reachable URL navigates to Login.

## M3.4 — Login screen
**Depends:** M3.1, M3.2. **Files:** `src/ui/screens/login/LoginScreen.kt`,
`src/ui/screens/login/LoginViewModel.kt`.
`LoginViewModel(authRepository)`: state `(username, password, loading, error)`,
`fun login()` → `authRepository.login(...)`, one-shot `navigateToHome`.
Screen: two text fields (password with `PasswordVisualTransformation`), Sign In
button, error text, `onLoggedIn: () -> Unit` callback. Add a small
"Quick Connect — coming soon" disabled note (design defers QC to v2).
**Verify:** `assembleDebug`. **Done:** valid credentials navigate to Home.

## M3.5 — Home screen
**Depends:** M3.1, M3.2. **Files:** `src/ui/screens/home/HomeScreen.kt`,
`src/ui/screens/home/HomeViewModel.kt`.
`HomeViewModel(libraryRepository, imageUrlBuilder)`:
```kotlin
data class HomeRow(val key: String, val title: String, val items: List<MediaCardUi>)
data class HomeUiState(val loading: Boolean = true, val rows: List<HomeRow> = emptyList(), val error: String? = null)
```
`load()` (called in `init` and by `retry()`): fetch `resumeItems`, `nextUp`,
`userViews`; for each view with `CollectionType in ("movies","tvshows")` fetch
`latest(parentId = view.id, limit = 20)`; assemble rows in order
Continue Watching → Next Up → Latest in <view name> (one per library) → My Media
(the views themselves, wide cards). Empty rows are dropped. Use
`coroutineScope { async { } }` so library-latest calls run in parallel.
Screen: `LazyColumn` of `MediaRow`s + a top app bar row with Search and Settings
buttons; `onItemClick(id)` → `onNavigateToDetail(id)`; view cards →
`onNavigateToLibrary(id, name, collectionType)`.
Screen signature:
`HomeScreen(onNavigateToDetail: (String) -> Unit, onNavigateToLibrary: (String, String, String?) -> Unit, onNavigateToSearch: () -> Unit, onNavigateToSettings: () -> Unit)`.
**Verify:** `assembleDebug`. **Done:** M3 gate — assembleDebug + testDebugUnitTest green;
Home renders rows against a live server.

---

# M4 — Browse UI

## M4.1 — Library screen (paged grid)
**Depends:** M3.5. **Files:** `src/ui/screens/library/LibraryScreen.kt`,
`src/ui/screens/library/LibraryViewModel.kt`.
`LibraryViewModel(libraryRepository, imageUrlBuilder, route: LibraryRoute)`:
```kotlin
enum class LibrarySort(val apiValue: String, val label: String) {
    NAME("SortName", "Name"), DATE_ADDED("DateCreated", "Date added"), PREMIERE("PremiereDate", "Release date")
}
enum class LibraryFilter(val apiValue: String?, val label: String) {
    ALL(null, "All"), UNWATCHED("IsUnplayed", "Unwatched"), WATCHED("IsPlayed", "Watched")
}
data class LibraryUiState(val title: String = "", val items: List<MediaCardUi> = emptyList(),
    val total: Int = 0, val loading: Boolean = true, val loadingMore: Boolean = false,
    val sort: LibrarySort = LibrarySort.NAME, val filter: LibraryFilter = LibraryFilter.ALL,
    val error: String? = null)
```
`loadPage()` uses `startIndex = items.size`, `limit = 100`; `setSort`/`setFilter`
reset to page 0. `includeItemTypes` derived from `collectionType`
(`movies`→`Movie`, `tvshows`→`Series`, else empty), `recursive = true`,
`sortOrder = "Descending"` for DATE_ADDED/PREMIERE else `"Ascending"`.
Screen: `LazyVerticalGrid(GridCells.Adaptive(160.dp))` with
`Modifier.focusRestorer()`, overscan padding, sort/filter chip row at top,
`LaunchedEffect` on last-visible-index to trigger `loadPage()`.
Signature: `LibraryScreen(route: LibraryRoute, onNavigateToDetail: (String) -> Unit, onBack: () -> Unit)`.
**Verify:** `assembleDebug`. **Done:** compiles; scrolling past 100 items triggers a second page.

## M4.2 — Detail screen (movie + series)
**Depends:** M3.5. **Files:** `src/ui/screens/detail/DetailScreen.kt`,
`src/ui/screens/detail/DetailViewModel.kt`.
`DetailViewModel(libraryRepository, imageUrlBuilder, itemId)`:
```kotlin
data class DetailUiState(
    val loading: Boolean = true, val item: BaseItemDto? = null,
    val backdropUrl: String? = null, val posterUrl: String? = null,
    val seasons: List<BaseItemDto> = emptyList(), val selectedSeasonId: String? = null,
    val episodes: List<MediaCardUi> = emptyList(), val error: String? = null,
)
```
`init` → `libraryRepository.item(itemId)`; when `Type == "Series"` also fetch
`seasons(itemId)` and `episodes(itemId, firstSeasonId)`. `selectSeason(id)` refetches
episodes. Expose `val resumePositionTicks: Long` = `item.userData.playbackPositionTicks ?: 0`.
Screen: full-bleed backdrop with a bottom scrim, title, `year · runtime · rating`
metadata line (runtime via `Ticks.formatDuration`), overview (max 4 lines),
button row `Play` / `Resume from HH:MM` (only when resume > 0) — both call
`onPlay(itemId, startTicks)`. For series: season selector row + episode `MediaRow`
(wide cards, episodes navigate via `onPlay(episodeId, episodeResumeTicks)`).
Signature: `DetailScreen(itemId: String, onPlay: (String, Long) -> Unit, onNavigateToDetail: (String) -> Unit, onBack: () -> Unit)`.
**Verify:** `assembleDebug`. **Done:** movie and series both render; Play emits the callback.

## M4.3 — Search screen
**Depends:** M3.5. **Files:** `src/ui/screens/search/SearchScreen.kt`,
`src/ui/screens/search/SearchViewModel.kt`.
`SearchViewModel(libraryRepository, imageUrlBuilder)`: `query: StateFlow<String>`,
debounced 350 ms via `flatMapLatest`, results as `List<MediaCardUi>` (min 2 chars).
Screen: text field pinned at top with initial focus, `LazyVerticalGrid` of results,
empty/loading states. Signature:
`SearchScreen(onNavigateToDetail: (String) -> Unit, onBack: () -> Unit)`.
**Verify:** `assembleDebug`. **Done:** typing yields results.

## M4.4 — NavGraph extension for M4
**Depends:** M4.1, M4.2, M4.3. **Files:** `src/ui/navigation/NavGraph.kt` (sole owner).
Register `LibraryRoute`, `DetailRoute`, `SearchRoute`; wire the Home callbacks;
`DetailScreen.onPlay` navigates to `PlayerRoute(itemId, startTicks)` — register a
placeholder `Text("Player")` composable for `PlayerRoute` here; M5.4 replaces it.
Use `composable<LibraryRoute> { backStackEntry -> val route = backStackEntry.toRoute<LibraryRoute>() ... }`.
**Verify:** `assembleDebug` + `testDebugUnitTest`. **Done:** M4 gate — full
Home → Library → Detail → back navigation works on device.

---

# M5 — Player UI

## M5.1 — PlaybackProgressReporter (+ test)
**Depends:** M1.7, M2.1. **Files:** `src/player/PlaybackProgressReporter.kt`,
`test/player/PlaybackProgressReporterTest.kt`.
```kotlin
class PlaybackProgressReporter(
    private val repository: PlaybackRepository,
    private val intervalMs: Long = 10_000L,
) {
    suspend fun run(
        engine: PlayerEngine,
        resolved: ResolvedPlayback,
        selectedAudioIndex: () -> Int?,
        selectedSubtitleIndex: () -> Int?,
    )
    suspend fun reportStopped(resolved: ResolvedPlayback, positionMs: Long, failed: Boolean)
}
```
`run` is a suspend loop, cancelled when the caller's scope dies: sends
`reportStart` once on first `Playing`, then `reportProgress` every `intervalMs`
**and** immediately on any `PlayerState` transition (pause/resume/seek is covered by
observing `state` and a distinct-until-changed on `positionMs / 10_000`).
Test with `runTest` + a fake `PlaybackRepository` seam: use an interface
`PlaybackReporting` (`suspend fun reportStart/reportProgress/reportStopped`) that
`PlaybackRepository` implements — **M1.7 must declare and implement this interface**;
the reporter depends on `PlaybackReporting`, not the concrete repository.
Assert: 1 start; 3 progress reports after 30 s virtual time; stopped carries final ticks.
**Verify:** `.\gradlew.bat :app:testDebugUnitTest`. **Done:** test green.

## M5.2 — PlayerViewModel
**Depends:** M5.1, M1.7, M2.1. **Files:** `src/ui/screens/player/PlayerViewModel.kt`.
```kotlin
data class PlayerUiState(
    val loading: Boolean = true, val title: String = "", val error: String? = null,
    val state: PlayerState = PlayerState.Idle, val positionMs: Long = 0, val durationMs: Long? = null,
    val audioTracks: List<PlayerTrack> = emptyList(), val subtitleTracks: List<PlayerTrack> = emptyList(),
)
class PlayerViewModel(
    private val engine: PlayerEngine,
    private val playbackRepository: PlaybackRepository,
    private val libraryRepository: LibraryRepository,
    private val reporter: PlaybackProgressReporter,
    private val itemId: String,
    private val startPositionTicks: Long,
) : ViewModel() {
    val uiState: StateFlow<PlayerUiState>
    fun setSurface(surface: Surface?)
    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun seekTo(ms: Long)
    fun selectTrack(track: PlayerTrack)
    fun onStopped()               // report stopped; call from onDispose
    override fun onCleared()      // engine.stop(); reporter cancelled by viewModelScope
}
```
`init`: fetch item name, `playbackRepository.resolve(itemId, startPositionTicks)`,
`engine.load(PlayRequest(...))`, launch `reporter.run(...)` in `viewModelScope`.
`uiState` = `combine(engine.state, engine.positionMs, engine.durationMs, engine.tracks, localFlow)`.
**Verify:** `assembleDebug`. **Done:** compiles.

## M5.3 — Player screen + OSD
**Depends:** M5.2, M3.2. **Files:** `src/ui/screens/player/PlayerScreen.kt`,
`src/ui/screens/player/PlayerOsd.kt`, `src/ui/screens/player/TrackDialog.kt`.
- `PlayerScreen(itemId: String, startPositionTicks: Long, onExit: () -> Unit)`.
- `AndroidView(factory = { SurfaceView(it).apply { holder.addCallback(...) } })`
  filling the screen; callbacks forward to `viewModel.setSurface`.
- OSD `AnimatedVisibility` overlay: title, position/duration text
  (`Ticks.formatDuration`), a seek bar (`Slider` from compose material3 or a custom
  `Box` progress track — must be D-pad operable: LEFT/RIGHT seek ∓10 s), transport
  row (rewind / play-pause / forward / audio / subtitles).
- Auto-hide after 5000 ms of no key events (`LaunchedEffect(lastInteraction)`);
  any D-pad key via `Modifier.onPreviewKeyEvent` reveals it and resets the timer.
- Back: if OSD visible → hide; else `onExit()` (`BackHandler`).
- `TrackDialog(title, tracks, onSelect, onDismiss)` — tv-material `Dialog`/
  `AlertDialog` listing tracks with the selected one marked.
- `DisposableEffect` calls `viewModel.onStopped()`.
**Verify:** `assembleDebug`. **Done:** with `SimulatedPlayerEngine` (debug build)
the OSD shows a ticking position, pause/seek/track selection all work by D-pad.

## M5.4 — NavGraph extension for M5
**Depends:** M5.3. **Files:** `src/ui/navigation/NavGraph.kt` (sole owner).
Replace the `PlayerRoute` placeholder with `PlayerScreen`; `onExit` pops back.
**Verify:** `assembleDebug` + `testDebugUnitTest`. **Done:** M5 gate — Detail →
Play → player → Back returns to Detail.

---

# M6 — Polish

## M6.1 — Settings screen + nav + AppContainer touch-up
**Depends:** M5.4. **Files:** `src/ui/screens/settings/SettingsScreen.kt`,
`src/ui/screens/settings/SettingsViewModel.kt`, `src/ui/navigation/NavGraph.kt`
(sole owner), `src/di/AppContainer.kt` (sole owner).
Settings shows server URL, server name, signed-in user, app version, engine name
(`playerEngine::class.simpleName`), and a Sign out button →
`authRepository.logout()` then navigate to `LoginRoute` with
`popUpTo(0) { inclusive = true }`. Register `SettingsRoute` in NavGraph.
**Verify:** `assembleDebug`. **Done:** sign out returns to Login and Home is
unreachable via Back.

## M6.2 — 401 handling
**Depends:** M5.4. **Files:** `src/data/api/JellyfinClient.kt`,
`src/di/AuthEvents.kt` (new).
`object AuthEvents { private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1);
val unauthorized: SharedFlow<Unit> = _unauthorized; fun emitUnauthorized() }`.
`JellyfinClient` emits on any 401 and calls `sessionStore.clearAuth()`. `NavGraph`
collects `AuthEvents.unauthorized` (add the collector inside `NavGraph.kt` —
coordinate: M6.1 owns that file this wave, so M6.2 hands its 3-line collector
snippet to M6.1 or lands after M6.1). Navigation on event: `LoginRoute` with
`popUpTo(0) { inclusive = true }`.
**Verify:** `assembleDebug` + `testDebugUnitTest` (add a MockEngine test asserting
`clearAuth` runs on 401). **Done:** revoking the token on the server bounces to Login.

## M6.3 — Focus / overscan / TV UX pass
**Depends:** M5.4. **Files:** every `src/ui/screens/**` screen root + `src/ui/components/**`.
Checklist: overscan padding on all screen roots; `focusRestorer()` on every
`LazyRow`/`LazyVerticalGrid`; `FocusRequester` + `LaunchedEffect { requestFocus() }`
for the first actionable element on each screen; no `clickable {}` without
`focusable()`; `Modifier.onPreviewKeyEvent` swallows nothing it should not; text
contrast on dark scheme; content descriptions on images.
**Note:** if M6.1/M6.2 are running in the same wave, M6.3 must not touch
`NavGraph.kt` or `SettingsScreen.kt`.
**Verify:** `assembleDebug`. **Done:** every screen fully navigable with D-pad only.

## M6.4 — Final gate
**Depends:** M6.1, M6.2, M6.3. **Files:** none (fix-ups only).
**Verify:** `.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest`.
**Done:** both tasks green from a clean build; no `TODO()` that throws at runtime;
`MplayerEngine` still compiles and reports its not-implemented error.

---

# Appendix A — Contracts (copy verbatim)

## A.1 Session / SessionStore — `src/data/session/`
```kotlin
data class Session(
    val serverUrl: String,      // normalized, no trailing slash
    val accessToken: String,
    val userId: String,
    val userName: String,
    val deviceId: String,
)

interface SessionProvider {
    suspend fun current(): Session?
    suspend fun deviceId(): String
    suspend fun serverUrlOrNull(): String?
}

class SessionStore(context: Context) : SessionProvider {
    val session: Flow<Session?>
    val serverUrl: Flow<String?>
    override suspend fun current(): Session?
    override suspend fun deviceId(): String
    override suspend fun serverUrlOrNull(): String?
    suspend fun setServerUrl(url: String)
    suspend fun saveAuth(accessToken: String, userId: String, userName: String)
    suspend fun clearAuth()   // keeps serverUrl + deviceId
    suspend fun clearAll()
}

internal fun normalizeServerUrl(raw: String): String
```

## A.2 Trimmed DTOs — `src/data/api/dto/`
```kotlin
@Serializable data class QueryResult<T>(
    @SerialName("Items") val items: List<T> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerialName("StartIndex") val startIndex: Int = 0,
)

@Serializable data class BaseItemDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("CommunityRating") val communityRating: Float? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("IsFolder") val isFolder: Boolean = false,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerialName("ParentThumbImageTag") val parentThumbImageTag: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("UserData") val userData: UserItemDataDto? = null,
)

@Serializable data class UserItemDataDto(
    @SerialName("Played") val played: Boolean = false,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
)

@Serializable data class PublicSystemInfo(
    @SerialName("Id") val id: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("ProductName") val productName: String? = null,
    @SerialName("StartupWizardCompleted") val startupWizardCompleted: Boolean? = null,
)

@Serializable data class AuthenticateUserByName(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String,
)
@Serializable data class UserDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)
@Serializable data class AuthenticationResult(
    @SerialName("User") val user: UserDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)

@Serializable data class MediaStreamDto(
    @SerialName("Index") val index: Int = -1,
    @SerialName("Type") val type: String? = null,          // Video|Audio|Subtitle|...
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("DeliveryMethod") val deliveryMethod: String? = null,
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
)

@Serializable data class MediaSourceInfoDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Protocol") val protocol: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Bitrate") val bitrate: Long? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerialName("DefaultAudioStreamIndex") val defaultAudioStreamIndex: Int? = null,
    @SerialName("DefaultSubtitleStreamIndex") val defaultSubtitleStreamIndex: Int? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
)

@Serializable data class PlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfoDto> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("ErrorCode") val errorCode: String? = null,
)

@Serializable data class PlaybackInfoDto(
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfileDto? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long? = null,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean = true,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean = true,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean = true,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean = true,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean = true,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)

// Playback reporting bodies (one class serves Start and Progress).
@Serializable data class PlaybackProgressBody(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("PlayMethod") val playMethod: String? = null,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("IsMuted") val isMuted: Boolean = false,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)
@Serializable data class PlaybackStopBody(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("Failed") val failed: Boolean = false,
)

// DeviceProfileDto.kt
@Serializable data class DirectPlayProfileDto(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String = "Video",
    @SerialName("VideoCodec") val videoCodec: String? = null,
    @SerialName("AudioCodec") val audioCodec: String? = null,
)
@Serializable data class TranscodingProfileDto(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String = "Video",
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("Protocol") val protocol: String = "hls",
    @SerialName("Context") val context: String = "Streaming",
    @SerialName("MaxAudioChannels") val maxAudioChannels: String? = null,
    @SerialName("MinSegments") val minSegments: Int = 1,
    @SerialName("BreakOnNonKeyFrames") val breakOnNonKeyFrames: Boolean = true,
)
@Serializable data class SubtitleProfileDto(
    @SerialName("Format") val format: String,
    @SerialName("Method") val method: String,
)
@Serializable data class DeviceProfileDto(
    @SerialName("Name") val name: String,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfileDto>,
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<TranscodingProfileDto>,
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<SubtitleProfileDto>,
    @SerialName("CodecProfiles") val codecProfiles: List<String> = emptyList(),
    @SerialName("ContainerProfiles") val containerProfiles: List<String> = emptyList(),
)
```

## A.3 ApiResult — `src/data/ApiResult.kt`
```kotlin
sealed interface ApiError {
    data object Unauthorized : ApiError
    data class Network(val message: String) : ApiError
    data class Http(val code: Int, val message: String) : ApiError
    data class Serialization(val message: String) : ApiError
    data class Unknown(val message: String) : ApiError
    val displayMessage: String get() = when (this) { /* human text */ }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R>
inline fun <T> ApiResult<T>.getOrNull(): T?
```

## A.4 JellyfinClient — `src/data/api/JellyfinClient.kt`
```kotlin
class JellyfinClient(
    private val sessions: SessionProvider,
    private val appName: String,
    private val appVersion: String,
    engine: HttpClientEngine? = null,
) {
    suspend fun <T> get(
        path: String,
        params: Map<String, Any?> = emptyMap(),
        baseUrlOverride: String? = null,
        deserialize: (HttpResponse) -> T,      // usually { it.body<X>() } — use the suspend body helper
    ): ApiResult<T>

    suspend inline fun <reified T> get(path: String, params: Map<String, Any?> = emptyMap(), baseUrlOverride: String? = null): ApiResult<T>
    suspend inline fun <reified T> post(path: String, body: Any? = null, params: Map<String, Any?> = emptyMap(), baseUrlOverride: String? = null): ApiResult<T>
    suspend fun postEmpty(path: String, body: Any? = null, params: Map<String, Any?> = emptyMap()): ApiResult<Unit>

    suspend fun resolveUrl(path: String, params: Map<String, Any?> = emptyMap()): String?
    suspend fun currentSession(): Session?
    suspend fun authHeader(): String
}
```
(If the non-reified `deserialize` overload proves awkward, keep only the reified
`get`/`post` forms plus `postEmpty` — the reified forms are the contract other
tasks call.)

## A.5 Repositories — `src/data/repo/`
```kotlin
class AuthRepository(private val client: JellyfinClient, private val sessionStore: SessionStore) {
    suspend fun validateServer(rawUrl: String): ApiResult<PublicSystemInfo>  // also persists normalized url on success
    suspend fun login(username: String, password: String): ApiResult<Session>
    suspend fun logout()
}

class LibraryRepository(private val client: JellyfinClient) {
    suspend fun userViews(): ApiResult<List<BaseItemDto>>
    suspend fun resumeItems(limit: Int = 20): ApiResult<List<BaseItemDto>>
    suspend fun nextUp(limit: Int = 20): ApiResult<List<BaseItemDto>>
    suspend fun latest(parentId: String?, limit: Int = 20): ApiResult<List<BaseItemDto>>
    suspend fun items(
        parentId: String? = null,
        includeItemTypes: List<String> = emptyList(),
        recursive: Boolean = true,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        filters: List<String> = emptyList(),
        startIndex: Int = 0,
        limit: Int = 100,
        searchTerm: String? = null,
    ): ApiResult<QueryResult<BaseItemDto>>
    suspend fun item(itemId: String): ApiResult<BaseItemDto>
    suspend fun seasons(seriesId: String): ApiResult<List<BaseItemDto>>
    suspend fun episodes(seriesId: String, seasonId: String?): ApiResult<List<BaseItemDto>>
    suspend fun search(term: String, limit: Int = 60): ApiResult<List<BaseItemDto>>
}

data class ResolvedPlayback(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val streamUrl: String,
    val playMethod: String,               // "DirectPlay" | "DirectStream" | "Transcode"
    val runTimeTicks: Long?,
    val audioTracks: List<PlayerTrack>,
    val subtitleTracks: List<PlayerTrack>,
    val defaultAudioIndex: Int?,
    val defaultSubtitleIndex: Int?,
    val headers: Map<String, String>,
)

interface PlaybackReporting {
    suspend fun reportStart(r: ResolvedPlayback, positionTicks: Long, audioIndex: Int?, subtitleIndex: Int?): ApiResult<Unit>
    suspend fun reportProgress(r: ResolvedPlayback, positionTicks: Long, isPaused: Boolean, audioIndex: Int?, subtitleIndex: Int?): ApiResult<Unit>
    suspend fun reportStopped(r: ResolvedPlayback, positionTicks: Long, failed: Boolean): ApiResult<Unit>
}

class PlaybackRepository(private val client: JellyfinClient) : PlaybackReporting {
    suspend fun resolve(itemId: String, startPositionTicks: Long = 0, mediaSourceId: String? = null): ApiResult<ResolvedPlayback>
    // + the three PlaybackReporting overrides
}
```

## A.6 AppContainer — `src/di/AppContainer.kt`
```kotlin
class AppContainer(private val context: Context) {
    val appName: String = "Shoumei Player"
    val appVersion: String = BuildConfig.VERSION_NAME
    val sessionStore: SessionStore by lazy { SessionStore(context) }
    val jellyfinClient: JellyfinClient by lazy { JellyfinClient(sessionStore, appName, appVersion) }
    val imageUrlBuilder: ImageUrlBuilder by lazy { ImageUrlBuilder { cachedServerUrl } }
    val authRepository: AuthRepository by lazy { AuthRepository(jellyfinClient, sessionStore) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(jellyfinClient) }
    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(jellyfinClient) }
    val playerEngine: PlayerEngine by lazy { if (BuildConfig.DEBUG) SimulatedPlayerEngine() else MplayerEngine() }
    val progressReporter: PlaybackProgressReporter by lazy { PlaybackProgressReporter(playbackRepository) }
    // cachedServerUrl: a @Volatile String? kept in sync by collecting sessionStore.serverUrl
    // on an application-scoped CoroutineScope created here (SupervisorJob + Dispatchers.Default).
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
```

## A.7 Player contracts — `src/player/`
```kotlin
enum class TrackType { AUDIO, SUBTITLE }

data class PlayerTrack(
    val id: Int,                 // Jellyfin MediaStream.Index (or -1 for "Off")
    val type: TrackType,
    val label: String,
    val language: String? = null,
    val isDefault: Boolean = false,
    val selected: Boolean = false,
)

data class PlayRequest(
    val url: String,
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0,
    val durationMs: Long? = null,
    val preferredAudioTrackId: Int? = null,
    val preferredSubtitleTrackId: Int? = null,
)

sealed interface PlayerState {
    data object Idle : PlayerState
    data object Loading : PlayerState
    data object Buffering : PlayerState
    data object Playing : PlayerState
    data object Paused : PlayerState
    data object Ended : PlayerState
    data class Error(val message: String) : PlayerState
}

interface PlayerEngine {
    val state: StateFlow<PlayerState>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long?>
    /** End of the buffered range, same timeline as positionMs; null = engine cannot report it. */
    val bufferedMs: StateFlow<Long?>
    val tracks: StateFlow<List<PlayerTrack>>
    /** Playback rate multiplier; 1.0 is normal. Engines without rate control hold it at 1.0. */
    val speed: StateFlow<Float>
    fun setSurface(surface: Surface?)
    fun setSurfaceSize(width: Int, height: Int)
    fun load(item: PlayRequest)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    /** Sets the playback rate; leaves `speed` untouched when the rate could not be applied. */
    fun setSpeed(speed: Float)
    fun selectTrack(track: PlayerTrack)
    fun stop()
    fun release()
}
```

**Speed (osd-v3 §5).** The ladder lives in `player/PlaybackSpeed.kt`
(`0.5 / 0.75 / 1 / 1.25 / 1.5 / 2`, plus `clamp`, `label`, `isNormal`). Per engine:
`MpvEngine` drives mpv's `speed` property and *observes* it back, so the flow reports
what mpv is actually running at rather than what was requested; `SimulatedPlayerEngine`
multiplies its 1s ticker's playhead advance; `MplayerEngine` is a logged no-op pinned
at 1× until the JNI bridge exists.

## A.8 Navigation routes — `src/ui/navigation/Routes.kt`
```kotlin
@Serializable data object ServerEntryRoute
@Serializable data object LoginRoute
@Serializable data object HomeRoute
@Serializable data class LibraryRoute(val libraryId: String, val title: String, val collectionType: String? = null)
@Serializable data class DetailRoute(val itemId: String)
@Serializable data class PlayerRoute(val itemId: String, val startPositionTicks: Long = 0)
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
```

---

# Appendix B — Risks

1. **AGP 9 built-in Kotlin.** AGP 9.0 flipped `android.builtInKotlin` to true:
   Kotlin compiles without `org.jetbrains.kotlin.android`, and applying that plugin
   *conflicts*. Unverified consequence: whether the
   `org.jetbrains.kotlin.plugin.serialization` compiler plugin registers correctly
   under built-in Kotlin. **If M0.2's build fails with "serialization plugin
   requires the Kotlin plugin" or `@Serializable` classes fail to get a serializer**,
   the fallback is: add `android.builtInKotlin=false` to `gradle.properties`, add
   `kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }`
   to `[plugins]`, and apply it in both build scripts. Record whichever path was
   taken at the top of `app/build.gradle.kts` as a comment. Other AGP 9 notes:
   JDK 17 minimum, Gradle ≥ 9.1 (wrapper is 9.4.1 — fine), `android.newDsl=true`
   removes the legacy `applicationVariants` API (unused here),
   `buildFeatures.resValues`/`shaders` now default false, and `uniquePackageNames`
   is enforced.
2. **tv-foundation removal.** `androidx.tv:tv-foundation` never reached stable and
   its `TvLazyColumn`/`TvLazyRow`/`ImmersiveList` were folded into
   `androidx.compose.foundation` (with `Modifier.focusRestorer()` moving to
   `androidx.compose.ui.focus`). Implementers must use `LazyColumn`/`LazyRow`/
   `LazyVerticalGrid` from compose-foundation. Any sample code found online using
   `TvLazy*` is stale. Other tv-material symbols removed/renamed since the alpha
   the scaffold targeted: `ImmersiveList` **removed** (no replacement — hand-roll);
   `StandardCardLayout`/`WideCardLayout` → `StandardCardContainer`/`WideCardContainer`;
   `CardContainerDefaults.ImageCard` removed;
   `NonInteractiveSurfaceDefaults`/`NonInteractiveSurfaceColors` → `SurfaceDefaults`/`SurfaceColors`;
   `NavigationDrawer` scope `doesTabRowHaveFocus()` → `hasFocus()`; component
   `interactionSource` params are now nullable with a `null` default. We target
   tv-material **1.1.0** (stable), not 1.0.x.
3. **tv-material coverage gaps.** `androidx.tv.material3` has no `TextField`,
   `Slider`, or `LinearProgressIndicator`. Mix in `androidx.compose.material3` for
   those specific widgets (both artifacts can coexist; import explicitly to avoid
   ambiguity, e.g. `androidx.compose.material3.OutlinedTextField`). Several
   tv-material APIs are still `@ExperimentalTvMaterial3Api` — opt in per file
   rather than globally.
4. **minSdk 36 → 28 fallout.** Any API used above 28 needs a version guard.
   Known: `Context.getMainExecutor` (28 ok), `SurfaceView` fine, DataStore fine,
   `enableEdgeToEdge` needs activity-compose (fine). Do not use
   `android.graphics.Path.getSegment`, `PictureInPictureParams.Builder#setAutoEnterEnabled`
   (31+), or `MediaCodec` HDR helpers without a guard.
5. **Cleartext HTTP.** `usesCleartextTraffic="true"` plus a permissive
   `network-security-config` is a deliberate home-lab trade-off (design §Constraints).
   Do not narrow it to a domain list — the server is user-entered at runtime.
6. **Jellyfin response-shape inconsistency.** `/Items/Latest` returns a bare array
   while nearly everything else returns `QueryResult`. `PlaybackStartInfo` and
   `PlaybackProgressInfo` are schema-identical. Both are covered by M1.6/M1.7 but
   are the most likely source of runtime deserialization failures.
7. **Stream auth.** Stream URLs carry `api_key` as a query param because the media
   engine may not be able to set headers. Never log the resolved stream URL at
   INFO level — it contains the access token.
8. **Engine swap.** Release builds get `MplayerEngine`, which deliberately fails.
   Until `../mplayer` exists, only debug builds are demoable. Do not "fix" this by
   shipping `SimulatedPlayerEngine` in release.
9. **Navigation choice.** `androidx.navigation3` is now stable (1.1.4) and is
   Google's forward direction, but it hands you the back stack manually. Design.md
   specifies `navigation-compose` with typed serializable routes; we stay on
   `navigation-compose` 2.9.8. Do not mix the two.
10. **Parallel-agent file collisions.** The wave table's sole-owner rule for
   `libs.versions.toml`, `app/build.gradle.kts`, `AppContainer.kt`, and
   `NavGraph.kt` is mandatory. An agent that needs a change in a file it does not
   own must stop and report rather than edit it.
