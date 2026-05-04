package io.github.fadizg.kmpai.sample.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.fadizg.kmpai.catalog.Qwen
import io.github.fadizg.kmpai.llm.LlmEnvironment

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(420.dp, 720.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "kmp-ai sample",
        state = windowState,
    ) {
        val state = remember {
            ChatState(
                env = LlmEnvironment(),
                source = Qwen.Qwen2_5_0_5B_Q4,
                template = Qwen.template,
                systemPrompt = "You are a concise, helpful assistant. Reply in one short paragraph.",
            )
        }
        App(state)
    }
}
