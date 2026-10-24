## 1. Prove the QuickJS foundation

- [x] 1.1 Define the checked-in runtime threat matrix and feasibility criteria
  for isolation, API 28+/arm64 support, license, maintenance, dependency
  verification, R8, APK size, startup cost, memory, stack, interruption, and
  cancellation; verify every criterion maps to an automated or documented
  physical-device check.
- [x] 1.2 Build a throwaway QuickJS Android adapter using the preferred pinned
  source/bridge and verify an optimized release-candidate arm64 APK, through
  either the focused instrumentation suite or the release-candidate manual
  validator, evaluates a data-only fixture without exposing a JVM object.
- [x] 1.3 Exercise the spike with infinite loops, excessive recursion/allocation,
  forbidden globals, cancellation, malformed UTF-8/JSON, and repeated isolate
  creation; record measured termination and resource evidence and either accept
  QuickJS or stop the change and amend the design before continuing.
- [x] 1.4 Record the accepted dependency origin, version, license, checksums,
  native ABI contents, R8 rules, binary-size delta, and update procedure; verify
  dependency locking/verification and repository license checks pass.

## 2. Define versioned program contracts

- [x] 2.1 Add failing tests for supported, unknown, missing, additional,
  malformed, oversized, and invalid manifest fields plus source SHA-256
  mismatch; verify the test cases cover every manifest requirement before
  implementing the parser.
- [x] 2.2 Implement immutable widget program, manifest, entrypoint, capability,
  requested-limit, digest, and stable failure contracts; verify construction
  rejects invalid combinations deterministically.
- [x] 2.3 Implement strict JSON parsing and canonical serialization without code
  evaluation; verify round trips are stable and incompatible or altered
  packages fail before a runtime is created.
- [x] 2.4 Implement checked-in API/resource policies and effective-grant
  intersection; verify undeclared, unsupported, ungranted, and above-policy
  capabilities/limits are rejected without external effects.

## 3. Implement the isolated JavaScript adapter

- [x] 3.1 Integrate the accepted pinned QuickJS source/bridge behind a narrow
  Kotlin interface and data-only JNI boundary; verify debug and optimized
  release-candidate builds contain only the intended arm64 native library.
- [x] 3.2 Implement per-phase runtime/context creation and unconditional cleanup;
  verify mutable globals do not survive another phase/run and repeated failure
  paths release native resources.
- [x] 3.3 Install the frozen versioned bootstrap and immutable structured inputs
  while removing host, dynamic-evaluation, import, WebAssembly, clock,
  randomness, timer, browser, and Node surfaces; verify the adversarial global
  contract suite cannot reach them.
- [x] 3.4 Supply explicit locale, timezone, local time, and seeded pseudo-random
  input; verify identical inputs replay identical program output and changed
  context changes only the expected values.
- [x] 3.5 Enforce memory, stack, monotonic deadline, cancellation flag, JSON
  traversal, source/input/output, and outer coroutine bounds; verify loops,
  recursion, allocation pressure, timeout, and cancellation return stable
  failures without terminating the host process.

## 4. Add the two-phase execution protocol

- [x] 4.1 Add failing plan-schema tests for bounded calls, stable tool/version,
  semantic arguments, unique aliases, cycles, non-finite values, unsupported
  fields, and oversized output; implement normalization and verify invalid plans
  are rejected as a whole.
- [x] 4.2 Implement the runtime coordinator's `plan` phase and effective-grant
  checks; verify an invented tool, transport field, undeclared capability, or
  excessive call list reaches neither the gateway nor a fake executor.
- [x] 4.3 Execute valid planned calls through `WidgetToolExecutionGateway` and
  collect canonical aliased outcomes; verify fake widget-only, model-only,
  disabled, unavailable, failed, timed-out, and cancelled contracts preserve
  existing dispatcher behavior.
- [x] 4.4 Define the versioned presentation tree and add failing tests for node
  count/depth, text/collection/output bounds, unsafe links/actions, unsupported
  nodes/properties, executable content, cycles, and non-finite values.
- [x] 4.5 Implement the fresh-isolate `render` phase and presentation
  normalization; verify it receives immutable bounded outcomes/state, cannot
  invoke tools, and returns only a renderer-neutral validated model.

## 5. Harden runtime failure boundaries

- [x] 5.1 Map parse, compatibility, capability, plan, script, presentation,
  resource, cancellation, tool, and runtime failures to stable production
  codes; verify source fragments, stack traces, native addresses, arbitrary
  exception text, and fake credentials never cross the public result boundary.
- [x] 5.2 Add an adversarial regression corpus covering prototype/global
  traversal, constructor/dynamic-evaluation access, imports, host reflection,
  direct network/storage attempts, output bombs, and malformed native inputs;
  verify all cases fail closed under both debug and release-candidate builds.
- [x] 5.3 Add lifecycle/concurrency tests for cancellation during each phase and
  tool dispatch, late results, adapter exceptions, and repeated recovery;
  verify no orphan call, retained isolate, automatic retry, persistence, worker,
  model, or UI side effect remains.

## 6. Document and validate the foundation

- [x] 6.1 Update architecture, project context, dependency/update, license, and
  widget-runtime security documentation with the final program/API contract,
  trust boundaries, measured budgets, and residual in-process native risk;
  verify documentation does not claim a user-facing widget feature exists.
- [x] 6.2 Run focused JVM/Robolectric/native tests, Android lint/builds, dependency
  verification, `openspec validate --all --strict`, and `git diff --check`;
  verify each completes successfully.
- [x] 6.3 Run the runtime/adversarial instrumentation matrix on a supported
  arm64 physical device, or run the equivalent in-app matrix from the dedicated
  release-candidate validator, including optimized code, and record the exported
  device/build/artifact/termination/memory evidence; do not mark the change
  complete if required isolation or bounded-termination evidence fails.
- [x] 6.4 Run `scripts/quality-gate.sh` after all changes and verify existing
  Chat, Voice Chat, model tools, widget-gateway tests, and application startup
  remain green with no production widget, database, navigation, or worker.
- [x] 6.5 Provide a release-candidate-only manual validation launcher that runs
  the physical QuickJS/runtime matrix without a model or network and exports a
  bounded sanitized JSON report; verify the optimized APK contains the launcher
  and exactly one arm64 QuickJS library while normal production source sets do
  not expose the launcher. APK assembly alone does not complete 1.2, 1.3, or 6.3.
