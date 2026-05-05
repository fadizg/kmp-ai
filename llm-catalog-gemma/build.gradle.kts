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

    if (androidEnabled) androidTarget()

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
            api(project(":llm"))
        }
    }
}

if (androidEnabled) {
    apply(from = "build-android.gradle")
}

apply(from = "$rootDir/gradle/publishing.gradle.kts")
