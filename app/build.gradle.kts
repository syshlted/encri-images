/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: SystemHalted
 * SPDX-License-Identifier: Apache-2.0
 */

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

// Kotlin support is built into AGP since 9.0; applying org.jetbrains.kotlin.android alongside it is an
// error, not a redundancy.
plugins {
    alias(libs.plugins.android.application)
}

val versions = libs.versions

// Aggregate, so CI and the pre-push hook name one task rather than enumerating variants, and a variant added
// later is covered without anybody remembering to add it. The gate is at the bottom of this file.
val thirdPartyNotices =
    tasks.register("checkThirdPartyNotices") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks every variant's runtime classpath against the notices the app shows."
    }

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

    // The inherited warnings are fixed (ROADMAP.md), so a new one fails the build rather than joining a list.
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
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
                classes("com.systemhalted.encri.images.vault.*", "com.systemhalted.encri.images.notices.*")
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

// ---------------------------------------------------------------------------------------------
// The third-party notices gate (I19), encri-files' (F10), which is encri's (encri D138) without its list of
// notices owed, since this app has never owed one.
//
// The notices the app shows are written by hand, because what each library's licence asks for — a
// copyright line, a NOTICE file, the licence of code it carries from elsewhere — is not in any POM.
// What a build can check is that the list is complete: every module an APK carries is named in the
// notices, and nothing is named there that no longer ships.

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar(Char::uppercaseChar)
        val notices =
            tasks.register<CheckThirdPartyNotices>("check${name}ThirdPartyNotices") {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description = "Fails if ${variant.name}'s runtime classpath is not exactly what the notices name."
                modules.set(
                    variant.runtimeConfiguration.incoming.resolutionResult.rootComponent
                        .map(CheckThirdPartyNotices::modulesIn),
                )
                notices.set(layout.projectDirectory.file(CheckThirdPartyNotices.NOTICES))
                receipt.set(layout.buildDirectory.file("reports/third-party-notices/${variant.name}.txt"))
            }
        tasks.named("check") { dependsOn(notices) }
        thirdPartyNotices.configure { dependsOn(notices) }
    }
}

/**
 * Fails when a variant's runtime classpath carries a module the notices
 * (`app/src/main/assets/third-party-notices.txt`) do not name, or when they name one it does not carry.
 *
 * **Exact in both directions**: a notice for a library that left is a claim about the app that is no
 * longer true, and a list that may say more than the truth stops being read as the truth. **By version
 * too**, so a bump fails until somebody has read the new release's licence, NOTICE and copyright lines
 * and changed the version in the notices. That diff is the review.
 *
 * A module is named by a line holding nothing but `group:artifact:version`, which no licence text
 * contains. encri-api's modules are not third parties: they are this project's, and an included build
 * until encri-api is published (I1).
 *
 * What it does not cover: code compiled into another module's artifact, which is the notices' job to
 * know about, such as the Kotlin standard library's borrowed parts. Those are read off each library's
 * source at the version named, and a version bump is what sends someone back to read them.
 */
abstract class CheckThirdPartyNotices : DefaultTask() {
    @get:Input
    abstract val modules: SetProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val notices: RegularFileProperty

    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @TaskAction
    fun verify() {
        val shipped = modules.get()
        val noticed = noticedIn(notices.get().asFile)
        val problems =
            listOf(
                "Shipped, and NOT named in $NOTICES" to (shipped - noticed),
                "Named in $NOTICES and NOT shipped" to (noticed - shipped),
            ).filter { it.second.isNotEmpty() }
        if (problems.isNotEmpty()) throw GradleException(complaint(problems))
        receipt
            .get()
            .asFile
            .also { it.parentFile.mkdirs() }
            .writeText(shipped.sorted().joinToString("") { "$it noticed\n" })
    }

    private fun complaint(problems: List<Pair<String, Set<String>>>): String =
        buildString {
            append("The third-party notices do not match what ${modules.get().size} shipped modules are.\n")
            problems.forEach { (what, which) ->
                append("\n$what:\n")
                which.sorted().forEach { append("    $it\n") }
            }
            append(
                "\nA module that arrived needs its notice written: read its licence, its NOTICE file if it has\n" +
                    "one, and what its artifact compiles in, at that version. A version that moved needs the line\n" +
                    "changed once the new release's licence has been read. A module that left needs its lines\n" +
                    "deleted, and its licence text too if nothing else uses it.\n",
            )
        }

    private fun noticedIn(file: File): Set<String> =
        file
            .readLines()
            .map(String::trim)
            .filter(COORDINATE::matches)
            .toSet()

    companion object {
        const val NOTICES = "src/main/assets/third-party-notices.txt"

        /** encri-api's group, whose modules are this project's own (I1). */
        private const val OWN_GROUP = "com.systemhalted.encri"

        private val COORDINATE = Regex("""[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+""")

        /**
         * Every module in the resolved graph below [root], as `group:artifact:version`.
         *
         * The graph rather than the files, so a module with no artifact of its own, such as
         * kotlinx-coroutines-bom, is named as a reader of the dependency report sees it. A constraint is
         * not a module, and an unresolved dependency is refused rather than walked past, since a partial
         * graph would pass with notices missing.
         */
        fun modulesIn(root: ResolvedComponentResult): Set<String> {
            val seen = mutableSetOf<ComponentIdentifier>()
            val found = sortedSetOf<String>()
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                val component = queue.removeFirst()
                if (!seen.add(component.id)) continue
                (component.id as? ModuleComponentIdentifier)
                    ?.takeUnless { it.group == OWN_GROUP }
                    ?.let { found += "${it.group}:${it.module}:${it.version}" }
                component.dependencies.filterIsInstance<UnresolvedDependencyResult>().firstOrNull()?.let {
                    throw GradleException("${it.attempted} did not resolve, so the notices cannot be checked")
                }
                component.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .filterNot { it.isConstraint }
                    .forEach { queue += it.selected }
            }
            return found
        }
    }
}
