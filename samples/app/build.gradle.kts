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
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

if (androidEnabled) {
    apply(plugin = "com.android.application")
}

kotlin {
    jvmToolchain(21)

    jvm()

    if (androidEnabled) androidTarget()

    if (iosEnabled) {
        listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
            target.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":llm"))
            implementation(project(":llm-catalog-qwen"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        if (androidEnabled) {
            getByName("androidMain").dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.fadizg.kmpai.sample.app.DesktopMainKt"
        nativeDistributions {
            packageName = "kmp-ai-sample"
            packageVersion = "0.1.0"
        }
    }
}

if (androidEnabled) {
    apply(from = "build-android.gradle")
}
