#include <jni.h>

#include <chrono>
#include <cstdint>
#include <fstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "whisper.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_jesjobom_ararai_whisper_WhisperRuntime_systemInfo(JNIEnv *env, jobject) {
    const char *info = whisper_print_system_info();
    return env->NewStringUTF(info == nullptr ? "" : info);
}

namespace {

struct WavSamples {
    std::vector<float> samples;
    long duration_millis;
};

uint16_t read_u16(std::istream &input) {
    uint8_t bytes[2]{};
    input.read(reinterpret_cast<char *>(bytes), sizeof(bytes));
    return static_cast<uint16_t>(bytes[0] | (bytes[1] << 8));
}

uint32_t read_u32(std::istream &input) {
    uint8_t bytes[4]{};
    input.read(reinterpret_cast<char *>(bytes), sizeof(bytes));
    return static_cast<uint32_t>(bytes[0] | (bytes[1] << 8) | (bytes[2] << 16) | (bytes[3] << 24));
}

void require_stream(const std::istream &input, const char *message) {
    if (!input) {
        throw std::runtime_error(message);
    }
}

WavSamples read_wav(const std::string &path) {
    std::ifstream input(path, std::ios::binary);
    require_stream(input, "Unable to open WAV file");

    char riff[4]{};
    input.read(riff, sizeof(riff));
    read_u32(input);
    char wave[4]{};
    input.read(wave, sizeof(wave));
    require_stream(input, "Invalid WAV header");
    if (std::string(riff, 4) != "RIFF" || std::string(wave, 4) != "WAVE") {
        throw std::runtime_error("Expected a RIFF WAVE file");
    }

    uint16_t format = 0;
    uint16_t channels = 0;
    uint32_t sample_rate = 0;
    uint16_t bits_per_sample = 0;
    std::vector<int16_t> pcm;
    while (input && pcm.empty()) {
        char chunk_id[4]{};
        input.read(chunk_id, sizeof(chunk_id));
        if (!input) break;
        const uint32_t chunk_size = read_u32(input);
        const std::string id(chunk_id, 4);
        if (id == "fmt ") {
            format = read_u16(input);
            channels = read_u16(input);
            sample_rate = read_u32(input);
            read_u32(input);
            read_u16(input);
            bits_per_sample = read_u16(input);
            if (chunk_size > 16) input.seekg(chunk_size - 16, std::ios::cur);
        } else if (id == "data") {
            if (chunk_size % sizeof(int16_t) != 0) throw std::runtime_error("Unaligned WAV PCM data");
            pcm.resize(chunk_size / sizeof(int16_t));
            input.read(reinterpret_cast<char *>(pcm.data()), chunk_size);
        } else {
            input.seekg(chunk_size, std::ios::cur);
        }
        if ((chunk_size & 1U) != 0U) input.seekg(1, std::ios::cur);
    }
    require_stream(input, "Truncated WAV file");
    if (format != 1 || channels != 1 || sample_rate != 16000 || bits_per_sample != 16 || pcm.empty()) {
        throw std::runtime_error("Whisper requires non-empty mono 16 kHz PCM 16-bit WAV");
    }

    WavSamples result;
    result.samples.reserve(pcm.size());
    for (const int16_t sample : pcm) result.samples.push_back(static_cast<float>(sample) / 32768.0F);
    result.duration_millis = static_cast<long>(pcm.size() * 1000ULL / sample_rate);
    return result;
}

long elapsed_millis(const std::chrono::steady_clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start).count();
}

void throw_java(JNIEnv *env, const std::string &message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(type, message.c_str());
}

std::string from_java(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_jesjobom_ararai_whisper_WhisperRuntime_nativeTranscribe(
    JNIEnv *env,
    jobject,
    jstring model_path,
    jstring wav_path,
    jstring language,
    jint threads) {
    try {
        const std::string model = from_java(env, model_path);
        const std::string wav = from_java(env, wav_path);
        const std::string locale = from_java(env, language);
        const WavSamples audio = read_wav(wav);

        const auto load_started = std::chrono::steady_clock::now();
        whisper_context_params context_params = whisper_context_default_params();
        context_params.use_gpu = false;
        whisper_context *context = whisper_init_from_file_with_params(model.c_str(), context_params);
        if (context == nullptr) throw std::runtime_error("Unable to load Whisper model");
        const long load_millis = elapsed_millis(load_started);

        const auto transcription_started = std::chrono::steady_clock::now();
        whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = threads;
        params.language = locale.c_str();
        params.translate = false;
        params.no_timestamps = true;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.print_special = false;
        const int status = whisper_full(context, params, audio.samples.data(), audio.samples.size());
        if (status != 0) {
            whisper_free(context);
            throw std::runtime_error("Whisper transcription failed with status " + std::to_string(status));
        }
        const long transcription_millis = elapsed_millis(transcription_started);

        std::string text;
        const int segments = whisper_full_n_segments(context);
        for (int index = 0; index < segments; ++index) {
            const char *segment = whisper_full_get_segment_text(context, index);
            if (segment != nullptr) text += segment;
        }
        whisper_free(context);

        jclass string_class = env->FindClass("java/lang/String");
        jobjectArray result = env->NewObjectArray(5, string_class, nullptr);
        const std::string fields[] = {
            text,
            std::to_string(load_millis),
            std::to_string(transcription_millis),
            std::to_string(audio.duration_millis),
            std::to_string(threads),
        };
        for (int index = 0; index < 5; ++index) {
            env->SetObjectArrayElement(result, index, env->NewStringUTF(fields[index].c_str()));
        }
        return result;
    } catch (const std::exception &error) {
        throw_java(env, error.what());
        return nullptr;
    }
}
