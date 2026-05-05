rootProject.name = "kmp-ai"

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
