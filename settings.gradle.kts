rootProject.name = "kmp-ai"

val androidEnabled: Boolean =
    System.getenv("KMP_AI_ANDROID")?.toBoolean()
        ?: System.getProperty("kmp-ai.android")?.toBoolean()
        ?: false

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":llm")
include(":llm-catalog-qwen")
include(":llm-catalog-gemma")
include(":samples:jvm")
include(":samples:app")
