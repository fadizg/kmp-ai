val androidEnabled: Boolean =
    System.getenv("KMP_AI_ANDROID")?.toBoolean()
        ?: System.getProperty("kmp-ai.android")?.toBoolean()
        ?: false

buildscript {
    val enabled =
        System.getenv("KMP_AI_ANDROID")?.toBoolean()
            ?: System.getProperty("kmp-ai.android")?.toBoolean()
            ?: false
    if (enabled) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools.build:gradle:8.7.0")
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(libs.llamacpp)
        }
    }
}

if (androidEnabled) {
    apply(from = "build-android.gradle.kts")
}
