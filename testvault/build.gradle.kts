/*
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

// The vault the device tests need, seeded and removed on a phone (I6). A library module only for the sake of
// its instrumented tests, whose APK instruments itself: a package of its own, so a uid and a grant of its own,
// with the rights to write that the app never asks for. The app's own tests run in the app's process and
// would widen the app's grant instead.
plugins {
    alias(libs.plugins.android.library)
}

val versions = libs.versions

android {
    namespace = "com.systemhalted.encri.images.testvault"
    compileSdk = versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        targetSdk = versions.targetSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    androidTestImplementation(libs.encri.client)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

detekt {
    source.setFrom(files("src/androidTest/kotlin"))
}
