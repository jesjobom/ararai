#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cmath>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char * LOG_TAG = "ArarAI.NativeLlm";
constexpr const char * INVALID_LOGITS_ERROR = "Native sampler received invalid logits";
constexpr int REPEAT_PENALTY_LAST_N = 64;

struct NativeLlmHandle {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    const llama_vocab * vocab = nullptr;
    std::string model_path;
    int gpu_layer_count = 0;
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

jstring utf8_to_jstring(JNIEnv * env, const std::string & value) {
    std::vector<jchar> utf16;
    utf16.reserve(value.size());

    for (size_t index = 0; index < value.size();) {
        const unsigned char current = static_cast<unsigned char>(value[index]);
        uint32_t codepoint = 0;
        size_t sequence_size = 0;

        if (current <= 0x7F) {
            codepoint = current;
            sequence_size = 1;
        } else if ((current & 0xE0) == 0xC0) {
            codepoint = current & 0x1F;
            sequence_size = 2;
        } else if ((current & 0xF0) == 0xE0) {
            codepoint = current & 0x0F;
            sequence_size = 3;
        } else if ((current & 0xF8) == 0xF0) {
            codepoint = current & 0x07;
            sequence_size = 4;
        } else {
            return nullptr;
        }

        if (index + sequence_size > value.size()) {
            return nullptr;
        }

        for (size_t offset = 1; offset < sequence_size; ++offset) {
            const unsigned char continuation = static_cast<unsigned char>(value[index + offset]);
            if ((continuation & 0xC0) != 0x80) {
                return nullptr;
            }
            codepoint = (codepoint << 6) | (continuation & 0x3F);
        }

        if (codepoint <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(codepoint));
        } else {
            codepoint -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (codepoint >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (codepoint & 0x3FF)));
        }
        index += sequence_size;
    }

    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
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

std::string validate_logits(llama_context * context, const llama_vocab * vocab) {
    const float * logits = llama_get_logits_ith(context, -1);
    if (logits == nullptr) {
        return "Native sampler logits are unavailable";
    }

    const int n_vocab = llama_vocab_n_tokens(vocab);
    int invalid_count = 0;
    int first_invalid = -1;
    int negative_infinity_count = 0;
    float min_logit = 0.0f;
    float max_logit = 0.0f;
    bool has_valid = false;

    for (int token_id = 0; token_id < n_vocab; ++token_id) {
        const float logit = logits[token_id];
        if (std::isnan(logit) || logit == INFINITY) {
            if (first_invalid < 0) {
                first_invalid = token_id;
            }
            invalid_count += 1;
            continue;
        }
        if (logit == -INFINITY) {
            negative_infinity_count += 1;
            continue;
        }
        if (!has_valid) {
            min_logit = logit;
            max_logit = logit;
            has_valid = true;
        } else {
            min_logit = std::min(min_logit, logit);
            max_logit = std::max(max_logit, logit);
        }
    }

    if (invalid_count > 0 || !has_valid) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            LOG_TAG,
            "%s: invalid=%d neg_inf=%d first_invalid=%d vocab=%d valid=%s min=%f max=%f",
            INVALID_LOGITS_ERROR,
            invalid_count,
            negative_infinity_count,
            first_invalid,
            n_vocab,
            has_valid ? "true" : "false",
            min_logit,
            max_logit
        );
        return INVALID_LOGITS_ERROR;
    }

    return "";
}

size_t valid_utf8_prefix_size(const std::string & value) {
    size_t index = 0;
    size_t last_valid = 0;

    while (index < value.size()) {
        const unsigned char current = static_cast<unsigned char>(value[index]);
        size_t sequence_size = 0;

        if (current <= 0x7F) {
            sequence_size = 1;
        } else if ((current & 0xE0) == 0xC0) {
            sequence_size = 2;
            if (current < 0xC2) return last_valid;
        } else if ((current & 0xF0) == 0xE0) {
            sequence_size = 3;
        } else if ((current & 0xF8) == 0xF0) {
            sequence_size = 4;
            if (current > 0xF4) return last_valid;
        } else {
            return last_valid;
        }

        if (index + sequence_size > value.size()) {
            return last_valid;
        }

        for (size_t offset = 1; offset < sequence_size; ++offset) {
            const unsigned char continuation = static_cast<unsigned char>(value[index + offset]);
            if ((continuation & 0xC0) != 0x80) {
                return last_valid;
            }
        }

        if (sequence_size == 3) {
            const unsigned char second = static_cast<unsigned char>(value[index + 1]);
            if ((current == 0xE0 && second < 0xA0) || (current == 0xED && second >= 0xA0)) {
                return last_valid;
            }
        } else if (sequence_size == 4) {
            const unsigned char second = static_cast<unsigned char>(value[index + 1]);
            if ((current == 0xF0 && second < 0x90) || (current == 0xF4 && second >= 0x90)) {
                return last_valid;
            }
        }

        index += sequence_size;
        last_valid = index;
    }

    return last_valid;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_jesjobom_ararai_engine_JniLlamaNativeBridge_loadModel(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jint context_tokens,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat min_p,
    jfloat repeat_penalty,
    jint gpu_layer_count
) {
    initialize_llama();

    const std::string path = to_string(env, model_path);
    if (path.empty()) {
        return 0L;
    }

    auto handle = std::make_unique<NativeLlmHandle>();
    handle->model_path = path;
    handle->gpu_layer_count = std::max(static_cast<int>(gpu_layer_count), 0);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = handle->gpu_layer_count;

    handle->model = llama_model_load_from_file(path.c_str(), model_params);
    if (handle->model == nullptr && model_params.n_gpu_layers > 0) {
        __android_log_print(
            ANDROID_LOG_WARN,
            LOG_TAG,
            "GPU model load failed; retrying CPU-only load: %s",
            path.c_str()
        );
        model_params.n_gpu_layers = 0;
        handle->gpu_layer_count = 0;
        handle->model = llama_model_load_from_file(path.c_str(), model_params);
    }
    if (handle->model == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Unable to load model: %s", path.c_str());
        return 0L;
    }

    handle->vocab = llama_model_get_vocab(handle->model);
    char model_description[256] = {};
    llama_model_desc(handle->model, model_description, sizeof(model_description));
    __android_log_print(
        ANDROID_LOG_INFO,
        LOG_TAG,
        "Loaded model: gpu_layers=%d desc=%s path=%s",
        handle->gpu_layer_count,
        model_description,
        path.c_str()
    );

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
    llama_sampler_chain_add(handle->sampler, llama_sampler_init_top_k(std::max(static_cast<int>(top_k), 1)));
    llama_sampler_chain_add(handle->sampler, llama_sampler_init_top_p(top_p, 1));
    if (min_p > 0.0f) {
        llama_sampler_chain_add(handle->sampler, llama_sampler_init_min_p(min_p, 1));
    }
    if (repeat_penalty > 0.0f && repeat_penalty != 1.0f) {
        llama_sampler_chain_add(
            handle->sampler,
            llama_sampler_init_penalties(REPEAT_PENALTY_LAST_N, repeat_penalty, 0.0f, 0.0f)
        );
    }
    llama_sampler_chain_add(handle->sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(handle->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle.release()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jesjobom_ararai_engine_JniLlamaNativeBridge_formatStructuredChatPrompt(
    JNIEnv * env,
    jobject,
    jlong native_handle,
    jobjectArray roles,
    jobjectArray contents
) {
    NativeLlmHandle * handle = from_handle(native_handle);
    if (handle == nullptr || handle->model == nullptr) {
        return nullptr;
    }
    if (roles == nullptr || contents == nullptr) {
        return nullptr;
    }

    const char * chat_template = llama_model_chat_template(handle->model, nullptr);
    if (chat_template == nullptr) {
        return nullptr;
    }

    const jsize role_count = env->GetArrayLength(roles);
    const jsize content_count = env->GetArrayLength(contents);
    if (role_count <= 0 || role_count != content_count) {
        return nullptr;
    }

    std::vector<std::string> role_values;
    std::vector<std::string> content_values;
    std::vector<llama_chat_message> messages;
    role_values.reserve(static_cast<size_t>(role_count));
    content_values.reserve(static_cast<size_t>(role_count));
    messages.reserve(static_cast<size_t>(role_count));

    for (jsize index = 0; index < role_count; ++index) {
        auto role_value = static_cast<jstring>(env->GetObjectArrayElement(roles, index));
        auto content_value = static_cast<jstring>(env->GetObjectArrayElement(contents, index));
        role_values.push_back(to_string(env, role_value));
        content_values.push_back(to_string(env, content_value));
        env->DeleteLocalRef(role_value);
        env->DeleteLocalRef(content_value);

        if (role_values.back().empty() || content_values.back().empty()) {
            continue;
        }
        messages.push_back(llama_chat_message{
            role_values.back().c_str(),
            content_values.back().c_str(),
        });
    }
    if (messages.empty()) {
        return nullptr;
    }

    int formatted_size = llama_chat_apply_template(
        chat_template,
        messages.data(),
        static_cast<int32_t>(messages.size()),
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
        messages.data(),
        static_cast<int32_t>(messages.size()),
        true,
        formatted.data(),
        static_cast<int32_t>(formatted.size())
    );
    if (actual_size > static_cast<int>(formatted.size())) {
        formatted.resize(static_cast<size_t>(actual_size));
        actual_size = llama_chat_apply_template(
            chat_template,
            messages.data(),
            static_cast<int32_t>(messages.size()),
            true,
            formatted.data(),
            static_cast<int32_t>(formatted.size())
        );
    }
    if (actual_size <= 0) {
        return nullptr;
    }

    return utf8_to_jstring(env, std::string(formatted.data(), static_cast<size_t>(actual_size)));
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
    std::string pending_utf8;
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), actual_tokens);
    for (int generated = 0; generated < max_tokens && !handle->cancelled; ++generated) {
        const int decode_result = llama_decode(handle->context, batch);
        if (decode_result != 0) {
            return to_jstring(env, "Native decode failed");
        }

        const std::string logits_error = validate_logits(handle->context, handle->vocab);
        if (!logits_error.empty()) {
            __android_log_print(
                ANDROID_LOG_ERROR,
                LOG_TAG,
                "Stopping generation before sampler: gpu_layers=%d path=%s",
                handle->gpu_layer_count,
                handle->model_path.c_str()
            );
            return to_jstring(env, logits_error);
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

        pending_utf8.append(piece.data(), static_cast<size_t>(piece_size));
        const size_t emit_size = valid_utf8_prefix_size(pending_utf8);
        if (emit_size > 0) {
            const std::string token_text_value = pending_utf8.substr(0, emit_size);
            pending_utf8.erase(0, emit_size);
            jstring token_text = utf8_to_jstring(env, token_text_value);
            if (token_text == nullptr) {
                return to_jstring(env, "Failed to decode generated text");
            }
            const jboolean should_continue = env->CallBooleanMethod(callback, on_token, token_text);
            env->DeleteLocalRef(token_text);
            if (env->ExceptionCheck()) {
                return to_jstring(env, "Token callback failed");
            }
            if (!should_continue) {
                handle->cancelled = true;
                return nullptr;
            }
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
