package io.github.fadizg.kmpai.llm

import android.content.Context
import androidx.startup.Initializer

/**
 * Captures the Application [Context] at process start so [LlmEnvironment.default]
 * works without the consumer threading a Context through their DI graph.
 *
 * Wired via the AndroidX Startup `ContentProvider` declared in this library's
 * `AndroidManifest.xml`. Manifest merging injects it into the consumer app
 * automatically — no setup required.
 *
 * If a consumer disables AndroidX Startup (rare — e.g. `tools:node="remove"`
 * on the InitializationProvider), [LlmEnvironment.default] will throw with a
 * helpful message and the consumer can fall back to `LlmEnvironment(context)`.
 */
class KmpAiInitializer : Initializer<Context> {
    override fun create(context: Context): Context {
        appContext = context.applicationContext
        return context
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    internal companion object {
        @Volatile
        internal var appContext: Context? = null
    }
}
