# AGENTS.md — Komorei

Android anime streaming app, Vietnamese UI. Single `:app` module, package `git.shin.komorei`.
Stack: Compose + Hilt + Room + Media3 (ExoPlayer) + OkHttp/Moshi. No CI, no lint gate.

## Commands
- Fast compile: `./gradlew :app:compileDebugKotlin` (runs KSP — Hilt/Room/Moshi codegen errors surface here)
- Unit tests (JVM, Robolectric + Roborazzi): `./gradlew :app:testDebugUnitTest`
- Single test: `./gradlew :app:testDebugUnitTest --tests "git.shin.komorei.AnimeRepositoryTest"`

## Architecture
- Entry: `MainActivity` + `KomoreiApplication` (Hilt). `KomoreiApplication` exposes an `@Inject dataSourceFactory: KomoreiDataSourceFactory` — composables reach it via `(context.applicationContext as KomoreiApplication)`.
- `di/RepositoryModule`: shared OkHttpClient (User-Agent header, HttpLogging HEADERS, `WebViewCookieJar`, followRedirects) + ImageLoader + Room DB. Cookies from WebView flow into every media request automatically.
- **ALL data is fake** (commit "fake data"): `AnimeRepository` is mocked, `delay()` simulates the network. `getStream` returns real sample URLs (Google sample HLS/MP4) so playback actually works. Retrofit is an unused dependency — scaffold for real sources. OkHttp/Moshi are used for real (Moshi via codegen for Room converters in `KomoreiTypeConverters`).
- **Anime "Lite" vs "full"**: list APIs return Lite; you MUST call `getAnimeUpdate(anime, needsDetails, needsChapters)` before `getStreamList`/`getStream`. `needsChapters=true` fetches episodes only for the current animeId/season (does not pull sibling seasons).
- **Player flow**: `PlayerViewModel`/`PlayerPlaybackState` is the single source of truth (fullAnime, streams, selectedStreamId, streamData, segment interceptors). `AnimeDetailView` is a pass-through view. Flow: `openAnime`/`selectEpisode` → `loadStreams` (getAnimeUpdate needsDetails=true → getStreamList → getStream first server) → `Media3VideoPlayer`.
- **Media3**: media source = `DefaultMediaSourceFactory(KomoreiDataSourceFactory)` (auto-detects HLS vs MP4). `KomoreiDataSourceFactory.configure(streamData, urlInterceptor?, dataInterceptor?)` injects `streamData.headers` into every sub-request and wraps with `TransformableHttpDataSource` when an interceptor is present.
- **Segment transformer**: `AnimeRepository.segmentUrlInterceptor`/`segmentDataInterceptor` are open/null — a real source just overrides these two properties and gets wrapped automatically.
- `StreamData.isContent == false` → treated as not playable (shows loading/error), no media source built.
- Model: `Episode` plays the role of "chapter" (kept the Episode name). `Episode.episodeNumber` is a **String**.
- Every displayed string is a string resource in `res/values/strings.xml` (~119 strings, Vietnamese) — never hardcode.

## Gotchas
- **Do NOT nest vertical scrollables**: `LazyVerticalGrid` inside an `item {}` of a `LazyColumn` → FATAL "measured with infinity maximum height" (usually triggered by prefetch). The "Related" section of the detail page deliberately uses a non-lazy grid (chunked rows) — keep it that way; any other nested grid must have a bounded height.
- "Unused" dependencies are **kept as comments** in `app/build.gradle.kts` + `gradle/libs.versions.toml` (datastore-preferences, Firebase, camera, play-services, ...) — don't remove them; uncomment to re-enable.
- `.env`/`.env.example` provide `API_KEY` via the Secrets Gradle Plugin (BuildConfig) for a future Gemini feature — currently just a placeholder, nothing reads it yet.
- Release builds need signing: env `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD` or a root `my-upload-key.jks`. Debug needs nothing.
- Configuration cache is on (`gradle.properties`); changing build scripts may trigger a config cache rebuild.
- Versions: AGP 9.3.2, Kotlin 2.4.20, Compose BOM 2026.09.00, media3 1.11.0, Hilt 2.60.1; minSdk 24, targetSdk 36, compileSdk 37.
- Tests: `app/src/test` runs on JVM with Robolectric (`isIncludeAndroidResources = true`); Roborazzi writes PNG screenshots to `src/test/screenshots/`.