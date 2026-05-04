package io.github.fadizg.kmpai.sample.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fadizg.kmpai.catalog.Qwen
import io.github.fadizg.kmpai.llm.AndroidModelCache
import io.github.fadizg.kmpai.llm.DefaultModelRepository

class MainActivity : ComponentActivity() {

    private val state by lazy {
        ChatState(
            source = Qwen.Qwen2_5_0_5B_Q4,
            template = Qwen.template,
            systemPrompt = "You are a concise, helpful assistant. Reply in one short paragraph.",
            repository = DefaultModelRepository(AndroidModelCache.forContext(this)),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(state) }
    }
}
