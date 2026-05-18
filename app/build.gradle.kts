// (c) Meta Platforms, Inc. and affiliates. Confidential and proprietary.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.meta.spatial.plugin)
}

android {
    namespace = "com.platypii.baselinexr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.platypii.baselinexr"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndkVersion = "27.0.12077973"
    }

    packaging { resources.excludes.add("META-INF/LICENSE") }

    lint { abortOnError = false }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=com.meta.spatial.core.SpatialSDKExperimentalAPI"
    }
    flavorDimensions.add("device")
    productFlavors {
        create("quest") {
            dimension = "device"
        }
        create("mobile") {
            dimension = "device"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Meta Spatial SDK libs
    implementation(libs.meta.spatial.sdk.base)
    implementation(libs.meta.spatial.sdk.ovrmetrics)
    implementation(libs.meta.spatial.sdk.toolkit)
    implementation(libs.meta.spatial.sdk.physics)
    implementation(libs.meta.spatial.sdk.vr)
//  implementation(libs.meta.spatial.sdk.mruk)
    implementation(libs.meta.spatial.sdk.castinputforward)
    implementation(libs.meta.spatial.sdk.hotreload)
    implementation(libs.meta.spatial.sdk.datamodelinspector)

    // Baseline deps
    implementation(libs.blessed.android)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.eventbus)

//  ksp("com.google.code.gson:gson:2.11.0")
//  ksp("com.meta.spatial.plugin:com.meta.spatial.plugin.gradle.plugin:$metaSpatialSdkVersion")
}

// Meta Spatial SDK: export scenes before building any debug variant (quest/mobile)
afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") && it.name.endsWith("Debug") }.configureEach {
        dependsOn("export")
    }
}

val projectDir = layout.projectDirectory
val sceneDirectory = projectDir.dir("scenes")

spatial {
    allowUsageDataCollection.set(true)
    scenes {
        cliPath.set("C:\\Program Files\\Meta Spatial Editor\\v16\\Resources\\CLI.exe")
        // if you have installed Meta Spatial Editor somewhere else, update the file path.
        // cliPath.set("/Applications/Meta Spatial Editor.app/Contents/MacOS/CLI")
        exportItems {
            item {
                projectPath.set(sceneDirectory.file("Main.metaspatial"))
                outputPath.set(projectDir.dir("src/main/assets/scenes"))
            }
            item {
                projectPath.set(sceneDirectory.file("Composition/Main.metaspatialcomp"))
                outputPath.set(projectDir.dir("src/main/assets/scenes"))
            }
        }
        hotReload {
            appPackage.set("com.platypii.baselinexr")
            appMainActivity.set(".BaselineActivity")
            assetsDir.set(File("src/main/assets"))
        }
    }
    shaders { sources.add(project.layout.projectDirectory.dir("src/shaders")) }
}
