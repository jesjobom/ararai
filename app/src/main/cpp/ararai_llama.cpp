#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char * LOG_TAG = "ArarAI.NativeLlm";

struct NativeLlmHandle {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    const llama_vocab * vocab = nullptr;
    std::atomic_bool cancelled = false;
    std::mutex generate_mutex;
};

std::once_flag init_once;

std::string to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return "";
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring to_jstring(JNIEnv * env, const std::string & value) {
    return env->NewStringUTF(value.c_str());
}

int thread_count() {
    const unsigned int hardware = std::thread::hardware_concurrency();
    if (hardware == 0) return 2;
    return static_cast<int>(std::clamp(hardware, 2u, 6u));
}

void initialize_llama() {
    std::call_once(init_once, [] {
        llama_log_set([](ggml_log_level level, const char * text, void *) {
            if (level >= GGML_LOG_LEVEL_ERROR) {
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", text);
            }
        }, nullptr);
        ggml_backend_load_all();
    });
}

NativeLlmHandle * from_handle(jlong handle) {
    return reinterpret_cast<NativeLlmHandle *>(static_cast<intptr_t>(handle));
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_jesjobom_ararai_engine_JniLlamaNativeBridge_loadModel(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jint context_tokens,
    jfloat temperature,
    jfloat top_p
) {
    initialize_llama();

    const std::string path = to_string(env, model_path);
    if (path.empty()) {
        return 0L;
    }

    auto handle = std::make_unique<NativeLlmHandle>();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    handle->model = llama_model_load_from_file(path.c_str(), model_params);
    if (handle->model == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unable to load model: %s", path.c_str());
        return 0L;
    }

    handle->vocab = llama_model_get_vocab(handle->model);

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(std::max(context_tokens, 256));
    context_params.n_batch = std::min<uint32_t>(context_params.n_ctx, 512);
    context_params.n_threads = thread_count();
    context_params.n_threads_batch = thread_count();
    context_params.no_perf = true;

    handle->context = llama_init_from_model(handle->model, context_params);
    if (handle->context == nullptr) {
        llama_model_free(handle->model);
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unable to create context");
        return 0L;
    }

    handle->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(handle->sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(handle->sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(handle->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle.release()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jesjobom_ararai_engine_JniLlamaNativeBridge_formatChatPrompt(
    JNIEnv * env,
    jobject,
    jlong native_handle,
    jstring prompt
) {
    NativeLlmHandle * handle = from_handle(native_handle);
    if (handle == nullptr || handle->model == nullptr) {
        return nullptr;
    }

    const char * chat_template = llama_model_chat_template(handle->model, nullptr);
    if (chat_template == nullptr) {
        return nullptr;
    }

    const std::string prompt_text = to_string(env, prompt);
    const llama_chat_message message = {
        "user",
        prompt_text.c_str(),
    };

    int formatted_size = llama_chat_apply_template(
        chat_template,
        &message,
        1,
        true,
        nullptr,
        0
    );
    if (formatted_size <= 0) {
        return nullptr;
    }

    std::vector<char> formatted(static_cast<size_t>(formatted_size));
    int actual_size = llama_chat_apply_template(
        chat_template,
        &message,
        1,
        true,
        formatted.data(),
        static_cast<int32_t>(formatted.size())
    );
    if (actual_size > static_cast<int>(formatted.size())) {
        formatted.resize(static_cast<size_t>(actual_size));
        actual_size = llama_chat_apply_template(
            chat_template,
            &message,
            1,
            true,
            formatted.data(),
            static_cast<int32_t>(formatted.size())
        );
    }
    if (actual_size <= 0) {
        return nullptr;
    }

    return env->NewStringUTF(std::string(formatted.data(), static_cast<size_t>(actual_size)).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jesjobom_ararai_engine_JniLlamaNativeBridge_generate(
    JNIEnv * env,
    jobject,
    jlong native_handle,
    jstring prompt,
    jint max_tokens,
    jobject callback
) {
    NativeLlmHandle * handle = from_handle(native_handle);
    if (handle == nullptr || handle->context == nullptr || handle->sampler == nullptr) {
        return to_jstring(env, "Model is not loaded");
    }
    if (callback == nullptr) {
        return to_jstring(env, "Token callback is missing");
    }

    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)Z");
    if (on_token == nullptr) {
        return to_jstring(env, "Token callback method is missing");
    }

    const std::string prompt_text = to_string(env, prompt);
    std::lock_guard<std::mutex> lock(handle->generate_mutex);

    llama_memory_clear(llama_get_memory(handle->context), true);
    llama_sampler_reset(handle->sampler);
    handle->cancelled = false;

    const int prompt_size = static_cast<int>(prompt_text.size());
    const int token_count = -llama_tokenize(
        handle->vocab,
        prompt_text.c_str(),
        prompt_size,
        nullptr,
        0,
        true,
        true
    );
    if (token_count <= 0) {
        return to_jstring(env, "Failed to tokenize prompt");
    }

    const int context_size = static_cast<int>(llama_n_ctx(handle->context));
    if (token_count + max_tokens > context_size) {
        return to_jstring(env, "Prompt is too large for the configured context");
    }

    std::vector<llama_token> prompt_tokens(token_count);
    const int actual_tokens = llama_tokenize(
        handle->vocab,
        prompt_text.c_str(),
        prompt_size,
        prompt_tokens.data(),
        static_cast<int>(prompt_tokens.size()),
        true,
        true
    );
    if (actual_tokens < 0) {
        return to_jstring(env, "Failed to tokenize prompt");
    }

    llama_token next_token = LLAMA_TOKEN_NULL;
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), actual_tokens);
    for (int generated = 0; generated < max_tokens && !handle->cancelled; ++generated) {
        const int decode_result = llama_decode(handle->context, batch);
        if (decode_result != 0) {
            return to_jstring(env, "Native decode failed");
        }

        const llama_token token = llama_sampler_sample(handle->sampler, handle->context, -1);
        if (llama_vocab_is_eog(handle->vocab, token)) {
            return nullptr;
        }

        std::vector<char> piece(256);
        int piece_size = llama_token_to_piece(
            handle->vocab,
            token,
            piece.data(),
            static_cast<int32_t>(piece.size()),
            0,
            true
        );
        if (piece_size < 0) {
            piece.resize(static_cast<size_t>(-piece_size));
            piece_size = llama_token_to_piece(
                handle->vocab,
                token,
                piece.data(),
                static_cast<int32_t>(piece.size()),
                0,
                true
            );
        }
        if (piece_size < 0) {
            return to_jstring(env, "Failed to decode generated token");
        }

        std::string token_piece(piece.data(), static_cast<size_t>(piece_size));
        jstring token_text = env->NewStringUTF(token_piece.c_str());
        const jboolean should_continue = env->CallBooleanMethod(callback, on_token, token_text);
        env->DeleteLocalRef(token_text);
        if (env->ExceptionCheck()) {
            return to_jstring(env, "Token callback failed");
        }
        if (!should_continue) {
            handle->cancelled = true;
            return nullptr;
        }

        next_token = token;
        batch = llama_batch_get_one(&next_token, 1);
    }

    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jesjobom_ararai_engine_JniLlamaNativeBridge_cancel(
    JNIEnv *,
    jobject,
    jlong native_handle
) {
    NativeLlmHandle * handle = from_handle(native_handle);
    if (handle != nullptr) {
        handle->cancelled = true;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_jesjobom_ararai_engine_JniLlamaNativeBridge_unloadModel(
    JNIEnv *,
    jobject,
    jlong native_handle
) {
    NativeLlmHandle * handle = from_handle(native_handle);
    if (handle == nullptr) return;

    handle->cancelled = true;
    std::lock_guard<std::mutex> lock(handle->generate_mutex);
    if (handle->sampler != nullptr) {
        llama_sampler_free(handle->sampler);
        handle->sampler = nullptr;
    }
    if (handle->context != nullptr) {
        llama_free(handle->context);
        handle->context = nullptr;
    }
    if (handle->model != nullptr) {
        llama_model_free(handle->model);
        handle->model = nullptr;
    }
    delete handle;
}
