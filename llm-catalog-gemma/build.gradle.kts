val androidEnabled: Boolean =
    System.getenv("KMP_AI_ANDROID")?.toBoolean()
        ?: System.getProperty("kmp-ai.android")?.toBoolean()
        ?: false

val iosEnabled: Boolean =
    System.getenv("KMP_AI_IOS")?.toBoolean()
        ?: System.getProperty("kmp-ai.ios")?.toBoolean()
        ?: false

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
