#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "kmpai-llama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct EngineHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    int n_ctx = 0;
    int n_threads = 0;

    // KV-cache reuse state. Tokens for the prefix currently in the KV cache.
    // Next generate() compares against the new prompt and only decodes the
    // diff suffix.
    std::vector<llama_token> cached_tokens;
    std::mutex generate_mutex;
};

std::atomic<bool> g_backend_initialized{false};

void ensure_backend() {
    bool expected = false;
    if (g_backend_initialized.compare_exchange_strong(expected, true)) {
        llama_backend_init();
    }
}

std::string j2s(JNIEnv *env, jstring js) {
    if (js == nullptr) return {};
    const char *raw = env->GetStringUTFChars(js, nullptr);
    std::string out(raw);
    env->ReleaseStringUTFChars(js, raw);
    return out;
}

void throw_runtime(JNIEnv *env, const std::string &msg) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(cls, msg.c_str());
}

std::vector<llama_token> tokenize_string(
    const llama_model *model,
    const std::string &text,
    bool add_special) {
    const llama_vocab *vocab = llama_model_get_vocab(model);
    int n = -llama_tokenize(vocab, text.c_str(), text.size(), nullptr, 0, add_special, true);
    std::vector<llama_token> tokens(n);
    int written = llama_tokenize(
        vocab, text.c_str(), text.size(),
        tokens.data(), tokens.size(), add_special, true);
    if (written < 0) {
        return {};
    }
    tokens.resize(written);
    return tokens;
}

std::string token_to_piece(const llama_model *model, llama_token token) {
    const llama_vocab *vocab = llama_model_get_vocab(model);
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (n < 0) return {};
    return std::string(buf, n);
}

bool ends_with_any(const std::string &s, const std::vector<std::string> &stops) {
    for (const auto &stop : stops) {
        if (stop.empty()) continue;
        if (s.size() < stop.size()) continue;
        if (s.compare(s.size() - stop.size(), stop.size(), stop) == 0) return true;
    }
    return false;
}

llama_sampler *build_sampler(
    const llama_model *model,
    int top_k,
    float top_p,
    float temperature,
    float repeat_penalty,
    int seed,
    const std::string *grammar) {
    auto *chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    // Grammar mask first so it can veto invalid tokens before any
    // probability shaping touches them.
    if (grammar != nullptr && !grammar->empty()) {
        const llama_vocab *vocab = llama_model_get_vocab(model);
        auto *gsmpl = llama_sampler_init_grammar(vocab, grammar->c_str(), "root");
        if (gsmpl != nullptr) {
            llama_sampler_chain_add(chain, gsmpl);
        } else {
            LOGE("llama_sampler_init_grammar returned null; ignoring grammar");
        }
    }
    llama_sampler_chain_add(chain, llama_sampler_init_penalties(64, repeat_penalty, 0.0f, 0.0f));
    if (top_k > 0) llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
    if (top_p < 1.0f) llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(seed >= 0 ? (uint32_t) seed : LLAMA_DEFAULT_SEED));
    return chain;
}

// Returns the length of the longest common prefix between a and b.
size_t common_prefix_len(
    const std::vector<llama_token> &a,
    const std::vector<llama_token> &b) {
    size_t n = std::min(a.size(), b.size());
    for (size_t i = 0; i < n; ++i) {
        if (a[i] != b[i]) return i;
    }
    return n;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_fadizg_kmpai_llm_LlamaNative_nativeLoadModel(
    JNIEnv *env, jclass /* clazz */,
    jstring jModelPath, jint ctxSize, jint gpuLayers, jint threads) {
    ensure_backend();

    std::string model_path = j2s(env, jModelPath);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = gpuLayers;

    llama_model *model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (model == nullptr) {
        throw_runtime(env, "failed to load model: " + model_path);
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = ctxSize;
    ctx_params.n_batch = 512;
    if (threads > 0) {
        ctx_params.n_threads = threads;
        ctx_params.n_threads_batch = threads;
    }

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        throw_runtime(env, "failed to create llama context");
        return 0;
    }

    auto *handle = new EngineHandle{model, ctx, ctxSize, threads};
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_io_github_fadizg_kmpai_llm_LlamaNative_nativeFreeModel(
    JNIEnv * /* env */, jclass /* clazz */, jlong jHandle) {
    auto *handle = reinterpret_cast<EngineHandle *>(jHandle);
    if (handle == nullptr) return;
    if (handle->ctx) llama_free(handle->ctx);
    if (handle->model) llama_model_free(handle->model);
    delete handle;
}

JNIEXPORT void JNICALL
Java_io_github_fadizg_kmpai_llm_LlamaNative_nativeGenerate(
    JNIEnv *env, jclass /* clazz */,
    jlong jHandle, jstring jPrompt,
    jint maxTokens, jfloat temperature, jint topK, jfloat topP,
    jfloat repeatPenalty, jint seed,
    jobjectArray jStops, jstring jGrammar, jboolean reuseCache,
    jobject jCallback) {
    auto *handle = reinterpret_cast<EngineHandle *>(jHandle);
    if (handle == nullptr) {
        throw_runtime(env, "null engine handle");
        return;
    }

    // Serialize generate calls; KV cache state isn't safe to share across
    // concurrent decodes on the same context.
    std::lock_guard<std::mutex> lock(handle->generate_mutex);

    std::vector<std::string> stops;
    if (jStops != nullptr) {
        jsize n = env->GetArrayLength(jStops);
        stops.reserve(n);
        for (jsize i = 0; i < n; ++i) {
            auto js = (jstring) env->GetObjectArrayElement(jStops, i);
            stops.push_back(j2s(env, js));
            env->DeleteLocalRef(js);
        }
    }

    std::string grammar_str;
    bool has_grammar = false;
    if (jGrammar != nullptr) {
        grammar_str = j2s(env, jGrammar);
        has_grammar = !grammar_str.empty();
    }

    jclass callbackClass = env->GetObjectClass(jCallback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
    if (onToken == nullptr) {
        throw_runtime(env, "callback class missing onToken(String): Boolean");
        return;
    }

    std::string prompt = j2s(env, jPrompt);
    auto tokens = tokenize_string(handle->model, prompt, true);
    if (tokens.empty()) {
        throw_runtime(env, "tokenization produced no tokens");
        return;
    }

    // Decide where in the prompt to start decoding. With reuse, we keep the
    // KV state for the longest common prefix between the cached tokens and
    // the new prompt, then only decode the diff suffix. Without reuse, we
    // wipe the cache and decode the whole prompt.
    size_t reuse_n = 0;
    if (reuseCache && !handle->cached_tokens.empty()) {
        reuse_n = common_prefix_len(handle->cached_tokens, tokens);
        // llama.cpp won't sample correctly if there's nothing left to
        // decode (the last logits would be from a previous run). Always
        // leave at least one token for the prefill.
        if (reuse_n == tokens.size()) reuse_n = tokens.size() - 1;
    }

    if (reuse_n == 0) {
        llama_memory_clear(llama_get_memory(handle->ctx), true);
    } else if (reuse_n < handle->cached_tokens.size()) {
        // Trim KV cache back to the common prefix.
        llama_memory_seq_rm(
            llama_get_memory(handle->ctx),
            /* seq_id */ 0, /* p0 */ (llama_pos) reuse_n, /* p1 */ -1);
    }

    int prefill_count = (int) (tokens.size() - reuse_n);
    llama_batch batch = llama_batch_get_one(tokens.data() + reuse_n, prefill_count);
    if (llama_decode(handle->ctx, batch) != 0) {
        // KV state is now in an undefined position — drop it so the next call rebuilds.
        llama_memory_clear(llama_get_memory(handle->ctx), true);
        handle->cached_tokens.clear();
        throw_runtime(env, "llama_decode failed for prompt");
        return;
    }

    llama_sampler *sampler = build_sampler(
        handle->model, topK, topP, temperature, repeatPenalty, seed,
        has_grammar ? &grammar_str : nullptr);

    std::string accumulated;
    accumulated.reserve(maxTokens * 4);
    const llama_vocab *vocab = llama_model_get_vocab(handle->model);

    // Track every token now in the KV cache so the next call can find a prefix.
    std::vector<llama_token> live_tokens = tokens;
    live_tokens.reserve(tokens.size() + maxTokens);

    for (int i = 0; i < maxTokens; ++i) {
        llama_token id = llama_sampler_sample(sampler, handle->ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) {
            live_tokens.push_back(id);
            break;
        }

        std::string piece = token_to_piece(handle->model, id);
        accumulated.append(piece);
        live_tokens.push_back(id);

        jstring jPiece = env->NewStringUTF(piece.c_str());
        jboolean keep = env->CallBooleanMethod(jCallback, onToken, jPiece);
        env->DeleteLocalRef(jPiece);
        if (env->ExceptionCheck()) break;
        if (!keep) break;

        if (ends_with_any(accumulated, stops)) break;

        llama_batch next = llama_batch_get_one(&id, 1);
        if (llama_decode(handle->ctx, next) != 0) break;
    }

    handle->cached_tokens = std::move(live_tokens);

    llama_sampler_free(sampler);
}

JNIEXPORT jintArray JNICALL
Java_io_github_fadizg_kmpai_llm_LlamaNative_nativeTokenize(
    JNIEnv *env, jclass /* clazz */, jlong jHandle, jstring jText) {
    auto *handle = reinterpret_cast<EngineHandle *>(jHandle);
    if (handle == nullptr) return nullptr;
    std::string text = j2s(env, jText);
    auto tokens = tokenize_string(handle->model, text, false);
    jintArray out = env->NewIntArray((jsize) tokens.size());
    if (!tokens.empty()) {
        env->SetIntArrayRegion(
            out, 0, (jsize) tokens.size(),
            reinterpret_cast<const jint *>(tokens.data()));
    }
    return out;
}

JNIEXPORT jint JNICALL
Java_io_github_fadizg_kmpai_llm_LlamaNative_nativeCountTokens(
    JNIEnv *env, jclass /* clazz */, jlong jHandle, jstring jText) {
    auto *handle = reinterpret_cast<EngineHandle *>(jHandle);
    if (handle == nullptr) return 0;
    std::string text = j2s(env, jText);
    const llama_vocab *vocab = llama_model_get_vocab(handle->model);
    // Pass nullptr buffer to get the negated required size — avoids
    // allocating the full token array just to count.
    int n = -llama_tokenize(vocab, text.c_str(), text.size(), nullptr, 0, false, true);
    return n < 0 ? 0 : n;
}

JNIEXPORT jfloatArray JNICALL
Java_io_github_fadizg_kmpai_llm_LlamaNative_nativeEmbed(
    JNIEnv *env, jclass /* clazz */, jlong jHandle, jstring jText) {
    auto *handle = reinterpret_cast<EngineHandle *>(jHandle);
    if (handle == nullptr) return nullptr;
    std::string text = j2s(env, jText);

    auto tokens = tokenize_string(handle->model, text, true);
    if (tokens.empty()) return env->NewFloatArray(0);

    // embed() invalidates the KV cache; drop our prefix tracking.
    llama_memory_clear(llama_get_memory(handle->ctx), true);
    handle->cached_tokens.clear();

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(handle->ctx, batch) != 0) {
        throw_runtime(env, "llama_decode failed during embed");
        return nullptr;
    }

    int n_embd = llama_model_n_embd(handle->model);
    const float *src = llama_get_embeddings(handle->ctx);
    if (src == nullptr) {
        src = llama_get_embeddings_seq(handle->ctx, 0);
    }
    jfloatArray out = env->NewFloatArray(n_embd);
    if (src != nullptr) {
        env->SetFloatArrayRegion(out, 0, n_embd, src);
    }
    return out;
}

JNIEXPORT jint JNICALL
Java_io_github_fadizg_kmpai_llm_LlamaNative_nativeContextSize(
    JNIEnv * /* env */, jclass /* clazz */, jlong jHandle) {
    auto *handle = reinterpret_cast<EngineHandle *>(jHandle);
    return handle ? handle->n_ctx : 0;
}

JNIEXPORT void JNICALL
Java_io_github_fadizg_kmpai_llm_LlamaNative_nativeResetCache(
    JNIEnv * /* env */, jclass /* clazz */, jlong jHandle) {
    auto *handle = reinterpret_cast<EngineHandle *>(jHandle);
    if (handle == nullptr) return;
    std::lock_guard<std::mutex> lock(handle->generate_mutex);
    llama_memory_clear(llama_get_memory(handle->ctx), true);
    handle->cached_tokens.clear();
}

} // extern "C"
