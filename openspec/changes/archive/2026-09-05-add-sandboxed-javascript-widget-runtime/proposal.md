## Why

LLM-authored widgets need real control flow and reusable logic, which would make
a JSON expression tree verbose, difficult to generate, and difficult to debug.
ArarAI therefore needs a separately bounded program runtime before any
user-facing widget lifecycle can safely execute generated JavaScript on-device.

## What Changes

- Add a versioned `WidgetProgram` package containing a strict JSON
  `WidgetProgramManifest` and separately hashed JavaScript source.
- Integrate QuickJS behind an application-owned runtime boundary, subject to an
  initial Android feasibility and isolation gate.
- Expose only a versioned capability facade with deterministic runtime values,
  pure helpers, bounded tool-request planning, bounded tool-result input, and a
  constrained presentation result.
- Keep Android/JVM objects, reflection, arbitrary network access, filesystem,
  process APIs, credentials, dynamic imports, and dynamic code evaluation out
  of the JavaScript environment.
- Enforce application-owned source, heap, stack, execution, iteration,
  invocation, and output limits, including prompt cancellation and controlled
  termination of non-cooperative programs.
- Route program-planned tool requests through the existing widget execution
  gateway; neither QuickJS nor generated code receives a direct tool executor
  or provider transport.
- Validate the runtime with deterministic fixtures, fake widget tools,
  adversarial scripts, optimized R8 builds, and recorded dependency/license/APK
  impact.
- Defer LLM authorship, durable widget state, UI, rendering in Compose,
  scheduling, execution history, and production widget tools.

## Capabilities

### New Capabilities

- `widget-script-runtime`: Defines versioned widget program packages, sandboxed
  JavaScript execution, capability grants, deterministic inputs, resource
  limits, tool-request planning, and constrained presentation output.

### Modified Capabilities

None. The existing application-tool dispatcher remains responsible for
validating every invocation and continues not to interpret generated code.

## Impact

- Affected code: new widget program contracts, manifest parser, runtime
  coordinator, QuickJS adapter, capability facade, presentation validator, and
  deterministic/adversarial test harnesses under the widget boundary.
- Dependencies: one embedded JavaScript runtime and its Android native/JVM
  bridge, accepted only after license, maintenance, ABI, R8, binary-size,
  cancellation, and isolation evidence passes the feasibility gate.
- Security: generated JavaScript becomes untrusted local input and receives no
  ambient authority; tool access remains independently constrained by manifest
  declaration, execution grant, registry eligibility, and shared dispatch.
- Compatibility: no existing Chat, Voice Chat, tool, persistence, navigation,
  or background behavior changes, and no durable widget data is introduced.
