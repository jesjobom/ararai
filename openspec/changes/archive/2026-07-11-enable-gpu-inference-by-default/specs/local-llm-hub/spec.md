## ADDED Requirements

### Requirement: GPU-Default Local Inference

The app SHALL use GPU-accelerated local inference as the default runtime path
for configured GGUF models when the device supports the native GPU backend.

#### Scenario: Load with GPU offload first

- **GIVEN** a configured GGUF model is available locally
- **WHEN** the real local inference engine loads the model
- **THEN** it requests GPU layer offload before attempting CPU-only loading
- **AND** no user-facing menu or setting is required to enable GPU usage.

#### Scenario: Graceful fallback when GPU load fails

- **GIVEN** the device cannot initialize the native GPU backend for the model
- **WHEN** the real local inference engine attempts to load the model
- **THEN** the app may retry CPU-only loading to keep the flow from crashing
- **AND** the default attempted path remains GPU acceleration.

### Requirement: GPU Runtime Benchmark Label

The benchmark screen SHALL identify the local runtime as GPU-default so
benchmark results are not confused with previous CPU-only measurements.

#### Scenario: View GPU-default benchmark backend

- **WHEN** the user opens the benchmark screen
- **THEN** the backend label identifies the llama.cpp Vulkan/GPU-default runtime.
