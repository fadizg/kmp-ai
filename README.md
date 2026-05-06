# kmp-ai

Kotlin Multiplatform library for running offline LLMs on JVM, Android, and iOS,
backed by [llama.cpp](https://github.com/ggml-org/llama.cpp).

- Coroutine `Flow<Token>` streaming
- Built-in HuggingFace / URL / local-file model resolution with SHA-256 verification
- Chat templates: ChatML, Llama 3, Gemma, Custom
- Curated model catalogs (Qwen 2.5, Gemma 2/3)
- KMP Compose Multiplatform sample app (Desktop + Android + iOS)
- **Maven Central** distribution — Android AARs ship prebuilt `.so` files; consumers don't need NDK locally.

## Compatibility matrix

| Tool                     | Required version       |
| ------------------------ | ---------------------- |
| Kotlin                   | 2.2.0 +                |
| Gradle                   | 8.7 +                  |
| AGP (Android)            | 8.7 +                  |
| Compose Multiplatform    | 1.9.0 +                |
| JDK                      | 21 (toolchain)         |
| Android SDK / NDK        | API 26 + (NDK only needed if you build kmp-ai itself, not for consuming the AAR) |
| Xcode (iOS)              | 16 +                   |
| iOS deployment target    | 14.0 +                 |

## Status

| Target          | Status                                                                                   |
| --------------- | ---------------------------------------------------------------------------------------- |
| **JVM (Desktop)**   | ✅ Stable. `de.kherud:llama` ships natives for Mac/Linux/Win.                          |
| **Android**         | ✅ Stable. Maven Central AAR includes prebuilt `libllama.so`/`libggml*.so`/`libkmpai_llama.so` for `arm64-v8a` + `x86_64`. Consumers don't need NDK. |
| **iOS**             | ⚠️ Engine pending rewrite for Kotlin 2.2.x cinterop bindings. The Maven Central iOS klib compiles but `LlmEnvironment.default().load(...)` throws at runtime. **For iOS today: use composite build** (`includeBuild("../kmp-ai")`), which still works against the older bindings. |

## Installation

The recommended path is **Maven Central** (live as of v0.2.x).

In the **consumer** project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

In the consumer module:

```kotlin
// Plain JVM / Android Gradle module:
dependencies {
    implementation("io.github.fadizg.kmpai:llm:0.2.8")
    implementation("io.github.fadizg.kmpai:llm-catalog-qwen:0.2.8")
}

// KMP module:
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("io.github.fadizg.kmpai:llm:0.2.8")
        implementation("io.github.fadizg.kmpai:llm-catalog-qwen:0.2.8")
    }
}
```

> Maven Central uses the real `io.github.fadizg.kmpai` groupId. Don't confuse
> with the older JitPack-rewritten `com.github.fadizg.kmp-ai:*` coordinates;
> JitPack publishing was retired with the move to Maven Central.

Other distribution options (composite build, mavenLocal, GitHub Packages)
are documented under [Distribution](#distribution).

### Swift Package Manager (iOS, no Gradle)

For iOS-only Swift consumers (no Kotlin/Gradle in the consumer project),
kmp-ai also publishes a Swift Package per release. Add it to your
`Package.swift`:

```swift
.package(url: "https://github.com/fadizg/kmp-ai", exact: "0.3.0")

// then in your target:
.product(name: "KmpAI", package: "kmp-ai")
```

Or in Xcode: File → Add Package Dependencies → paste the repo URL.

The package ships two binary `xcframework`s — `KmpAI.xcframework` (the
Swift bindings) and `llama.xcframework` (the underlying llama.cpp
runtime). Both are wired through a single `KmpAI` product, so consumers
declare exactly one dependency. Xcode embeds & signs both automatically.

```swift
import KmpAI

let env = LlmEnvironmentCompanion.shared.default()
env.load(source: ModelSourceLocalFile(path: "/path/to/model.gguf"),
         config: EngineConfigCompanion.shared.default_()) { engine, err in
    engine?.complete(prompt: "hello",
                     params: SamplingParamsCompanion.shared.default_()) { reply, err in
        print(reply ?? "")
    }
}
```

## Drop-in integration (KMP project, ≤ 10 lines)

For a project that already has Koin (or any DI) wired through a shared module:

```toml
# gradle/libs.versions.toml
[versions]
kmp-ai = "0.2.8"

[libraries]
kmp-ai-llm           = { module = "io.github.fadizg.kmpai:llm",                version.ref = "kmp-ai" }
kmp-ai-catalog-qwen  = { module = "io.github.fadizg.kmpai:llm-catalog-qwen",   version.ref = "kmp-ai" }
```

```kotlin
// shared/build.gradle.kts
sourceSets.commonMain.dependencies {
    implementation(libs.kmp.ai.llm)
    implementation(libs.kmp.ai.catalog.qwen)
}
```

```kotlin
// shared/src/commonMain/.../KoinModule.kt
val llmModule = module {
    single { LlmEnvironment.default() }
}
```

Done. `commonMain` calls `get<LlmEnvironment>().load(Qwen.Qwen2_5_0_5B_Q4)` and
gets a real engine on JVM and Android, no per-platform DI bindings, no `Context`
plumbing. (iOS via composite build until the engine rewrite ships.)

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

That's the whole API surface for the common case. The same five lines run on
JVM and Android off the shelf; iOS works the same shape via composite build.

If you want progress callbacks during the download, use the underlying
repository directly:

```kotlin
env.repository.resolve(source).collect { progress ->
    when (progress) {
        is DownloadProgress.Running -> println("${progress.bytes / 1_000_000} MB")
        is DownloadProgress.Done    -> println("ready")
        is DownloadProgress.Failed  -> throw progress.error
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
the call is genuinely zero-arg.

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
    auth = HuggingFaceAuth.Token("hf_...")  // optional, for gated repos
)
ModelSource.Url("https://example.com/model.gguf", sha256 = "...")
ModelSource.LocalFile("/abs/path/to/model.gguf")
```

`ModelRepository` resolves a `ModelSource` to a local file, downloading and caching
as needed. Two implementations:

- `DefaultModelRepository(File)` — JVM and Android (uses `HttpURLConnection`)
- `IosModelRepository(NSURL)` — iOS (currently a stub; see Status)

Both expose:

```kotlin
fun resolve(source: ModelSource): Flow<DownloadProgress>
suspend fun path(source: ModelSource): String
suspend fun remove(source: ModelSource)
suspend fun list(): List<CachedModel>
```

`DownloadProgress` is a sealed type — `Running(bytes, total)`, `Done(path)`, `Failed(error)`.

### Capability check (experimental)

Before constructing an engine, you can check whether the device can actually
run kmp-ai:

```kotlin
@OptIn(ExperimentalKmpAiApi::class)
when (val cap = LlmEnvironment.capability()) {
    is DeviceCapability.Available    -> showAiFeature(cap.backends)
    is DeviceCapability.Unsupported  -> hideAiFeature(cap.reason)
}
```

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

### `EngineConfig` and `SamplingParams` presets

```kotlin
EngineConfig.lowMemory()      // contextSize=512  · for low-RAM Androids
EngineConfig.balanced()       // contextSize=2048 · default for most devices
EngineConfig.highCapacity()   // contextSize=4096 · flagships / desktops

SamplingParams.Conservative   // temp=0.2 · factual / Q&A / code
SamplingParams.Balanced       // temp=0.7 · default chat
SamplingParams.Creative       // temp=0.9 · brainstorming / writing
```

### `DownloadConstraints` (experimental)

Gate large model downloads on device conditions:

```kotlin
@OptIn(ExperimentalKmpAiApi::class)
env.repository.resolve(
    Qwen.Qwen2_5_3B_Q4,
    constraints = DownloadConstraints.Recommended,  // wifiOnly + requiresCharging
).collect { ... }
```

Android uses `ConnectivityManager` + `BatteryManager`; iOS gates charging via
`UIDevice` (wifiOnly is no-op on iOS — set
`NSURLSessionConfiguration.allowsCellularAccess = false` if you need a hard
guarantee). JVM is no-op.

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

# Android (needs ANDROID_HOME + NDK 27 to build kmp-ai's native code)
./gradlew :samples:app:installDebug

# iOS framework (composite-build path; needs llama.xcframework)
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

### Android prerequisites (only needed to build kmp-ai itself)

- Android SDK (`ANDROID_HOME` set, or `local.properties` with `sdk.dir`)
- NDK 27.0.12077973 (the CMake config auto-installs it)
- ~3 GB free for the llama.cpp build artifacts

```bash
./gradlew :llm:assembleRelease
./gradlew :samples:app:installDebug
```

First Android build is slow (~5 min) because llama.cpp is compiled from source
per ABI; incremental builds are fast. **Consumers of the published Maven
Central AAR don't need any of this** — the prebuilt `.so` files are inside the
artifact.

### iOS prerequisites

- Xcode 16 + macOS host
- A `llama.xcframework` — auto-downloaded to `$rootDir/.cache/llama.xcframework`
  on first iOS build (~50 MB from llama.cpp's GitHub release). Override with
  `-Pkmp-ai.iosFramework=/path/to/your/llama.xcframework` if you want to use
  a custom build.
- Pin `kmp-ai.llamaCppBuild=bXXXX` in `gradle.properties` to a specific
  llama.cpp release tag (default: `b9033`).

```bash
./gradlew :samples:app:linkDebugFrameworkIosArm64
# First run: downloads + unzips the xcframework, then links.
# Subsequent runs reuse the cached framework.
```

To produce your own xcframework from source instead of using the released
one:

```bash
git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
cmake --workflow ios-arm64-release
cp -R build-ios/llama.xcframework /path/to/kmp-ai/.cache/llama.xcframework
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

This library can be consumed four ways. Pick one based on your needs.

### 1. Maven Central (recommended)

Documented above. Released from a macOS GitHub Actions runner via
`vanniktech/gradle-maven-publish-plugin`, signed with GPG, indexed by
Sonatype. No PAT or credentials needed by the consumer.

```kotlin
implementation("io.github.fadizg.kmpai:llm:0.2.8")
```

iOS klib ships in the published artifact but currently throws at runtime
(see Status). Use composite build for iOS today.

### 2. Composite build (no publish needed)

Best for actively developing kmp-ai alongside a consumer project, or for
iOS until the engine rewrite ships. In the consumer's `settings.gradle.kts`:

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
./gradlew publishToMavenLocal -Pkmp-ai.version=0.2.8

# JVM-only publish on a machine without those toolchains:
./gradlew publishToMavenLocal -Pkmp-ai.version=0.2.8 -Pkmp-ai.android=false -Pkmp-ai.ios=false
```

```kotlin
// consumer settings.gradle.kts
dependencyResolutionManagement {
    repositories { mavenLocal(); mavenCentral(); google() }
}

// consumer build.gradle.kts
dependencies { implementation("io.github.fadizg.kmpai:llm:0.2.8") }
```

### 4. GitHub Packages (legacy — Maven Central is preferred)

Requires a classic PAT with `read:packages` + `write:packages` (and `repo`):

```bash
GITHUB_USER=fadizg GITHUB_TOKEN=ghp_xxx ./gradlew publish -Pkmp-ai.version=0.2.8
```

Consumer needs the same PAT to read — GitHub Packages requires auth even for
public packages.

## Project layout

```
kmp-ai/
├── llm/                          KMP library: engine, repository, chat
│   ├── src/commonMain/           public API (interfaces, data classes)
│   ├── src/jvmAndAndroidMain/    DefaultModelRepository (HttpURLConnection)
│   ├── src/jvmMain/              LlamaCppEngine (de.kherud:llama)
│   ├── src/androidMain/          AndroidLlamaCppEngine + JNI bridge + cpp/
│   └── src/iosMain/              IosLlamaCppEngine (stub) + cinterop bindings
├── llm-catalog-qwen/             curated Qwen ModelSource constants
├── llm-catalog-gemma/            curated Gemma ModelSource constants
├── samples/
│   ├── jvm/                      headless CLI
│   └── app/                      Compose MP chat UI (Desktop + Android + iOS)
├── scripts/
│   └── setup-release.sh          one-shot GPG + GitHub Secrets bootstrap
├── .github/workflows/
│   ├── ci.yml                    JVM / Android (.so verify) / iOS smoke tests
│   └── release.yml               tag → Maven Central + GitHub Release asset
└── jitpack.yml                   legacy JitPack config (Maven Central preferred)
```

## Caveats

- **iOS engine is stubbed.** The Maven Central iOS klib compiles and the API
  surface matches JVM/Android, but the actual engine throws at runtime. The
  cinterop bindings need a rewrite to match Kotlin 2.2.x's Objective-C-mode
  output for `modules = llama` framework imports. Use composite build until
  this lands. Android and JVM are unaffected.
- **Variants are publish-time gated.** A Maven artifact published with
  `kmp-ai.android=false` has no Android variant — Android consumers can't
  resolve it. Maven Central releases come from GitHub Actions on `macos-latest`
  with all targets enabled, so the published `0.2.x` artifacts always have
  full coverage.
- **JVM cancellation isn't mid-token.** Cancelling a `generate()` `Flow`
  on JVM lets the current token finish; the underlying `de.kherud:llama`
  doesn't expose mid-token cancellation. Android cancels between tokens.
- **No KV-cache reuse across `ChatSession.send()` turns.** Each turn
  re-feeds the full prompt. Fine for short conversations, wasteful for
  long ones. On the roadmap.

## Requirements (consumer)

- JDK 21 (auto-provisioned via Gradle toolchain)
- For Android consumer: nothing extra — AAR ships prebuilt `.so` files
- For iOS consumer: composite build setup (see Status), `llama.xcframework`
  on disk

## License

Apache-2.0.

llama.cpp is MIT-licensed; respect upstream license terms when redistributing
GGUF models or native binaries.
