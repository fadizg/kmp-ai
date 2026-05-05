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
}

if (androidEnabled) {
    apply(plugin = "com.android.library")
}

kotlin {
    jvmToolchain(21)

    jvm()

    if (androidEnabled) {
        androidTarget()
    }

    if (iosEnabled) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
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

apply(from = "$rootDir/gradle/publishing.gradle.kts")
