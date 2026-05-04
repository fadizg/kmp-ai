package io.github.fadizg.kmpai.llm

import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

internal class LlamaCppEngine private constructor(
    private val model: LlamaModel,
    override val info: ModelInfo,
) : LlmEngine {

    override fun generate(prompt: String, params: SamplingParams): Flow<Token> = flow {
        val ip = params.toInferenceParameters(prompt)
        try {
            for (out in model.generate(ip)) {
                emit(Token(text = out.text))
            }
        } catch (t: Throwable) {
            throw GenerationException("generation failed: ${t.message}", t)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(prompt: String, params: SamplingParams): String =
        withContext(Dispatchers.IO) {
            try {
                model.complete(params.toInferenceParameters(prompt))
            } catch (t: Throwable) {
                throw GenerationException("completion failed: ${t.message}", t)
            }
        }

    override fun tokenize(text: String): IntArray = model.encode(text)

    override fun embed(text: String): FloatArray = model.embed(text)

    override fun close() = model.close()

    private fun SamplingParams.toInferenceParameters(prompt: String): InferenceParameters {
        val ip = InferenceParameters(prompt)
            .setTemperature(temperature)
            .setTopK(topK)
            .setTopP(topP)
            .setRepeatPenalty(repeatPenalty)
            .setNPredict(maxTokens)
        if (stop.isNotEmpty()) ip.setStopStrings(*stop.toTypedArray())
        seed?.let { ip.setSeed(it) }
        return ip
    }

    companion object {
        suspend fun load(modelPath: String, config: EngineConfig): LlmEngine =
            withContext(Dispatchers.IO) {
                val file = File(modelPath)
                if (!file.exists()) {
                    throw ModelLoadException("model file not found: $modelPath")
                }
                val params = ModelParameters()
                    .setModel(modelPath)
                    .setCtxSize(config.contextSize)
                    .setGpuLayers(config.gpuLayers)
                config.threads?.let { params.setThreads(it) }
                val model = try {
                    LlamaModel(params)
                } catch (t: Throwable) {
                    throw ModelLoadException("failed to load model: ${t.message}", t)
                }
                LlamaCppEngine(
                    model = model,
                    info = ModelInfo(
                        name = file.nameWithoutExtension,
                        contextSize = config.contextSize,
                        backend = if (config.gpuLayers != 0) Backend.METAL else Backend.CPU,
                    ),
                )
            }
    }
}
