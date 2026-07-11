## 1. Spec

- [x] Create OpenSpec proposal for GPU-default local inference.

## 2. Tests First

- [x] Add JVM boundary coverage showing the real engine requests GPU layer
      offload by default.
- [x] Add JVM coverage for GPU-default benchmark/backend labeling.

## 3. Native Runtime

- [x] Enable the llama.cpp Vulkan backend in the Android native build.
- [x] Pass GPU offload configuration from Kotlin to the native bridge.
- [x] Attempt model loading with GPU offload before CPU fallback.

## 4. Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate enable-gpu-inference-by-default --strict`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
- [x] Document the physical-device smoke test blocker or result.

Physical-device smoke test blocker: this OpenClaw container can build the APK
but does not have direct ADB access to the Android device. The APK has been
copied to the handoff path for external device validation.

Build environment note: this container does not provide a host C/C++ compiler,
which llama.cpp Vulkan needs for `vulkan-shaders-gen`. Validation used local
`zig cc` wrappers through `ARARAI_HOST_CC` and `ARARAI_HOST_CXX`.
