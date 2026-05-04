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
