apply(plugin = "com.android.library")

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>("kotlin") {
    androidTarget {
        publishLibraryVariants("release")
    }
    sourceSets.getByName("androidMain").dependencies {
        implementation("androidx.annotation:annotation:1.9.1")
    }
}

extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
    namespace = "io.github.fadizg.kmpai.llm"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                arguments += listOf(
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_CURL=OFF",
                    "-DGGML_OPENMP=OFF",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }
}
