// (c) Meta Platforms, Inc. and affiliates. Confidential and proprietary.

pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
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
