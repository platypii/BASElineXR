// (c) Meta Platforms, Inc. and affiliates. Confidential and proprietary.

pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    google()
    maven(url = "https://jitpack.io")
  }
}

rootProject.name = "BASElineXR"

include(":app")
