package io.github.fadizg.kmpai.sample.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun App(state: ChatState) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.prepare() }
    DisposableEffect(state) {
        onDispose { state.close() }
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        ChatScreen(state = state, onSend = { state.send(it, scope) })
    }
}
