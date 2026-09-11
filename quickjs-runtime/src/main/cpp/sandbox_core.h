#ifndef ARARAI_QUICKJS_SANDBOX_CORE_H
#define ARARAI_QUICKJS_SANDBOX_CORE_H

#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>

namespace ararai::quickjs {

enum class SandboxStatus {
    kOk,
    kInvalid,
    kScript,
    kResource,
    kTimeout,
    kCancelled,
};

struct SandboxResult {
    SandboxStatus status = SandboxStatus::kResource;
    std::string payload;
};

struct SandboxLimits {
    std::uint64_t memory_bytes = 0;
    std::uint64_t stack_bytes = 0;
    std::uint64_t execution_millis = 0;
    std::size_t max_output_bytes = 0;
};

struct SandboxSession;

SandboxSession* CreateSession(const SandboxLimits& limits) noexcept;

SandboxResult Evaluate(
    SandboxSession* session,
    std::string_view source,
    std::string_view entrypoint,
    std::string_view input_json,
    bool expand_arguments
) noexcept;

void CancelSession(SandboxSession* session) noexcept;

void DestroySession(SandboxSession* session) noexcept;

const char* StatusName(SandboxStatus status) noexcept;

}  // namespace ararai::quickjs

#endif  // ARARAI_QUICKJS_SANDBOX_CORE_H
