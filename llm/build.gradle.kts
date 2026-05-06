import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// Targets default to ON. Disable an individual target with a Gradle property:
//   gradle.properties:        kmp-ai.android=false
//   command line:             -Pkmp-ai.android=false
//   user-wide:                ~/.gradle/gradle.properties
// (Env vars KMP_AI_ANDROID / KMP_AI_IOS still work as a fallback for CI.)
val androidEnabled: Boolean =
    (findProperty("kmp-ai.android") as String?)?.toBooleanStrictOrNull()
        ?: System.getenv("KMP_AI_ANDROID")?.toBooleanStrictOrNull()
        ?: true

val iosEnabled: Boolean =
    (findProperty("kmp-ai.ios") as String?)?.toBooleanStrictOrNull()
        ?: System.getenv("KMP_AI_IOS")?.toBooleanStrictOrNull()
        ?: true

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

if (androidEnabled) {
    apply(plugin = "com.android.library")
}

group = "io.github.fadizg.kmpai"
version = providers.gradleProperty("kmp-ai.version").orNull ?: "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)

    jvm()

    if (androidEnabled) {
        androidTarget()
    }

    if (iosEnabled) {
        // Bundle the iOS slices into a single KmpAI.xcframework for Swift
        // Package Manager distribution. Built by `assembleKmpAIXCFramework`
        // (debug/release variants exist via `assembleKmpAIDebugXCFramework`
        // and `assembleKmpAIReleaseXCFramework`). Output:
        //   llm/build/XCFrameworks/{debug,release}/KmpAI.xcframework
        // Consumed by Package.swift at the repo root.
        val xcf = XCFramework("KmpAI")
        listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
            target.binaries.framework {
                baseName = "KmpAI"
                isStatic = false
                xcf.add(this)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain {
            kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
            dependencies {
                implementation(libs.llamacpp)
            }
        }
        if (androidEnabled) {
            getByName("androidMain") {
                kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
                dependencies {
                    implementation("androidx.annotation:annotation:1.9.1")
                    api("androidx.startup:startup-runtime:1.2.0")
                }
            }
        }
    }
}

if (androidEnabled) {
    apply(from = "build-android.gradle")
}

if (iosEnabled) {
    apply(from = "build-ios.gradle")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    // Only enforce signing when a key is actually available. Without this
    // guard, plain `publishToMavenLocal` (used in local dev and the JVM CI
    // smoke test) errors with "no configured signatory".
    val hasSigningKey = listOf("signingInMemoryKey", "signing.keyId").any { providers.gradleProperty(it).isPresent }
        || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
    if (hasSigningKey) {
        signAllPublications()
    }
    coordinates("io.github.fadizg.kmpai", project.name, version.toString())
    pom {
        name.set("kmp-ai · ${project.name}")
        description.set("Kotlin Multiplatform offline LLM library backed by llama.cpp")
        inceptionYear.set("2026")
        url.set("https://github.com/fadizg/kmp-ai")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("fadizg")
                name.set("fadizg")
                url.set("https://github.com/fadizg")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/fadizg/kmp-ai.git")
            developerConnection.set("scm:git:ssh://git@github.com/fadizg/kmp-ai.git")
            url.set("https://github.com/fadizg/kmp-ai")
        }
    }
}
