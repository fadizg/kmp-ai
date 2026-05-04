package io.github.fadizg.kmpai.llm

sealed interface ModelSource {
    val id: String

    data class HuggingFace(
        val repo: String,
        val file: String,
        val revision: String = "main",
    ) : ModelSource {
        override val id: String get() = "hf/$repo@$revision/$file"
        val url: String get() = "https://huggingface.co/$repo/resolve/$revision/$file"
    }

    data class Url(
        val url: String,
        val sha256: String? = null,
    ) : ModelSource {
        override val id: String get() = "url/$url"
    }

    data class LocalFile(val path: String) : ModelSource {
        override val id: String get() = "local/$path"
    }
}
