// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
  id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

// ktlint — Kotlin style gate. The Rust half of the tree is already gated by
// `cargo fmt` + `cargo clippy` (see .husky/ and Android CI), so this is what
// makes the Kotlin side symmetrical: `./gradlew ktlintCheck` in CI,
// `./gradlew ktlintFormat` to fix, and a pre-commit hook catches it locally.
//
// Rules mirror the committed .editorconfig — do not duplicate values here.
// `android = true` keeps the Android/Compose idioms (rather than plain-JVM
// defaults) so the formatter does not fight the framework.
subprojects {
  apply(plugin = "org.jlleitschuh.gradle.ktlint")

  ktlint {
    // The ktlint CLI version is intentionally NOT pinned: the plugin ships a
    // matching default, and overriding it risks pairing a CLI with a
    // mismatched editorconfig schema. Style rules live in .editorconfig.
    android.set(true)
    enableExperimentalRules.set(false)
    filter {
      exclude("**/generated/**")
      exclude("**/build/**")
    }
  }
}
// NOTE: the plugin's own git hook is deliberately NOT installed — husky owns
// every hook in this repo (see .husky/pre-commit), and two competing
// pre-commit installers is how you end up with one silently overwriting the
// other.
