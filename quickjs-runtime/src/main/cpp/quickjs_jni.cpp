#include <jni.h>

#include <cstdint>
#include <string>
#include <cstring>

#include "sandbox_core.h"

namespace {

std::string ReadBytes(JNIEnv* env, jbyteArray value) {
    if (value == nullptr) return {};
    const jsize size = env->GetArrayLength(value);
    std::string bytes(static_cast<std::size_t>(size), '\0');
    if (size > 0) {
        env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
    }
    return bytes;
}

jbyteArray Encode(JNIEnv* env, const ararai::quickjs::SandboxResult& result) {
    const char* status = ararai::quickjs::StatusName(result.status);
    const auto status_size = static_cast<jsize>(std::strlen(status));
    const auto payload_size = static_cast<jsize>(result.payload.size());
    auto output = env->NewByteArray(status_size + 1 + payload_size);
    if (output == nullptr) return nullptr;
    env->SetByteArrayRegion(output, 0, status_size, reinterpret_cast<const jbyte*>(status));
    const jbyte separator = '\n';
    env->SetByteArrayRegion(output, status_size, 1, &separator);
    if (payload_size > 0) {
        env->SetByteArrayRegion(
            output,
            status_size + 1,
            payload_size,
            reinterpret_cast<const jbyte*>(result.payload.data())
        );
    }
    return output;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_jesjobom_ararai_quickjs_NativeQuickJsBridge_create(
    JNIEnv*,
    jobject,
    jlong memory_bytes,
    jlong stack_bytes,
    jlong execution_millis,
    jint max_output_bytes
) {
    const ararai::quickjs::SandboxLimits limits{
        static_cast<std::uint64_t>(memory_bytes),
        static_cast<std::uint64_t>(stack_bytes),
        static_cast<std::uint64_t>(execution_millis),
        static_cast<std::size_t>(max_output_bytes),
    };
    return reinterpret_cast<jlong>(ararai::quickjs::CreateSession(limits));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_jesjobom_ararai_quickjs_NativeQuickJsBridge_evaluate(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray source_value,
    jbyteArray entrypoint_value,
    jbyteArray input_value,
    jboolean expand_arguments
) {
    try {
        const std::string source = ReadBytes(env, source_value);
        const std::string entrypoint = ReadBytes(env, entrypoint_value);
        const std::string input = ReadBytes(env, input_value);
        if (env->ExceptionCheck()) {
            return Encode(env, {ararai::quickjs::SandboxStatus::kInvalid, {}});
        }
        return Encode(
            env,
            ararai::quickjs::Evaluate(
                reinterpret_cast<ararai::quickjs::SandboxSession*>(handle),
                source,
                entrypoint,
                input,
                expand_arguments == JNI_TRUE
            )
        );
    } catch (...) {
        return Encode(env, {ararai::quickjs::SandboxStatus::kResource, {}});
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_jesjobom_ararai_quickjs_NativeQuickJsBridge_cancel(
    JNIEnv*,
    jobject,
    jlong handle
) {
    ararai::quickjs::CancelSession(
        reinterpret_cast<ararai::quickjs::SandboxSession*>(handle)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_jesjobom_ararai_quickjs_NativeQuickJsBridge_close(
    JNIEnv*,
    jobject,
    jlong handle
) {
    ararai::quickjs::DestroySession(
        reinterpret_cast<ararai::quickjs::SandboxSession*>(handle)
    );
}
