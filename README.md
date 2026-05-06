# kmp-ai

Kotlin Multiplatform library for running offline LLMs on JVM, Android, and iOS,
backed by [llama.cpp](https://github.com/ggml-org/llama.cpp).

- Coroutine `Flow<Token>` streaming
- Built-in HuggingFace / URL / local-file model resolution with SHA-256 verification
- Resumable downloads — survives flaky networks / app restarts
- Chat templates: ChatML, Llama 3, Gemma, Custom
- KV-cache reuse across `ChatSession` turns — follow-ups only decode the new message
- Constrained generation via GBNF grammars (`Grammar.choice`, `Grammar.json`, `Grammar.regex`, …)
- Curated model catalogs (Qwen 2.5, Gemma 2/3, DeepSeek-R1-Distill)
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
| **iOS**             | ✅ Stable since v0.3.0. Engine + repository are live; SPM distribution ships `KmpAI.xcframework` + `llama.xcframework` for Swift consumers. Background-download resume via `cancelByProducingResumeData` since v0.4.0. |

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
gets a real engine on JVM, Android, and iOS — no per-platform DI bindings, no
`Context` plumbing.

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
    fun countTokens(text: String): Int           // cheap path; default delegates to tokenize().size
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

- `DefaultModelRepository(File)` — JVM and Android (uses `HttpURLConnection`, supports `Range:` resume)
- `IosModelRepository(NSURL)` — iOS (uses `NSURLSession`, supports `cancelByProducingResumeData`)

Both expose:

```kotlin
fun resolve(source: ModelSource): Flow<DownloadProgress>
suspend fun path(source: ModelSource): String
suspend fun remove(source: ModelSource)
suspend fun pathIfCached(source: ModelSource): String?     // since 0.3.2
suspend fun isCached(source: ModelSource): Boolean         // since 0.3.2
suspend fun list(): List<CachedModel>
```

`isCached` / `pathIfCached` check the on-disk path that `resolve` would
write to — use these instead of `list().any { it.id == source.id }`, which
won't match because `CachedModel.id` is derived from the on-disk layout
and not guaranteed to equal `ModelSource.id`.

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
chat.reset()    // clear, keep system prompt; also drops the engine's KV cache
```

Follow-up turns reuse the engine's KV cache: `ChatSession` finds the longest
common prefix between the new prompt and what's already decoded, then only
decodes the diff. On a 4 k-context chat this typically cuts turn-2 latency
from "full prompt" down to "just the new user message". Works on Android &
iOS natively; on JVM via `de.kherud:llama`'s `setCachePrompt(true)`.

Built-in templates: `ChatML` (Qwen, many open models), `Llama3`, `Gemma`. For
anything else, use `ChatTemplate.Custom(formatter, stops)`.

### Constrained generation (`Grammar`)

Force the model to emit text that matches a [GBNF grammar][gbnf]. Useful for
agentic tool calls, structured output, multiple-choice routing.

```kotlin
// Most-specific wins: per-call > ChatSession.defaults > LlmEnvironment.defaultSampling
val env = LlmEnvironment.default()
val chat = env.chat(Qwen.Qwen2_5_0_5B_Q4, template = Qwen.template)

// Bounded multiple choice:
chat.choose("Is Kotlin Multiplatform mobile-first?", "yes", "no")
// → "yes"

// Free JSON:
chat.askJson("Give me a person record with name and age.")
// → {"name":"Ada","age":36}

// Grammar attached to per-call SamplingParams:
val schema = Grammar.jsonObject("city", "country")
chat.send("Where was Ada Lovelace born?", SamplingParams(grammar = schema))
    .collect { print(it.text) }
// → {"city":"London","country":"United Kingdom"}

// Hand-written GBNF for full control:
val years = Grammar.raw("""
    root ::= year (", " year)*
    year ::= [0-9] [0-9] [0-9] [0-9]
""")
```

Grammar layering ([demo](#layered-defaults)): set `defaultSampling` on the
environment if every call should produce JSON, then pass per-call grammars
to override on specific endpoints. The most-specific grammar always wins;
unset levels fall through.

[gbnf]: https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md

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

`:llm-catalog-qwen`, `:llm-catalog-gemma`, and `:llm-catalog-deepseek` are
tiny modules holding curated `ModelSource` constants and matched
`ChatTemplate`s:

```kotlin
import io.github.fadizg.kmpai.catalog.Qwen
import io.github.fadizg.kmpai.catalog.Gemma
import io.github.fadizg.kmpai.catalog.DeepSeek

Qwen.Qwen2_5_0_5B_Q4   // ~350 MB, smallest practical Qwen
Qwen.Qwen2_5_1_5B_Q4   // ~1.0 GB
Qwen.Qwen2_5_3B_Q4     // ~1.9 GB
Qwen.Qwen2_5_7B_Q4     // ~4.7 GB
Qwen.Qwen2_5_Coder_1_5B_Q4
Qwen.template          // ChatTemplate.ChatML

Gemma.Gemma2_2B_Q4     // ~1.6 GB
Gemma.Gemma3_1B_Q4     // ~750 MB
Gemma.template         // ChatTemplate.Gemma

DeepSeek.R1_Distill_Qwen_1_5B_Q4    // ~1.0 GB, reasoning model — emits <think>…</think> blocks
DeepSeek.R1_Distill_Qwen_7B_Q4      // ~4.7 GB
DeepSeek.R1_Distill_Llama_8B_Q4     // ~4.9 GB
DeepSeek.template                    // ChatTemplate.ChatML
```

### Layered defaults

Set a baseline `SamplingParams` (most usefully a `Grammar`) once on the
environment; every chat session inherits it; per-call params still win:

```kotlin
val env = LlmEnvironment.default()
    .withDefaults(SamplingParams(grammar = Grammar.json()))   // global default
val chat = env.chat(Qwen.Qwen2_5_0_5B_Q4, template = Qwen.template)

chat.send("Tell me about Ada Lovelace")   // emits valid JSON (env default)
chat.send(
    "Yes or no — was she a programmer?",
    SamplingParams(grammar = Grammar.choice("yes", "no")),  // per-call override
)
```

### Resumable downloads

Multi-GB GGUF downloads survive transient failures. JVM/Android send
`Range: bytes=N-` when a `.part` file exists; iOS uses NSURLSession
`cancelByProducingResumeData` and replays the resumeData blob on the next
attempt. SHA-256 verification still spans the full file.

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

# iOS — open the bundled Xcode shell, hit Run on a simulator
cd samples/app/iosApp
./setup.sh                         # generates iosApp.xcodeproj via xcodegen
open iosApp.xcodeproj
# Build phase auto-runs ./gradlew :samples:app:embedAndSignAppleFrameworkForXcode
# and embeds llama.xcframework. See samples/app/iosApp/README.md.
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

iOS consumers can either pull the Maven Central klib (Kotlin/KMP project) or
the Swift Package (Swift-only project) — see [Swift Package Manager](#swift-package-manager-ios-no-gradle).

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
│   └── src/iosMain/              IosLlamaCppEngine + cinterop bindings + IosModelRepository
├── llm-catalog-qwen/             curated Qwen ModelSource constants
├── llm-catalog-gemma/            curated Gemma ModelSource constants
├── llm-catalog-deepseek/         curated DeepSeek-R1-Distill ModelSource constants
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

- **Variants are publish-time gated.** A Maven artifact published with
  `kmp-ai.android=false` has no Android variant — Android consumers can't
  resolve it. Maven Central releases come from GitHub Actions on `macos-latest`
  with all targets enabled, so the published `0.x` artifacts always have
  full coverage.
- **JVM cancellation isn't mid-token.** Cancelling a `generate()` `Flow`
  on JVM lets the current token finish; the underlying `de.kherud:llama`
  doesn't expose mid-token cancellation. Android and iOS cancel between tokens.
- **Grammar truncation.** A grammar enforced via `SamplingParams.grammar`
  is checked at every step; if `maxTokens` runs out before the grammar
  reaches an accepting state, output is truncated. Size `maxTokens`
  generously when emitting structured output.
- **KV-cache reuse falls back to a full re-decode** when the system prompt
  changes mid-session, the prefix divergence is far enough back that
  reuse isn't worthwhile, or `ChatSession.reset()` is called.

## Requirements (consumer)

- JDK 21 (auto-provisioned via Gradle toolchain)
- For Android consumer: nothing extra — AAR ships prebuilt `.so` files
- For iOS consumer (Kotlin/KMP): nothing extra beyond Maven Central
- For iOS consumer (Swift-only): SPM auto-fetches the binary xcframeworks

## License

Apache-2.0.

llama.cpp is MIT-licensed; respect upstream license terms when redistributing
GGUF models or native binaries.
