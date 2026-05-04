rootProject.name = "kmp-ai"

val androidEnabled: Boolean =
    System.getenv("KMP_AI_ANDROID")?.toBoolean()
        ?: System.getProperty("kmp-ai.android")?.toBoolean()
        ?: false

pluginManagement {
    val androidEnabled =
        System.getenv("KMP_AI_ANDROID")?.toBoolean()
            ?: System.getProperty("kmp-ai.android")?.toBoolean()
            ?: false
    repositories {
        gradlePluginPortal()
        mavenCentral()
        if (androidEnabled) google()
    }
}

dependencyResolutionManagement {
    val androidEnabled =
        System.getenv("KMP_AI_ANDROID")?.toBoolean()
            ?: System.getProperty("kmp-ai.android")?.toBoolean()
            ?: false
    repositories {
        mavenCentral()
        if (androidEnabled) google()
    }
}

include(":llm")
include(":samples:jvm")
