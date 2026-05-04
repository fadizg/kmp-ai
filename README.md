# kmp-ai

A Kotlin Multiplatform library for running LLMs **offline** via a small,
explicit API. Both JVM and Android targets use **llama.cpp** under the hood,
so the same GGUF model file works everywhere:

- **JVM**: backed by [`de.kherud:llama`](https://github.com/kherud/java-llama.cpp) (prebuilt natives bundled).
- **Android**: builds llama.cpp from source via NDK + CMake (vendored as a git submodule), exposed through a JNI bridge.

iOS is on the roadmap.

## Modules

```
:llm           public KMP API + JVM implementation
:samples:jvm   runnable command-line sample
```

## Public API (commonMain)

```kotlin
interface LlmEngine : AutoCloseable {
    val info: ModelInfo
    fun generate(prompt: String, params: SamplingParams = SamplingParams()): Flow<Token>
    suspend fun complete(prompt: String, params: SamplingParams = SamplingParams()): String
    fun tokenize(text: String): IntArray
    fun embed(text: String): FloatArray
}

expect class LlmEngineFactory() {
    suspend fun load(modelPath: String, config: EngineConfig = EngineConfig()): LlmEngine
}

class ChatSession(engine: LlmEngine, template: ChatTemplate, systemPrompt: String?)

interface ModelRepository {
    fun resolve(source: ModelSource): Flow<DownloadProgress>
    suspend fun path(source: ModelSource): String
    suspend fun remove(source: ModelSource)
    fun list(): List<CachedModel>
}

sealed interface ModelSource {
    data class HuggingFace(val repo: String, val file: String, val revision: String = "main")
    data class Url(val url: String, val sha256: String? = null)
    data class LocalFile(val path: String)
}
```

## Running the sample

The sample downloads a small Qwen 2.5 0.5B GGUF (≈ 350 MB) from Hugging Face
into `~/.cache/kmp-ai/models/` on first run and then chats with it.

```bash
./gradlew :samples:jvm:run
```

Pass a prompt and optional flags after `--args`:

```bash
# default model, custom prompt
./gradlew :samples:jvm:run --args="Explain mmap in one sentence"

# use a local GGUF you already have
./gradlew :samples:jvm:run --args="--model=/path/to/model.gguf Hello"

# pull a different file from a HF repo
./gradlew :samples:jvm:run --args="--hf=Qwen/Qwen2.5-1.5B-Instruct-GGUF --file=qwen2.5-1.5b-instruct-q4_k_m.gguf"

# arbitrary URL with optional SHA-256 verification
./gradlew :samples:jvm:run --args="--url=https://example.com/model.gguf --sha256=<hex>"
```

## Why a model repository instead of shipping weights as Maven artifacts?

Model files are 300 MB – 5 GB. They don't belong inside Gradle artifacts:

- Maven Central rejects/discourages anything over a few hundred MB
- Many model licenses (Llama, Gemma, Mistral) restrict redistribution
- They bloat every CI run and developer cache
- They couple model versioning to library versioning — different cadences

Instead, `ModelRepository` resolves a `ModelSource` (Hugging Face, URL, or
local file), downloads it once with optional SHA-256 verification, and caches
it under the platform's user cache dir. Apps can also bundle the model file
through Android Play Asset Delivery or iOS On-Demand Resources and pass the
path to `LlmEngineFactory.load(...)`.

## Building the Android target

The Android target is **opt-in** so the JVM build keeps working in environments
without the Android SDK. Enable it by either setting an env var or a system
property:

```bash
export KMP_AI_ANDROID=true
# or pass each invocation:  ./gradlew -Dkmp-ai.android=true ...
```

When enabled, Gradle pulls in AGP from `maven.google.com` and the `:llm`
module gains an `androidTarget()`. You'll also need:

- Android SDK with `platforms;android-35`, `build-tools;35.0.0`, `cmake;3.22.1`
- Android NDK r26 or newer
- The `llama.cpp` git submodule checked out:
  ```bash
  git submodule update --init --recursive
  ```

Then:

```bash
KMP_AI_ANDROID=true ./gradlew :llm:assembleRelease
```

This produces an AAR with `libllama.so`, `libggml.so`, and `libkmpai_llama.so`
(our JNI bridge) packaged for `arm64-v8a` and `x86_64`. First build is slow
(~5 min) because llama.cpp is compiled from source per ABI; incremental
builds are fast.

The Android `actual` of `LlmEngineFactory.load(...)` takes the same parameters
as JVM. Get a model path from your `Context.cacheDir` (or wherever you stored
the GGUF — Play Asset Delivery, on-demand download, etc.) and pass it in.

## Roadmap

- [x] JVM target — `llama.cpp` via `de.kherud:llama`
- [x] Android target — `llama.cpp` via NDK + CMake + JNI bridge
- [ ] iOS target — cinterop to `llama.xcframework`
- [ ] Lift `DefaultModelRepository` into a shared `jvmAndAndroidMain` source set
      so it works on Android too (today it's JVM-only)
- [ ] Catalog modules (`llm-catalog-qwen`, `llm-catalog-gemma`) — small KB-sized
      artifacts that only contain `ModelSource` constants for curated checkpoints
- [ ] Streaming cancellation that aborts mid-token in the native loop
- [ ] LoRA adapters

## Requirements

- JDK 21 (the project uses a Gradle toolchain — auto-provisioned if missing)
- JVM target: Linux x64 / macOS arm64 / Windows x64 (native bits ship with `de.kherud:llama`)
- Android target: Android SDK + NDK + `KMP_AI_ANDROID=true` env var (see above)
