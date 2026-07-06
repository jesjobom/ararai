# Define ArarAI Project Foundation

## Why

ArarAI is starting as an exploratory Android project for local LLM inference.
Before generating application code, the core product, runtime, environment, and
test-loop decisions need a durable home so future implementation work does not
reopen settled choices or hide assumptions.

## What Changes

- Define ArarAI as an Android SDK 36 local LLM hub.
- Define the initial Android namespace/application ID as
  `com.jesjobom.ararai`.
- Pin the initial Kotlin and Compose setup.
- Record the first runtime direction as llama.cpp plus GGUF through JNI/NDK.
- Declare that the MVP has no external backend, database, or model API.
- Choose one configured GGUF model with automatic startup download as the first
  model source.
- Record that early testing targets a physical Galaxy 26 device instead of an
  emulator.
- Define the expected split between building APKs in the OpenClaw container and
  installing them outside the container with ADB.
- Define the smallest first vertical slice for chat plus local inference.

## Out Of Scope

- Creating the Android project scaffold.
- Implementing the actual download worker, chat UI, inference, voice, or image
  support.
- Configuring release signing.
- Adding external services or hosted APIs.
