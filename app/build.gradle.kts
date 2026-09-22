import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  // alias(libs.plugins.google.services)
}

android {
  namespace = "git.shin.komorei"
  compileSdk = 37

  defaultConfig {
    applicationId = "git.shin.komorei"
    minSdk = 24
    //noinspection OldTargetApi
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  // uniffi Kotlin bindings are generated into build/ by the tasks below and
  // wired as a GENERATED source dir via the Variant API (see the
  // `androidComponents.onVariants` block). AGP 9 error-deprecates the SourceSet
  // srcDir API, so the generated dir must NOT be added here.
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true
    all { test ->
      // The uniffi bindings read `uniffi.component.komorei_runner.libraryOverride`
      // to locate the cdylib. Point the JVM tests at the host-built debug .so
      // (File.exists is unreliable inside the Robolectric sandbox classloader).
      test.systemProperty(
        "uniffi.component.komorei_runner.libraryOverride",
        rootProject.file("runner/target/debug/libkomorei_runner.so").absolutePath,
      )
      test.systemProperty(
        "komorei.test.exampleKrx",
        rootProject.file("komorei-sdk/examples/example-source/package.krx").absolutePath,
      )
      // The app's own fake source (rich catalog), packaged from
      // sources/sources/vi.fake-source/package.krx — the committed fixture in
      // main assets.
      test.systemProperty(
        "komorei.test.fakeKrx",
        rootProject.file("app/src/main/assets/sources/fake-vi-source.krx").absolutePath,
      )
      // The real OPhim source (sources/sources/vi.ophim) — exercised end-to-end
      // against a local classic-OPHIM fixture server in
      // OphimSourceRunnerIntegrationTest.
      test.systemProperty(
        "komorei.test.ophimKrx",
        rootProject.file("sources/sources/vi.ophim/package.krx").absolutePath,
      )
      // The real Nguồn C source (sources/sources/vi.nguonc) — exercised
      // end-to-end against a local fixture server (including the
      // bootstrap → issue embed grant) in
      // NguoncSourceRunnerIntegrationTest.
      test.systemProperty(
        "komorei.test.nguoncKrx",
        rootProject.file("sources/sources/vi.nguonc/package.krx").absolutePath,
      )
      // The real Nguồn Phim source (sources/sources/vi.nguonphim) — the same
      // nguonc-style engine pointed at api.nguonphim.net; exercised against the
      // same fixture shape in NguonphimSourceRunnerIntegrationTest.
      test.systemProperty(
        "komorei.test.nguonphimKrx",
        rootProject.file("sources/sources/vi.nguonphim/package.krx").absolutePath,
      )
      // The real Nguồn Phim site source (sources/sources/vi.nguonphime) — an
      // HTML scrape (nguonphime.site) with the NP Checker bounce + base64 grab
      // playlists + the streamc grant; exercised end-to-end against a local
      // fixture server in NguonphimeSourceRunnerIntegrationTest.
      test.systemProperty(
        "komorei.test.nguonphimeKrx",
        rootProject.file("sources/sources/vi.nguonphime/package.krx").absolutePath,
      )
    }
  } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// ─── uniffi Kotlin bindings — generated at build time (flutter_rust_bridge style) ───
// The Rust trait lives in runner/; bindgen emits
// build/generated/uniffi/kotlin/git/shin/komorei/sdk/runner/komorei_runner.kt.
// runner/uniffi.toml sets package_name (kills the old sed); the
// `RunnerException.Source` patch is re-applied by patchUniffiBindings (uniffi
// 0.32.1 Kotlin codegen emits a REDECLARING computed `override val message` —
// keep the constructor field). Use explicit Exec TASKS, NOT `project.exec {}`
// (the Project.exec/javaexec family was removed in Gradle 9.6+).
val uniffiBindingsDir = layout.buildDirectory.dir("generated/uniffi/kotlin")
val uniffiBindgenPath = providers
  .gradleProperty("uniffiBindgen")
  .orElse("${System.getProperty("user.home")}/.cargo/bin/uniffi-bindgen")
  .get()

val cargoBuildUniffi = tasks.register<Exec>("cargoBuildUniffi") {
  description = "Build the host debug cdylib that uniffi-bindgen reads metadata from"
  val runnerDir = rootProject.layout.projectDirectory.dir("runner")
  workingDir = runnerDir.asFile
  // `cargo` is invoked relative to the runner workspace (uniffi.toml + Cargo.toml).
  commandLine("cargo", "build")
  inputs.dir(runnerDir.dir("src"))
  inputs.files(runnerDir.file("Cargo.toml"), runnerDir.file("Cargo.lock"))
  outputs.file(runnerDir.file("target/debug/libkomorei_runner.so"))
}

val generateUniffiBindings = tasks.register<Exec>("generateUniffiBindings") {
  description = "Run uniffi-bindgen to emit the Kotlin bindings into build/generated/uniffi/kotlin"
  dependsOn(cargoBuildUniffi)
  val runnerDir = rootProject.layout.projectDirectory.dir("runner")
  workingDir = runnerDir.asFile
  commandLine(
    uniffiBindgenPath,
    "generate",
    "--library", "target/debug/libkomorei_runner.so",
    "--language", "kotlin",
    "--no-format",
    "--out-dir", uniffiBindingsDir.get().asFile.absolutePath,
  )
  inputs.file(runnerDir.file("uniffi.toml"))
  // the .so produced by cargoBuildUniffi doubles as the ordering edge
  inputs.file(runnerDir.file("target/debug/libkomorei_runner.so"))
}

// Custom task so the generated dir can be a managed @OutputDirectory — that's
// what `androidComponents.onVariants` needs to wire it as a generated source dir.
abstract class PatchUniffiBindings : DefaultTask() {
  @get:OutputDirectory
  abstract val bindingsDir: DirectoryProperty

  @TaskAction
  fun patch() {
    val f = bindingsDir.get().asFile.resolve("git/shin/komorei/sdk/runner/komorei_runner.kt")
    val patched = f.readText()
      .replace(
        "        val `message`: kotlin.String",
        "        override val `message`: kotlin.String",
      )
      .replace(
        "        override val message\n" +
          "            get() = \"code=\${ `code` }, message=\${ `message` }\"\n" +
          "    }",
        "        // NOTE: no computed `override val message` here — the constructor field\n" +
          "        // IS the message (uniffi 0.32.1 codegen emits a duplicate override that\n" +
          "        // would be a REDECLARATION; patched on generation).\n" +
          "    }",
      )
    check("override val `message`: kotlin.String" in patched) {
      "RunnerException.Source constructor field not found — uniffi codegen changed, update the patch in app/build.gradle.kts"
    }
    check("NOTE: no computed `override val message` here" in patched) {
      "RunnerException.Source computed getter not found — uniffi codegen changed, update the patch in app/build.gradle.kts"
    }
    f.writeText(patched)
  }
}

val patchUniffiBindings = tasks.register<PatchUniffiBindings>("patchUniffiBindings") {
  description = "Re-apply the RunnerException.Source patch (uniffi 0.32.1 codegen bug)"
  dependsOn(generateUniffiBindings)
  bindingsDir.set(uniffiBindingsDir)
  inputs.dir(uniffiBindingsDir)
}

// Wire the generated bindings into every variant's Kotlin (java fallback)
// generated-source dir — `addGeneratedSourceDirectory` also creates the task
// dependency for compile*Kotlin/KSP automatically. This is AGP 9's replacement
// for the error-deprecated SourceSet srcDir API.
androidComponents {
  onVariants { variant ->
    (variant.sources.kotlin ?: variant.sources.java)?.addGeneratedSourceDirectory(
      patchUniffiBindings,
    ) { it.bindingsDir }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

// googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation.layout)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.datasource.okhttp)
  implementation(libs.androidx.media3.ui.compose)
  implementation(libs.androidx.media3.ui.compose.material3)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi)
  implementation(libs.okhttp)
  // Phase 4 SDK — the `.krx` runner embedded in the app:
  implementation(libs.jsoup) // the runner's HTML DOM lives here (KrxHostImpl)
  // The uniffi-generated bindings (git.shin.komorei.sdk.runner) are JNA-based;
  // the @aar pulls libjnidispatch for the Android ABIs. Version pinned in the catalog.
  implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
  // JVM unit tests (Robolectric) need JNA's bundled platform natives — the
  // @aar variant only carries the Android libjnidispatch.
  testImplementation("net.java.dev.jna:jna:${libs.versions.jna.get()}")
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.hilt.android)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  "ksp"(libs.hilt.compiler)
}
