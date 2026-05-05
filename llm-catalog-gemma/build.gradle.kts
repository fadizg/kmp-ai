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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
    coordinates("io.github.fadizg.kmpai", project.name, version.toString())
    pom {
        name.set("kmp-ai · ${project.name}")
        description.set("Curated Gemma 2 / 3 ModelSource constants for kmp-ai")
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
            developer { id.set("fadizg"); name.set("fadizg"); url.set("https://github.com/fadizg") }
        }
        scm {
            connection.set("scm:git:https://github.com/fadizg/kmp-ai.git")
            developerConnection.set("scm:git:ssh://git@github.com/fadizg/kmp-ai.git")
            url.set("https://github.com/fadizg/kmp-ai")
        }
    }
}
