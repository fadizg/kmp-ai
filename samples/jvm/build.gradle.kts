plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":llm"))
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.github.fadizg.kmpai.sample.MainKt")
}
