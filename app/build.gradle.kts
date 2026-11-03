/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// Kotlin support is built into AGP since 9.0; applying org.jetbrains.kotlin.android alongside it is an
// error, not a redundancy.
plugins {
    alias(libs.plugins.android.application)
}

val versions = libs.versions

android {
    namespace = "com.systemhalted.encri.images"
    compileSdk = versions.compileSdk.get().toInt()

    defaultConfig {
        // Permanent from the first build a user installs, because encri keys a grant on it (encri D120).
        applicationId = "com.systemhalted.encri.images"
        minSdk = versions.minSdk.get().toInt()
        targetSdk = versions.targetSdk.get().toInt()
        // encri D6's scheme, which this app shares: MAJOR*1000000 + MINOR*1000 + PATCH. encri keys a grant
        // on the version code too (encri D120), so every change to it asks the user again.
        versionCode = 1_000
        versionName = "0.1.0"

        // The device tests (I6), run in this app's own process, with its grant and nothing more.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        lintConfig = file("lint.xml")
    }

    dependenciesInfo {
        // encri D116: an APK signed by AGP would carry the dependency list encrypted to a key only
        // Google holds. The bundle keeps its plaintext copy.
        includeInApk = false
        includeInBundle = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The only way this app reaches the vault (encri D112): the published client, never SAF.
    implementation(libs.encri.client)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.adobe.xmpcore)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.glide)
    implementation(libs.material)
    implementation(libs.zoomimage.view.glide)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)

    // Robolectric is a JUnit 4 runner and the rest of the suite is JUnit 5, so the vintage engine runs
    // both under one task, encri's arrangement for encri's reason.
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.junit.vintage.engine)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Robolectric at SDK 36 on JDK 21 (encri-api L8).
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

// I1: 80% of the lines written here. The screens inherited from Glimpse came with no tests, and a floor over
// them would measure that rather than this code.
kover {
    reports {
        filters {
            includes {
                classes("com.systemhalted.encri.images.vault.*")
            }
        }
        verify {
            rule("I1: line coverage of src/main/kotlin") { minBound(80, CoverageUnit.LINE) }
        }
    }
}

// I1: what came from Glimpse stays in src/main/java in upstream's own formatting, so a fix there can be
// picked across; what is written here goes in src/main/kotlin and passes every gate.
ktlint {
    filter {
        exclude { it.file.path.contains("/src/main/java/") }
    }
}

detekt {
    source.setFrom(files("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin"))
}
