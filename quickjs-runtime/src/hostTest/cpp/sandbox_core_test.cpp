#include "sandbox_core.h"

#include <chrono>
#include <cstddef>
#include <functional>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <utility>
#include <vector>

namespace {

using ararai::quickjs::CancelSession;
using ararai::quickjs::CreateSession;
using ararai::quickjs::DestroySession;
using ararai::quickjs::Evaluate;
using ararai::quickjs::SandboxLimits;
using ararai::quickjs::SandboxResult;
using ararai::quickjs::SandboxSession;
using ararai::quickjs::SandboxStatus;

constexpr SandboxLimits kDefaultLimits{
    8ULL * 1024 * 1024,
    1024ULL * 1024,
    250,
    64 * 1024,
};

using Session = std::unique_ptr<SandboxSession, decltype(&DestroySession)>;

Session NewSession(SandboxLimits limits = kDefaultLimits) {
    Session session(CreateSession(limits), DestroySession);
    if (!session) throw std::runtime_error("QuickJS session was not created");
    return session;
}

SandboxResult Run(
    std::string_view source,
    std::string_view entrypoint = "run",
    std::string_view input = "{}",
    bool expand_arguments = false,
    SandboxLimits limits = kDefaultLimits
) {
    auto session = NewSession(limits);
    return Evaluate(session.get(), source, entrypoint, input, expand_arguments);
}

void Require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}

void RequireStatus(const SandboxResult& result, SandboxStatus expected) {
    if (result.status != expected) {
        throw std::runtime_error(
            std::string("unexpected status: ") + ararai::quickjs::StatusName(result.status)
        );
    }
}

void RequireSuccess(const SandboxResult& result, std::string_view expected) {
    RequireStatus(result, SandboxStatus::kOk);
    if (result.payload != expected) {
        throw std::runtime_error("unexpected JSON output: " + result.payload);
    }
}

void ExecutesRealJavaScriptControlFlowAndArguments() {
    const auto result = Run(
        R"JS(
            function compute(context, values) {
              let total = 0;
              for (const value of values) {
                if (value > context.minimum) total += value;
              }
              return {label: context.label, total, doubled: values.map(x => x * 2)};
            }
        )JS",
        "compute",
        R"JSON([{"minimum":2,"label":"São Paulo 🦜"},[1,3,5]])JSON",
        true
    );
    RequireSuccess(
        result,
        R"JSON({"label":"São Paulo 🦜","total":8,"doubled":[2,6,10]})JSON"
    );
}

void RemovesForbiddenAndConstructorGlobals() {
    const auto result = Run(R"JS(
        function run(input) {
          return [
            eval, Function, Date, WebAssembly, Promise, fetch, require, process,
            Java, Packages, document, localStorage, Math.random,
            (() => {}).constructor, ({}).constructor.constructor
          ].map(value => typeof value);
        }
    )JS");
    RequireSuccess(
        result,
        R"JSON(["undefined","undefined","undefined","undefined","undefined","undefined","undefined","undefined","undefined","undefined","undefined","undefined","undefined","undefined","undefined"])JSON"
    );
}

void RejectsAdversarialAmbientAuthorityAttempts() {
    const auto result = Run(R"JS(
        function run(input) {
          const attempts = [
            () => eval('1'),
            () => Function('return 1')(),
            () => (() => {}).constructor('return globalThis')(),
            () => ({}).constructor.constructor('return globalThis')(),
            () => Object.getPrototypeOf(() => {}).constructor('return globalThis')(),
            () => fetch('https://forbidden.invalid'),
            () => new XMLHttpRequest(),
            () => new WebSocket('wss://forbidden.invalid'),
            () => require('fs').readFileSync('/etc/passwd'),
            () => process.env,
            () => Deno.readTextFile('/etc/passwd'),
            () => Bun.file('/etc/passwd'),
            () => Java.type('java.lang.System').getenv(),
            () => Packages.java.lang.System.getenv(),
            () => localStorage.getItem('credential'),
            () => sessionStorage.getItem('credential'),
            () => document.cookie,
          ];
          let blocked = 0;
          for (const attempt of attempts) {
            try {
              if (attempt() === undefined) blocked += 1;
            } catch (_) {
              blocked += 1;
            }
          }
          return {blocked, total: attempts.length};
        }
    )JS");
    RequireSuccess(result, R"JSON({"blocked":17,"total":17})JSON");
}

void RejectsStaticAndDynamicImports() {
    RequireStatus(
        Run("import value from 'host'; function run(input){ return input; }"),
        SandboxStatus::kScript
    );
    const auto dynamic_import = Run("function run(input){ return import('host'); }");
    Require(dynamic_import.status != SandboxStatus::kOk, "dynamic import was accepted");
}

void DeepFreezesCopiedInput() {
    const auto result = Run(R"JS(
        function run(input) {
          let rootBlocked = false;
          let nestedBlocked = false;
          try { input.value = 99; } catch (_) { rootBlocked = true; }
          try { input.nested.value = 99; } catch (_) { nestedBlocked = true; }
          return {value: input.value, nested: input.nested.value, rootBlocked, nestedBlocked};
        }
    )JS", "run", R"JSON({"value":1,"nested":{"value":2}})JSON");
    RequireSuccess(
        result,
        R"JSON({"value":1,"nested":2,"rootBlocked":true,"nestedBlocked":true})JSON"
    );
}

void CreatesFreshIsolatesAndReplaysExplicitInputs() {
    constexpr std::string_view source = R"JS(
        var counter = 0;
        function run(input) {
          counter += 1;
          return {counter, localTime: input.localTime, seed: input.seed};
        }
    )JS";
    const auto first = Run(source, "run", R"JSON({"localTime":"06:00","seed":42})JSON");
    const auto replay = Run(source, "run", R"JSON({"localTime":"06:00","seed":42})JSON");
    const auto changed = Run(source, "run", R"JSON({"localTime":"07:00","seed":43})JSON");
    RequireSuccess(first, R"JSON({"counter":1,"localTime":"06:00","seed":42})JSON");
    Require(first.payload == replay.payload, "identical explicit input did not replay identically");
    Require(first.payload != changed.payload, "changed explicit input had no effect");
}

void RejectsMalformedNativeBoundaryInputs() {
    const std::string malformed_utf8("\xc0\xaf", 2);
    RequireStatus(Run("", "run", "{}"), SandboxStatus::kInvalid);
    RequireStatus(Run("function run(x){return x}", "../run", "{}"), SandboxStatus::kInvalid);
    RequireStatus(Run("function run(x){return x}", "run", "{"), SandboxStatus::kInvalid);
    RequireStatus(Run(malformed_utf8, "run", "{}"), SandboxStatus::kInvalid);
    RequireStatus(
        Run("function run(x){return x}", "run", malformed_utf8),
        SandboxStatus::kInvalid
    );
    RequireStatus(
        Run("function run(a,b,c,d){return true}", "run", "[1,2,3,4,5]", true),
        SandboxStatus::kInvalid
    );
    Require(CreateSession({}) == nullptr, "zero limits created a native session");
    Require(
        CreateSession({64ULL * 1024 * 1024, 1024, 10'000, 1024 * 1024}) == nullptr,
        "above-policy native limits created a session"
    );

    auto one_shot = NewSession();
    RequireSuccess(
        Evaluate(one_shot.get(), "function run(input){return true}", "run", "{}", false),
        "true"
    );
    RequireStatus(
        Evaluate(one_shot.get(), "function run(input){return true}", "run", "{}", false),
        SandboxStatus::kInvalid
    );
}

void RejectsScriptFailuresWithoutLeakingExceptionText() {
    const auto thrown = Run(
        "function run(input){ throw new Error('credential=super-secret'); }"
    );
    RequireStatus(thrown, SandboxStatus::kScript);
    Require(thrown.payload.empty(), "script exception text crossed the native boundary");

    RequireStatus(Run("function missing(input){ return input; }"), SandboxStatus::kScript);
    RequireStatus(Run("function run(input){ const value={}; value.self=value; return value; }"),
                  SandboxStatus::kScript);
    RequireStatus(Run("function run(input){ return 1n; }"), SandboxStatus::kScript);
}

void EnforcesOutputMemoryAndStackLimits() {
    SandboxLimits output_limits = kDefaultLimits;
    output_limits.max_output_bytes = 128;
    RequireStatus(
        Run("function run(input){ return 'x'.repeat(2048); }", "run", "{}", false, output_limits),
        SandboxStatus::kResource
    );

    SandboxLimits stack_limits = kDefaultLimits;
    stack_limits.stack_bytes = 64 * 1024;
    RequireStatus(
        Run("function run(input){ return run(input); }", "run", "{}", false, stack_limits),
        SandboxStatus::kResource
    );

    SandboxLimits memory_limits = kDefaultLimits;
    memory_limits.memory_bytes = 1024 * 1024;
    const auto allocation = Run(
        "function run(input){ const values=[]; while(true) values.push('x'.repeat(4096)); }",
        "run",
        "{}",
        false,
        memory_limits
    );
    Require(
        allocation.status == SandboxStatus::kResource ||
            allocation.status == SandboxStatus::kTimeout,
        "allocation pressure escaped resource controls"
    );
}

void InterruptsDeadlineAndRecovers() {
    SandboxLimits limits = kDefaultLimits;
    limits.execution_millis = 30;
    const auto started = std::chrono::steady_clock::now();
    RequireStatus(
        Run("function run(input){ while(true) {} }", "run", "{}", false, limits),
        SandboxStatus::kTimeout
    );
    const auto elapsed = std::chrono::steady_clock::now() - started;
    Require(elapsed < std::chrono::seconds(2), "deadline did not terminate promptly");
    RequireSuccess(Run("function run(input){ return input + 1; }", "run", "1"), "2");
}

void InterruptsCancellationFromAnotherThread() {
    SandboxLimits limits = kDefaultLimits;
    limits.execution_millis = 1000;
    auto session = NewSession(limits);
    SandboxResult result;
    std::thread worker([&] {
        result = Evaluate(
            session.get(),
            "function run(input){ while(true) {} }",
            "run",
            "{}",
            false
        );
    });
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    CancelSession(session.get());
    worker.join();
    RequireStatus(result, SandboxStatus::kCancelled);
}

void SurvivesRepeatedCreationSuccessAndFailure() {
    for (int index = 0; index < 100; ++index) {
        if (index % 2 == 0) {
            const auto result = Run(
                "var retained=" + std::to_string(index) +
                "; function run(input){ return retained; }"
            );
            RequireSuccess(result, std::to_string(index));
        } else {
            const auto result = Run(
                "function run(input){ throw new Error('credential=secret'); }"
            );
            RequireStatus(result, SandboxStatus::kScript);
            Require(result.payload.empty(), "failure retained exception content");
        }
    }
}

struct TestCase {
    const char* name;
    std::function<void()> run;
};

}  // namespace

int main() {
    std::cout.setf(std::ios::unitbuf);
    std::cerr.setf(std::ios::unitbuf);
    const std::vector<TestCase> tests{
        {"executes real JavaScript control flow and arguments", ExecutesRealJavaScriptControlFlowAndArguments},
        {"removes forbidden and constructor globals", RemovesForbiddenAndConstructorGlobals},
        {"rejects adversarial ambient authority attempts", RejectsAdversarialAmbientAuthorityAttempts},
        {"rejects static and dynamic imports", RejectsStaticAndDynamicImports},
        {"deep freezes copied input", DeepFreezesCopiedInput},
        {"creates fresh isolates and replays explicit inputs", CreatesFreshIsolatesAndReplaysExplicitInputs},
        {"rejects malformed native boundary inputs", RejectsMalformedNativeBoundaryInputs},
        {"rejects script failures without leaking exception text", RejectsScriptFailuresWithoutLeakingExceptionText},
        {"enforces output memory and stack limits", EnforcesOutputMemoryAndStackLimits},
        {"interrupts deadline and recovers", InterruptsDeadlineAndRecovers},
        {"interrupts cancellation from another thread", InterruptsCancellationFromAnotherThread},
        {"survives repeated creation success and failure", SurvivesRepeatedCreationSuccessAndFailure},
    };

    std::size_t passed = 0;
    for (const auto& test : tests) {
        try {
            test.run();
            ++passed;
            std::cout << "PASS: " << test.name << '\n';
        } catch (const std::exception& error) {
            std::cerr << "FAIL: " << test.name << ": " << error.what() << '\n';
        }
    }
    std::cout << passed << '/' << tests.size() << " host-native QuickJS tests passed\n";
    return passed == tests.size() ? 0 : 1;
}
