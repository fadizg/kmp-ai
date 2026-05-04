package io.github.fadizg.kmpai.sample.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fadizg.kmpai.catalog.Qwen
import io.github.fadizg.kmpai.llm.LlmEnvironment

class MainActivity : ComponentActivity() {

    private val state by lazy {
        ChatState(
            env = LlmEnvironment(applicationContext),
            source = Qwen.Qwen2_5_0_5B_Q4,
            template = Qwen.template,
            systemPrompt = "You are a concise, helpful assistant. Reply in one short paragraph.",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(state) }
    }
}
