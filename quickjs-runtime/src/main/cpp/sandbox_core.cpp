#include "sandbox_core.h"

#include <atomic>
#include <chrono>
#include <cstring>
#include <limits>
#include <new>
#include <string>
#include <utility>
#include <vector>

extern "C" {
#include "quickjs.h"
}

namespace ararai::quickjs {
namespace {

using Clock = std::chrono::steady_clock;

constexpr std::size_t kMaxSourceBytes = 64 * 1024;
constexpr std::size_t kMaxInputBytes = 256 * 1024;
constexpr std::size_t kMaxEntrypointBytes = 64;
constexpr std::uint32_t kMaxArguments = 4;
constexpr std::uint64_t kMinMemoryBytes = 1024ULL * 1024;
constexpr std::uint64_t kMaxMemoryBytes = 32ULL * 1024 * 1024;
constexpr std::uint64_t kMinStackBytes = 64ULL * 1024;
constexpr std::uint64_t kMaxStackBytes = 1024ULL * 1024;
constexpr std::uint64_t kMaxExecutionMillis = 1000;
constexpr std::size_t kMaxOutputBytes = 256 * 1024;

constexpr char kBootstrap[] = R"JS(
(() => {
  'use strict';
  const freeze = Object.freeze;
  const deepFreeze = (value, seen = new Set()) => {
    if (value === null || (typeof value !== 'object' && typeof value !== 'function')) return value;
    if (seen.has(value)) return value;
    seen.add(value);
    for (const key of Reflect.ownKeys(value)) deepFreeze(value[key], seen);
    return freeze(value);
  };
  const deny = (object, name) => {
    try {
      Object.defineProperty(object, name, {
        value: undefined,
        writable: false,
        enumerable: false,
        configurable: false,
      });
    } catch (_) {}
  };
  for (const fn of [function(){}, function*(){}, async function(){}, async function*(){}]) {
    deny(Object.getPrototypeOf(fn), 'constructor');
  }
  for (const name of [
    'eval', 'Function', 'Date', 'WebAssembly', 'Promise', 'WeakRef',
    'FinalizationRegistry', 'SharedArrayBuffer', 'Atomics', 'fetch',
    'XMLHttpRequest', 'WebSocket', 'require', 'module', 'process', 'Deno',
    'Bun', 'Java', 'Packages', 'console', 'setTimeout', 'setInterval',
    'queueMicrotask', 'performance', 'navigator', 'document', 'window',
    'localStorage', 'sessionStorage'
  ]) deny(globalThis, name);
  deny(Math, 'random');
  freeze(Math);
  freeze(JSON);
  return deepFreeze;
})()
)JS";

bool IsContinuationByte(unsigned char value) {
    return (value & 0xc0U) == 0x80U;
}

bool IsValidUtf8(std::string_view value) {
    std::size_t index = 0;
    while (index < value.size()) {
        const auto first = static_cast<unsigned char>(value[index]);
        if (first <= 0x7fU) {
            ++index;
            continue;
        }

        std::size_t length = 0;
        std::uint32_t code_point = 0;
        if (first >= 0xc2U && first <= 0xdfU) {
            length = 2;
            code_point = first & 0x1fU;
        } else if (first >= 0xe0U && first <= 0xefU) {
            length = 3;
            code_point = first & 0x0fU;
        } else if (first >= 0xf0U && first <= 0xf4U) {
            length = 4;
            code_point = first & 0x07U;
        } else {
            return false;
        }
        if (index + length > value.size()) return false;
        for (std::size_t offset = 1; offset < length; ++offset) {
            const auto next = static_cast<unsigned char>(value[index + offset]);
            if (!IsContinuationByte(next)) return false;
            code_point = (code_point << 6U) | (next & 0x3fU);
        }
        if ((length == 3 && code_point < 0x800U) ||
            (length == 4 && code_point < 0x10000U) ||
            (code_point >= 0xd800U && code_point <= 0xdfffU) ||
            code_point > 0x10ffffU) {
            return false;
        }
        index += length;
    }
    return true;
}

bool IsValidEntrypoint(std::string_view value) {
    if (value.empty() || value.size() > kMaxEntrypointBytes) return false;
    const auto is_start = [](char character) {
        return (character >= 'A' && character <= 'Z') ||
            (character >= 'a' && character <= 'z') || character == '_' || character == '$';
    };
    const auto is_part = [&](char character) {
        return is_start(character) || (character >= '0' && character <= '9');
    };
    if (!is_start(value.front())) return false;
    for (char character : value.substr(1)) {
        if (!is_part(character)) return false;
    }
    return true;
}

void ClearException(JSContext* context) {
    JSValue exception = JS_GetException(context);
    JS_FreeValue(context, exception);
}

bool AddIntrinsics(JSContext* context) {
    if (JS_AddIntrinsicBaseObjects(context) < 0) return false;
    if (JS_AddIntrinsicEval(context) < 0) return false;
    if (JS_AddIntrinsicStringNormalize(context) < 0) return false;
    JS_AddIntrinsicRegExpCompiler(context);
    if (JS_AddIntrinsicRegExp(context) < 0) return false;
    if (JS_AddIntrinsicJSON(context) < 0) return false;
    if (JS_AddIntrinsicMapSet(context) < 0) return false;
    if (JS_AddIntrinsicTypedArrays(context) < 0) return false;
    if (JS_AddIntrinsicPromise(context) < 0) return false;
    return true;
}

}  // namespace

struct SandboxSession {
    JSRuntime* runtime = nullptr;
    JSContext* context = nullptr;
    std::atomic<bool> cancelled{false};
    std::atomic<bool> timed_out{false};
    std::atomic<bool> evaluation_started{false};
    Clock::time_point deadline;
    std::size_t max_output_bytes = 0;
};

namespace {

int InterruptHandler(JSRuntime*, void* opaque) {
    auto* session = static_cast<SandboxSession*>(opaque);
    if (session->cancelled.load(std::memory_order_relaxed)) return 1;
    if (Clock::now() >= session->deadline) {
        session->timed_out.store(true, std::memory_order_relaxed);
        return 1;
    }
    return 0;
}

SandboxResult InterruptedResult(SandboxSession* session) {
    if (session->cancelled.load(std::memory_order_relaxed)) {
        return {SandboxStatus::kCancelled, {}};
    }
    if (session->timed_out.load(std::memory_order_relaxed)) {
        return {SandboxStatus::kTimeout, {}};
    }
    return {SandboxStatus::kResource, {}};
}

SandboxResult ExceptionResult(SandboxSession* session) {
    if (session->cancelled.load(std::memory_order_relaxed) ||
        session->timed_out.load(std::memory_order_relaxed)) {
        ClearException(session->context);
        return InterruptedResult(session);
    }
    JSValue exception = JS_GetException(session->context);
    JSValue message = JS_GetPropertyStr(session->context, exception, "message");
    const char* text = JS_ToCString(session->context, message);
    const bool resource = text != nullptr &&
        (std::strstr(text, "out of memory") != nullptr ||
         std::strstr(text, "stack overflow") != nullptr ||
         std::strstr(text, "interrupted") != nullptr);
    if (text != nullptr) JS_FreeCString(session->context, text);
    JS_FreeValue(session->context, message);
    JS_FreeValue(session->context, exception);
    return {resource ? SandboxStatus::kResource : SandboxStatus::kScript, {}};
}

SandboxResult EvaluateImpl(
    SandboxSession* session,
    std::string_view source,
    std::string_view entrypoint,
    std::string_view input_json,
    bool expand_arguments
) {
    if (session == nullptr || session->context == nullptr ||
        session->evaluation_started.exchange(true, std::memory_order_relaxed) || source.empty() ||
        source.size() > kMaxSourceBytes || input_json.empty() ||
        input_json.size() > kMaxInputBytes || !IsValidEntrypoint(entrypoint) ||
        !IsValidUtf8(source) || !IsValidUtf8(input_json)) {
        return {SandboxStatus::kInvalid, {}};
    }

    JSContext* context = session->context;
    // Sessions are created before Kotlin dispatches evaluation to its bounded
    // native executor. QuickJS must measure stack usage from that worker
    // thread, not from the thread that allocated the runtime.
    JS_UpdateStackTop(session->runtime);
    JSValue deep_freeze = JS_Eval(
        context,
        kBootstrap,
        sizeof(kBootstrap) - 1,
        "<bootstrap>",
        JS_EVAL_TYPE_GLOBAL | JS_EVAL_FLAG_STRICT
    );
    if (JS_IsException(deep_freeze)) {
        ClearException(context);
        return InterruptedResult(session);
    }

    JSValue source_result = JS_Eval(
        context,
        source.data(),
        source.size(),
        "<widget>",
        JS_EVAL_TYPE_GLOBAL | JS_EVAL_FLAG_STRICT
    );
    if (JS_IsException(source_result)) {
        JS_FreeValue(context, deep_freeze);
        return ExceptionResult(session);
    }
    JS_FreeValue(context, source_result);

    const std::string entrypoint_name(entrypoint);
    JSValue global = JS_GetGlobalObject(context);
    JSValue function = JS_GetPropertyStr(context, global, entrypoint_name.c_str());
    JS_FreeValue(context, global);
    if (!JS_IsFunction(context, function)) {
        JS_FreeValue(context, function);
        JS_FreeValue(context, deep_freeze);
        return {SandboxStatus::kScript, {}};
    }

    JSValue argument = JS_ParseJSON(context, input_json.data(), input_json.size(), "<input>");
    if (JS_IsException(argument)) {
        ClearException(context);
        JS_FreeValue(context, function);
        JS_FreeValue(context, deep_freeze);
        return {SandboxStatus::kInvalid, {}};
    }

    JSValue frozen = JS_Call(context, deep_freeze, JS_UNDEFINED, 1, &argument);
    JS_FreeValue(context, deep_freeze);
    JS_FreeValue(context, argument);
    if (JS_IsException(frozen)) {
        ClearException(context);
        JS_FreeValue(context, function);
        return InterruptedResult(session);
    }

    std::vector<JSValue> arguments;
    if (expand_arguments) {
        if (!JS_IsArray(context, frozen)) {
            JS_FreeValue(context, frozen);
            JS_FreeValue(context, function);
            return {SandboxStatus::kInvalid, {}};
        }
        JSValue length_value = JS_GetPropertyStr(context, frozen, "length");
        std::uint32_t length = 0;
        if (JS_ToUint32(context, &length, length_value) < 0 ||
            length == 0 || length > kMaxArguments) {
            JS_FreeValue(context, length_value);
            JS_FreeValue(context, frozen);
            JS_FreeValue(context, function);
            ClearException(context);
            return {SandboxStatus::kInvalid, {}};
        }
        JS_FreeValue(context, length_value);
        arguments.reserve(length);
        for (std::uint32_t index = 0; index < length; ++index) {
            arguments.push_back(JS_GetPropertyUint32(context, frozen, index));
        }
    } else {
        arguments.push_back(JS_DupValue(context, frozen));
    }

    JSValue call_result = JS_Call(
        context,
        function,
        JS_UNDEFINED,
        static_cast<int>(arguments.size()),
        arguments.data()
    );
    for (JSValue value : arguments) JS_FreeValue(context, value);
    JS_FreeValue(context, frozen);
    JS_FreeValue(context, function);
    if (JS_IsException(call_result)) return ExceptionResult(session);
    if (JS_PromiseState(context, call_result) != static_cast<JSPromiseStateEnum>(-1)) {
        JS_FreeValue(context, call_result);
        return {SandboxStatus::kScript, {}};
    }

    JSValue json = JS_JSONStringify(context, call_result, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(context, call_result);
    if (JS_IsException(json) || !JS_IsString(json)) {
        if (JS_IsException(json)) ClearException(context);
        JS_FreeValue(context, json);
        return {SandboxStatus::kScript, {}};
    }

    std::size_t output_size = 0;
    const char* output = JS_ToCStringLen(context, &output_size, json);
    if (output == nullptr) {
        ClearException(context);
        JS_FreeValue(context, json);
        return {SandboxStatus::kResource, {}};
    }
    SandboxResult result = output_size > session->max_output_bytes
        ? SandboxResult{SandboxStatus::kResource, {}}
        : SandboxResult{SandboxStatus::kOk, std::string(output, output_size)};
    JS_FreeCString(context, output);
    JS_FreeValue(context, json);
    return result;
}

}  // namespace

SandboxSession* CreateSession(const SandboxLimits& limits) noexcept {
    if (limits.memory_bytes < kMinMemoryBytes || limits.memory_bytes > kMaxMemoryBytes ||
        limits.stack_bytes < kMinStackBytes || limits.stack_bytes > kMaxStackBytes ||
        limits.execution_millis == 0 || limits.execution_millis > kMaxExecutionMillis ||
        limits.max_output_bytes == 0 || limits.max_output_bytes > kMaxOutputBytes ||
        limits.memory_bytes > std::numeric_limits<std::size_t>::max() ||
        limits.stack_bytes > std::numeric_limits<std::size_t>::max()) {
        return nullptr;
    }

    auto* session = new (std::nothrow) SandboxSession();
    if (session == nullptr) return nullptr;
    session->runtime = JS_NewRuntime();
    if (session->runtime == nullptr) {
        delete session;
        return nullptr;
    }
    JS_SetMemoryLimit(session->runtime, static_cast<std::size_t>(limits.memory_bytes));
    JS_SetMaxStackSize(session->runtime, static_cast<std::size_t>(limits.stack_bytes));
    session->deadline = Clock::now() + std::chrono::milliseconds(limits.execution_millis);
    session->max_output_bytes = limits.max_output_bytes;
    JS_SetInterruptHandler(session->runtime, InterruptHandler, session);
    session->context = JS_NewContextRaw(session->runtime);
    if (session->context == nullptr || !AddIntrinsics(session->context)) {
        if (session->context != nullptr) JS_FreeContext(session->context);
        JS_FreeRuntime(session->runtime);
        delete session;
        return nullptr;
    }
    return session;
}

SandboxResult Evaluate(
    SandboxSession* session,
    std::string_view source,
    std::string_view entrypoint,
    std::string_view input_json,
    bool expand_arguments
) noexcept {
    try {
        return EvaluateImpl(session, source, entrypoint, input_json, expand_arguments);
    } catch (const std::bad_alloc&) {
        return {SandboxStatus::kResource, {}};
    } catch (...) {
        return {SandboxStatus::kResource, {}};
    }
}

void CancelSession(SandboxSession* session) noexcept {
    if (session != nullptr) session->cancelled.store(true, std::memory_order_relaxed);
}

void DestroySession(SandboxSession* session) noexcept {
    if (session == nullptr) return;
    if (session->context != nullptr) JS_FreeContext(session->context);
    if (session->runtime != nullptr) JS_FreeRuntime(session->runtime);
    delete session;
}

const char* StatusName(SandboxStatus status) noexcept {
    switch (status) {
        case SandboxStatus::kOk:
            return "ok";
        case SandboxStatus::kInvalid:
            return "invalid";
        case SandboxStatus::kScript:
            return "script";
        case SandboxStatus::kResource:
            return "resource";
        case SandboxStatus::kTimeout:
            return "timeout";
        case SandboxStatus::kCancelled:
            return "cancelled";
    }
    return "resource";
}

}  // namespace ararai::quickjs
