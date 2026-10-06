# Komorei

> [!WARNING]
> **Komorei is under active development and is only basically functional.**
> Expect rough edges, incomplete features, and behaviour that changes between
> releases. The feature list below describes what the app is being built towards,
> not a guarantee of what any given build does. Please do not treat a release as
> stable, and report what breaks rather than assuming it is a bug you introduced.

Komorei is an Aidoku-compatible Android application for watching anime from multiple sources. It is built with modern Jetpack Compose and supports Android phones, tablets, and Android TV.

Releases are distributed directly as signed APK/AAB files through GitHub Releases. Komorei does not depend on Google Play for installation.

<img width="1080" height="2310" alt="1000056866" src="https://github.com/user-attachments/assets/6844e2dd-b6bb-4366-90b7-305e79232f09" />


## Features

- Dark Material 3 interface with native D-pad navigation on TV.
- Multi-source Home screen with source tabs, dynamic listings, banners, anime lists, and pagination.
- Dedicated Home, search, source settings, and in-app browser pages for each source.
- media3/ExoPlayer playback:
  - watch progress and resume playback;
  - next episode, seek, subtitles, playback speed;
  - intro/outro skipping;
  - mini player and Picture-in-Picture.
- Library, watch history, and Insights statistics.
- Local backups and Google Drive backups with optional background synchronization.
- OTA updates from signed APKs published on GitHub Releases.
- `komorei://` deep links for sources, source repositories, and app content.
- `.krx` source execution through the Rust runner and uniffi bridge.

## Development Requirements

| Component | Requirement |
|---|---|
| JDK | 21 |
| Android SDK | `compileSdk 37`, build tools 36.x, platform tools |
| Rust | Stable, 1.85 or newer (edition 2024) |
| Rust target | `wasm32-unknown-unknown` |
| uniffi | `uniffi-bindgen 0.32.1` |
| Package manager | **Bun 1.4.2+** — npm is not used |
| Android NDK | Only required to build device `.so` libraries |

The following directories are separate repositories and must be checked out next to the app repository:

```text
komorei-app/
├── komorei-sdk/
└── sources/
```

`runner/Cargo.toml` uses the relative path dependency `../komorei-sdk/crates/lib`, so the runner cannot be built without `komorei-sdk`.

## Getting Started

### 1. Check out the repositories

```bash
git clone https://github.com/tachibana-shin/komorei-app.git
cd komorei-app

git clone https://github.com/tachibana-shin/komorei-sdk.git komorei-sdk
git clone https://github.com/tachibana-shin/komorei-sources.git sources
```

### 2. Install the Rust tooling

```bash
rustup target add wasm32-unknown-unknown
cargo install uniffi-bindgen --version 0.32.1 --locked
cargo install --path komorei-sdk/crates/cli --locked
```

### 3. Build source fixtures

The source `.krx` packages are generated from the crates in `sources`:

```bash
komorei package komorei-sdk/examples/example-source

for source in sources/sources/*; do
  [ -d "$source" ] || continue
  [ -f "$source/.skip" ] && continue
  komorei package "$source"
done
```

### 4. Build the app

```bash
./gradlew :app:assembleDebug
```

The application IDs are separated by build type:

```text
Debug:   git.shin.komorei.dev
Release: git.shin.komorei
```

This allows debug and release builds to be installed side by side with isolated data.

## Building and Testing

### Local quality gates

Style and lint run locally through [husky](https://typicode.github.io/husky/): a
`pre-commit` hook covers the fast checks and a `pre-push` hook adds the slower
ones. The same commands are what CI runs, so nothing is gated locally that is
not gated remotely.

```bash
# Everything the pre-commit + pre-push hooks check
bun run lint:ci

# Kotlin style — the rules live in .editorconfig
./gradlew ktlintCheck     # check
./gradlew ktlintFormat    # fix

# Rust style + lints for the wasm runner crate
cargo fmt  --manifest-path runner/Cargo.toml          # fix
cargo clippy --manifest-path runner/Cargo.toml --all-targets --all-features -- -D warnings
```

`ktlint` and `clippy` run with `-D warnings` in CI, so a new violation fails the
build. Two lint families are deliberately relaxed in `.editorconfig`, with the
reasoning recorded next to each:

- **Wildcard imports** — `import androidx.compose.foundation.layout.*` is the
  style Compose's own samples use; the plain-JVM rule reported 22 legitimate
  imports.
- **Max line length** — a Composable is allowed to be long; real complexity is
  bounded by SonarQube and detekt, not by line width.

The `rust` commands deliberately omit `--all`: `komorei` is a path dependency
into the separate `komorei-sdk` checkout, and `--all` would reformat a repo that
has its own CI for exactly that.

### Kotlin / Android

```bash
# Fast compile; runs KSP, Hilt, Room, and uniffi code generation
./gradlew :app:compileDebugKotlin

# JVM test suite; this also finalizes testSdkRunnerUnitTest
./gradlew :app:testDebugUnitTest

# Run the wasm/JNA runner tests in their own JVM
./gradlew :app:testSdkRunnerUnitTest

# Build the debug APK
./gradlew :app:assembleDebug
```

The test suite uses Robolectric and Roborazzi. Golden screenshots are stored in:

```text
app/src/test/screenshots/
```

### Rust runner

```bash
cargo build --manifest-path runner/Cargo.toml
cargo test --manifest-path runner/Cargo.toml --all-targets --locked
```

### Building native libraries for a physical device

Install `cargo-ndk`, set `ANDROID_NDK_HOME`, and run:

```bash
cargo ndk \
  -t arm64-v8a \
  -t armeabi-v7a \
  -t x86_64 \
  -o app/src/main/jniLibs \
  build -p komorei-runner --release
```

## Local Configuration

`local.properties` is ignored by Git and must never be committed:

```properties
sdk.dir=/path/to/Android/sdk
google.drive.android.clientId=<release android client id>
google.drive.android.debugClientId=<debug android client id>
google.drive.web.clientId=<web client id>
```

The same values can be supplied through environment variables:

```text
GOOGLE_ANDROID_CLIENT_ID
GOOGLE_ANDROID_DEBUG_CLIENT_ID
GOOGLE_WEB_CLIENT_ID
```

Google Drive uses the following scope:

```text
https://www.googleapis.com/auth/drive.appdata
```

### Google OAuth

Because the application IDs are separated, two Android OAuth clients are required:

| Build | Package name | Signing certificate |
|---|---|---|
| Release | `git.shin.komorei` | Release keystore |
| Debug | `git.shin.komorei.dev` | Debug keystore |

The Web OAuth client ID is shared for `requestOfflineAccess`. Never embed an OAuth client secret in the APK.

Current certificate SHA-1 fingerprints:

```text
Release (git.shin.komorei): 11:94:00:88:21:94:5C:7E:9D:D3:3F:47:5C:E7:6C:19:DF:6E:86:15
Local debug keystore:      E0:5E:C0:BF:F6:CF:D7:38:81:F9:61:94:9B:5A:7E:D1:A8:DD:47:5F
```

The debug SHA-1 depends on the debug keystore on the machine performing the build. Use the fingerprint from the machine that creates the debug build.

## Release Signing

Release builds read signing configuration from the environment:

```text
KEYSTORE_PATH
KEYSTORE_ALIAS
STORE_PASSWORD
KEY_PASSWORD
```

Local release build:

```bash
KEYSTORE_PATH=/path/to/keystore.jks \
KEYSTORE_ALIAS=<alias> \
STORE_PASSWORD=<store-password> \
KEY_PASSWORD=<key-password> \
./gradlew :app:assembleRelease
```

Build the release App Bundle:

```bash
KEYSTORE_PATH=/path/to/keystore.jks \
KEYSTORE_ALIAS=<alias> \
STORE_PASSWORD=<store-password> \
KEY_PASSWORD=<key-password> \
./gradlew :app:bundleRelease
```

## GitHub Actions and Semantic Release

The workflows are located in `.github/workflows/`:

- `android-ci.yml` runs Rust tests, Kotlin tests, source fixtures, and the debug APK build.
- `android-release.yml` runs the CI gate, predicts the next semantic version, updates the Gradle version, builds signed APK/AAB files, and publishes a GitHub Release.

Release tooling uses Bun exclusively:

```bash
bun ci
bunx semantic-release --dry-run
bun run release
```

The release workflow requires these secrets:

```text
KEYSTORE_CONTENT
KEYSTORE_ALIAS
STORE_PASSWORD
KEY_PASSWORD
GOOGLE_ANDROID_CLIENT_ID
GOOGLE_WEB_CLIENT_ID
```

`GOOGLE_ANDROID_DEBUG_CLIENT_ID` is used by debug builds. `KEYSTORE_PASSWORD` is retained as a compatibility name for older workflow configurations.

The `komorei-sdk` and `sources` repositories must be public or otherwise accessible to the GitHub Runner before CI can check them out.

Semantic release updates the following values in `gradle.properties`:

```properties
VERSION_NAME=<semantic-version>
VERSION_CODE=<monotonic-android-version-code>
```

## OTA Updater

From **Settings → App Update**, Komorei checks the latest GitHub Release. The updater accepts an APK only when:

- exactly one APK asset is present;
- the asset has a valid `sha256` digest;
- the package name matches the installed app;
- the candidate `versionCode` is higher;
- the signing certificate matches the installed app.

The APK is downloaded into private cache storage, verified, and then handed to the Android installer. This is an updater for directly distributed APKs, not Google Play In-App Update.

## Logcat Server

Enter the `komorei logcat` server URL from **Settings → Advanced**:

```bash
komorei logcat --port 9000
```

For the Android Emulator, use the host machine address:

```text
http://10.0.2.2:9000
```

For a physical device, use the LAN IP printed by the CLI:

```text
http://192.168.x.x:9000
```

Each log line is sent asynchronously with HTTP POST while remaining available in the local **Server Log** screen.

## Deep Links

Komorei registers only the `komorei://` scheme.

### Add a source repository

```text
komorei://addSourceList?url=https%3A%2F%2Frepo.example%2Findex.min.json
```

### Install a source

```text
komorei://addSource?url=https%3A%2F%2Frepo.example%2Fsources%2Fdemo.krx
```

Query parameter URLs must be percent-encoded.

## Project Structure

```text
app/                 Android application module
runner/              Rust wasm runner and uniffi bridge
app/src/main/java/   Compose UI, navigation, data, and source host
app/src/test/        Robolectric, Roborazzi, and JVM tests
komorei-sdk/         SDK, CLI, and source API (separate repository)
sources/             `.krx` source collection (separate repository)
```

## Installing on the Emulator

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p git.shin.komorei.dev 1
```

## Security Notes

- Never commit keystores, `local.properties`, `client_secret*.json`, or `google-services.json`.
- Never put keystore passwords in source files or commit messages.
- `KEYSTORE_CONTENT` should contain only the base64-encoded keystore and must be stored in GitHub Actions Secrets.
- Protect the `main` branch and use protected secrets for public releases.

## Links

- GitHub: https://github.com/tachibana-shin/komorei-app
- Ko-fi: https://ko-fi.com/tachib_shin
- Discord: `@tachib.shin`
