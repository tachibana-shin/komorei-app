pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // For `com.github.jeziellago:compose-markdown`, which is published through
    // JitPack rather than to Maven Central — without this the dependency
    // resolves nowhere and the build fails on a missing POM.
    maven { url = uri("https://jitpack.io") }
  }
}

rootProject.name = "Komorei"
include(":app")
