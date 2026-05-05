# kmp-ai

Kotlin Multiplatform library for running offline LLMs on JVM, Android, and iOS,
backed by [llama.cpp](https://github.com/ggml-org/llama.cpp).

- Coroutine `Flow<Token>` streaming
- Built-in HuggingFace / URL / local-file model resolution with SHA-256 verification
- Chat templates: ChatML, Llama 3, Gemma, Custom
- Curated model catalogs (Qwen 2.5, Gemma 2/3)
- KMP Compose Multiplatform sample app (Desktop + Android + iOS)

## Status

| Target          | Status                                                                   |
| --------------- | ------------------------------------------------------------------------ |
| JVM (Desktop)   | Works out of the box — `de.kherud:llama` ships natives for Mac/Linux/Win |
| Android         | Builds llama.cpp via NDK at consumer build time. Needs NDK 27.           |
| iOS             | Cinterop bindings against a prebuilt `llama.xcframework`. Bring your own.|

## Installation

The fastest path while the library is in 0.x is **JitPack**.

In the **consumer** project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://jitpack.io") }
    }
}
```

In the consumer module:

```kotlin
// Plain JVM / Android Gradle module:
dependencies {
    implementation("com.github.fadizg.kmp-ai:llm:v0.1.0")
    implementation("com.github.fadizg.kmp-ai:llm-catalog-qwen:v0.1.0")
}

// KMP module:
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("com.github.fadizg.kmp-ai:llm:v0.1.0")
        implementation("com.github.fadizg.kmp-ai:llm-catalog-qwen:v0.1.0")
    }
}
```

> JitPack rewrites the groupId for multi-module repos. Coordinate is
> `com.github.<owner>.<repo>:<module>:<git-tag>`, **not** the
> `io.github.fadizg.kmpai` groupId from the POM.

Other distribution options are documented under [Distribution](#distribution).

## Quick start

```kotlin
import io.github.fadizg.kmpai.catalog.Qwen
import io.github.fadizg.kmpai.llm.*

suspend fun main() {
    // Zero-arg on every platform — Android picks up the Application context
    // automatically via AndroidX Startup.
    val env = LlmEnvironment.default()

    // load() resolves the model (downloading + caching on first run)
    // and loads it into a fresh engine.
    env.load(Qwen.Qwen2_5_0_5B_Q4).use { engine ->
        val chat = ChatSession(
            engine = engine,
            template = Qwen.template,
            systemPrompt = "You are a concise assistant. Reply in one short paragraph.",
        )
        chat.send("In one sentence, what is Kotlin Multiplatform?")
            .collect { token -> print(token.text) }
    }
}
```

That's the whole API surface for the common case. The same five lines run
identically on JVM, Android, and iOS.

If you want progress callbacks during the download, use the underlying
repository directly:

```kotlin
env.repository.resolve(source).collect { progress ->
    when (progress) {
        is DownloadProgress.Running -> println("${progress.bytes / 1_000_000} MB")
        is DownloadProgress.Done    -> println("ready")
        is DownloadProgress.Failed  -> throw progress.cause
    }
}
val engine = env.load(source)
```

## Integrating into an existing KMP project

After adding the dependency (see [Installation](#installation)), do… nothing
special. Bind `LlmEnvironment.default()` once in `commonMain`:

```kotlin
// shared/src/commonMain/.../KoinModule.kt
val llmModule = module {
    single { LlmEnvironment.default() }
}
```

That's it — no per-platform `actual fun platformModule()` edits, no
`ModelPathResolver` interface, no platform-specific bindings. On Android the
Application context is captured automatically via AndroidX Startup; on JVM
and iOS the call is genuinely zero-arg.

Consume from `commonMain` like any other dependency:

```kotlin
// shared/src/commonMain/.../ChatRepository.kt
class ChatRepository(private val env: LlmEnvironment) {
    suspend fun reply(userMessage: String): Flow<String> = flow {
        val engine = env.load(Qwen.Qwen2_5_0_5B_Q4)
        ChatSession(engine, Qwen.template).send(userMessage)
            .collect { emit(it.text) }
    }
}
```

If your Android consumer has disabled AndroidX Startup (rare —
`tools:node="remove"` on the InitializationProvider), fall back to passing
the context explicitly:

```kotlin
// shared/src/androidMain/.../KoinModule.kt
single { LlmEnvironment(androidContext()) }    // overrides the common binding
```

## Concepts

### `LlmEngine`

The low-level inference handle. Implementation is per-platform; consumers always
go through the multiplatform `LlmEngineFactory`.

```kotlin
interface LlmEngine : AutoCloseable {
    val info: ModelInfo
    fun generate(prompt: String, params: SamplingParams): Flow<Token>
    suspend fun complete(prompt: String, params: SamplingParams): String
    fun tokenize(text: String): IntArray
    fun embed(text: String): FloatArray
}
```

### `ModelSource` and `ModelRepository`

`ModelSource` describes *where* a model lives. Three flavours:

```kotlin
ModelSource.HuggingFace(
    repo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
    file = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
)
ModelSource.Url("https://example.com/model.gguf", sha256 = "...")
ModelSource.LocalFile("/abs/path/to/model.gguf")
```

`ModelRepository` resolves a `ModelSource` to a local file, downloading and caching
as needed. Two implementations:

- `DefaultModelRepository(File)` — JVM and Android (uses `HttpURLConnection`)
- `IosModelRepository(NSURL)` — iOS (uses `NSURLSessionDownloadTask`)

Both expose:

```kotlin
fun resolve(source: ModelSource): Flow<DownloadProgress> // streaming
suspend fun path(source: ModelSource): String           // blocks until done
suspend fun remove(source: ModelSource)
fun list(): List<CachedModel>
```

`DownloadProgress` is a sealed type — `Running(bytes, total)`, `Done(path)`, `Failed(cause)`.

### `ChatSession` and `ChatTemplate`

`ChatSession` wraps an engine with a template-aware message log:

```kotlin
val chat = ChatSession(engine, ChatTemplate.ChatML, systemPrompt = "...")
chat.send("hello").collect { print(it.text) }
chat.send("and now in french").collect { print(it.text) }
chat.history    // List<ChatMessage>
chat.reset()    // clear, keep system prompt
```

Built-in templates: `ChatML` (Qwen, many open models), `Llama3`, `Gemma`. For
anything else, use `ChatTemplate.Custom(formatter, stops)`.

### `SamplingParams`

```kotlin
SamplingParams(
    maxTokens = 256,
    temperature = 0.7f,
    topP = 0.9f,
    topK = 40,
    repeatPenalty = 1.1f,
    seed = null,            // null = random
    stop = listOf("</end>"),
)
```

### Catalogs

`:llm-catalog-qwen` and `:llm-catalog-gemma` are tiny modules holding curated
`ModelSource` constants and matched `ChatTemplate`s:

```kotlin
import io.github.fadizg.kmpai.catalog.Qwen
import io.github.fadizg.kmpai.catalog.Gemma

Qwen.Qwen2_5_0_5B_Q4   // ~350 MB, smallest practical Qwen
Qwen.Qwen2_5_1_5B_Q4   // ~1.0 GB
Qwen.Qwen2_5_3B_Q4     // ~1.9 GB
Qwen.Qwen2_5_7B_Q4     // ~4.7 GB
Qwen.Qwen2_5_Coder_1_5B_Q4
Qwen.template          // ChatTemplate.ChatML

Gemma.Gemma2_2B_Q4     // ~1.6 GB
Gemma.Gemma3_1B_Q4     // ~750 MB
Gemma.template         // ChatTemplate.Gemma
```

## Sample apps

The repo ships two runnable samples.

### `:samples:jvm` — headless CLI

```bash
./gradlew :samples:jvm:run
./gradlew :samples:jvm:run --args="What is Kotlin Multiplatform?"

# Use a local GGUF you already have:
./gradlew :samples:jvm:run --args="--model=/path/to/model.gguf Hello"

# Pull a specific file from a HF repo:
./gradlew :samples:jvm:run --args="--hf=Qwen/Qwen2.5-1.5B-Instruct-GGUF --file=qwen2.5-1.5b-instruct-q4_k_m.gguf"

# Arbitrary URL with optional SHA-256:
./gradlew :samples:jvm:run --args="--url=https://example.com/model.gguf --sha256=<hex>"
```

### `:samples:app` — Compose Multiplatform chat UI

One `App()` composable shared across Desktop, Android, and iOS. Per-platform
entry points wire in the right `ModelRepository`.

```bash
# Desktop (no extra setup)
./gradlew :samples:app:run

# Android (needs ANDROID_HOME + NDK 27)
./gradlew :samples:app:installDebug

# iOS (needs llama.xcframework — see "Building from source" below)
./gradlew :samples:app:linkDebugFrameworkIosArm64
# then embed ComposeApp.framework in an Xcode project, present MainViewController()
```

## Building from source

```bash
git clone https://github.com/fadizg/kmp-ai
cd kmp-ai
git submodule update --init --recursive   # only needed for Android (vendors llama.cpp)

./gradlew :llm:jvmJar                     # JVM artifact
./gradlew :samples:jvm:run                # headless CLI
./gradlew :samples:app:run                # desktop UI
```

By default all targets (JVM, Android, iOS) are declared. If you don't have a
particular toolchain locally, disable that target with a Gradle property:

```bash
# command line
./gradlew build -Pkmp-ai.android=false -Pkmp-ai.ios=false

# permanent: add to ~/.gradle/gradle.properties
kmp-ai.android=false
kmp-ai.ios=false
```

`KMP_AI_ANDROID` / `KMP_AI_IOS` env vars still work as a fallback for CI.

### Android prerequisites

- Android SDK (`ANDROID_HOME` set, or `local.properties` with `sdk.dir`)
- NDK 27.0.12077973 (the CMake config requires this exact version)
- ~3 GB free for the llama.cpp build artifacts

```bash
./gradlew :llm:assembleRelease
./gradlew :samples:app:installDebug
```

First Android build is slow (~5 min) because llama.cpp is compiled from source
per ABI; incremental builds are fast.

### iOS prerequisites

- Xcode + macOS host
- A prebuilt `llama.xcframework` at `$rootDir/.cache/llama.xcframework`,
  overridable via the `kmp-ai.iosFramework` Gradle property

To produce the xcframework from upstream llama.cpp:

```bash
git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
cmake --workflow ios-arm64-release
cp -R build-ios/llama.xcframework /path/to/kmp-ai/.cache/llama.xcframework
```

Then:

```bash
./gradlew :llm:linkDebugFrameworkIosArm64
```

## Why a model repository instead of bundled weights?

Model files are 300 MB – 5 GB. They don't belong inside Gradle artifacts:

- Maven Central rejects anything over a few hundred MB
- Many model licenses (Llama, Gemma, Mistral) restrict redistribution
- They bloat every CI run and developer cache
- They couple model versioning to library versioning — different cadences

Instead, `ModelRepository` resolves a `ModelSource` (Hugging Face, URL, or
local file), downloads once with optional SHA-256 verification, and caches
it under the platform's user cache dir. Apps can also bundle the GGUF via
Android Play Asset Delivery / iOS On-Demand Resources and pass the path to
`LlmEngineFactory.load(...)`.

## Distribution

This library can be consumed five ways. Pick one based on your needs.

### 1. JitPack (recommended for public)

Documented above. Consumer just adds the JitPack repo and uses
`com.github.fadizg.kmp-ai:<module>:<tag>` coordinates.

JitPack only builds the JVM variant by default. To extend to Android, edit
`jitpack.yml` to install NDK 27 and set `KMP_AI_ANDROID=true`. iOS is
unsupported on JitPack since their build environment is Linux.

### 2. Composite build (no publish needed)

Best for actively developing kmp-ai alongside a consumer project. In the
consumer's `settings.gradle.kts`:

```kotlin
includeBuild("../kmp-ai")
```

```kotlin
// consumer build.gradle.kts
dependencies {
    implementation("io.github.fadizg.kmpai:llm")
    implementation("io.github.fadizg.kmpai:llm-catalog-qwen")
}
```

All targets work, source changes are picked up immediately.

### 3. mavenLocal (single machine)

```bash
# in kmp-ai (all targets on by default — needs Android SDK + iOS xcframework)
./gradlew publishToMavenLocal -Pkmp-ai.version=0.1.0

# JVM-only publish on a machine without those toolchains:
./gradlew publishToMavenLocal -Pkmp-ai.version=0.1.0 -Pkmp-ai.android=false -Pkmp-ai.ios=false
```

```kotlin
// consumer settings.gradle.kts
dependencyResolutionManagement {
    repositories { mavenLocal(); mavenCentral(); google() }
}

// consumer build.gradle.kts
dependencies { implementation("io.github.fadizg.kmpai:llm:0.1.0") }
```

### 4. GitHub Packages

Requires a classic PAT with `read:packages` + `write:packages` (and `repo`):

```bash
GITHUB_USER=fadizg GITHUB_TOKEN=ghp_xxx ./gradlew publish -Pkmp-ai.version=0.1.0
```

Consumer needs the same PAT to read — GitHub Packages requires auth even for
public packages.

```kotlin
// consumer settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            url = uri("https://maven.pkg.github.com/fadizg/kmp-ai")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_USER")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### 5. Maven Central (not yet wired)

The conventional choice for OSS libraries. Requires a Sonatype OSSRH account,
GPG signing, and the `io.github.gradle-nexus.publish-plugin`. Not configured
in this repo yet.

## Project layout

```
kmp-ai/
├── llm/                          KMP library: engine, repository, chat
│   ├── src/commonMain/           public API (interfaces, data classes)
│   ├── src/jvmAndAndroidMain/    DefaultModelRepository (HttpURLConnection)
│   ├── src/jvmMain/              LlamaCppEngine (de.kherud:llama)
│   ├── src/androidMain/          AndroidLlamaCppEngine + JNI bridge + cpp/
│   └── src/iosMain/              IosLlamaCppEngine + cinterop bindings
├── llm-catalog-qwen/             curated Qwen ModelSource constants
├── llm-catalog-gemma/            curated Gemma ModelSource constants
├── samples/
│   ├── jvm/                      headless CLI
│   └── app/                      Compose MP chat UI (Desktop + Android + iOS)
├── gradle/
│   └── publishing.gradle.kts     shared maven-publish config
└── jitpack.yml                   JitPack build instructions
```

## Caveats

- **Variants are publish-time gated.** A Maven artifact published with
  `kmp-ai.android=false` has no Android variant — Android consumers can't
  resolve it. The default ("all targets on") works as long as the publishing
  machine has the right toolchains; publish from a Mac with the Android SDK
  and `llama.xcframework` to get a fully multi-target artifact.
- **No prebuilt natives in the AAR.** Android consumers' first build runs
  CMake on the bundled llama.cpp.
- **iOS consumers must provide their own xcframework.** No automatic
  resolution. This is the next thing on the list to fix.
- **JVM cancellation isn't mid-token.** Cancelling a `generate()` `Flow`
  on JVM lets the current token finish; the underlying `de.kherud:llama`
  doesn't expose mid-token cancellation. Android and iOS implementations
  honour cancellation between tokens.
- **No KV-cache reuse across `ChatSession.send()` turns.** Each turn
  re-feeds the full prompt. Fine for short conversations, wasteful for
  long ones.

## Requirements

- JDK 21 (auto-provisioned via Gradle toolchain)
- For Android: Android SDK + NDK 27
- For iOS: macOS + Xcode + `llama.xcframework`
- To opt out of either target: `-Pkmp-ai.android=false` / `-Pkmp-ai.ios=false`

## License

Apache-2.0.

llama.cpp is MIT-licensed; respect upstream license terms when redistributing
GGUF models or native binaries.
