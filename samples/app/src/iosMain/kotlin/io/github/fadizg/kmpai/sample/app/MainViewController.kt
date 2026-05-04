package io.github.fadizg.kmpai.sample.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import io.github.fadizg.kmpai.catalog.Qwen
import io.github.fadizg.kmpai.llm.IosModelCache
import io.github.fadizg.kmpai.llm.IosModelRepository
import platform.UIKit.UIViewController

@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val state = remember {
        ChatState(
            source = Qwen.Qwen2_5_0_5B_Q4,
            template = Qwen.template,
            systemPrompt = "You are a concise, helpful assistant. Reply in one short paragraph.",
            repository = IosModelRepository(IosModelCache.userCacheDirUrl()),
        )
    }
    App(state)
}
