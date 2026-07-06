# ArarAI

ArarAI is an Android application concept for running open local LLMs on-device.
The first milestone is a focused text-chat MVP that downloads or loads an open
model, runs inference locally, and streams text responses without depending on a
remote API or external database.

## Current Direction

- Product name: ArarAI
- Platform: Android
- Target SDK: Android SDK 36
- Runtime direction: llama.cpp with GGUF models through JNI/NDK
- Build toolchain: JDK 17, AGP 9.2.x, Gradle 9.4.1, Build Tools 36.0.0,
  NDK 28.2.13676358
- First device target: Galaxy 26 physical device
- Backend: none for the MVP
- External database: none for the MVP
- Model access: open models only, no Hugging Face token required initially
- Android signing: debug builds only for now
- Development process: TDD by default; write a failing test before implementing
  each behavior when an automated test is practical

## Planning

Project decisions and requirements are tracked under `openspec/`.
The initial project definition lives in:

- `openspec/project.md`
- `openspec/changes/define-project-foundation/`

No Android application scaffold has been created yet.
