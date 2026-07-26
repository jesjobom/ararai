## MODIFIED Requirements

### Requirement: GPU-Default Local Inference

The app SHALL use bounded GPU-accelerated local inference as the default runtime
path for configured GPU-preferred GGUF models when the device supports the
native GPU backend, and SHALL NOT translate GPU preference into unlimited model
offload.

#### Scenario: Load with GPU offload first

- **GIVEN** a configured GPU-preferred GGUF model is available locally
- **WHEN** the real local inference engine loads the model
- **THEN** it requests the model's configured finite GPU-layer count
- **AND** a legacy entry without an explicit count uses a conservative finite
  default
- **AND** no user-facing menu or setting is required to enable GPU usage
- **AND** the runtime does not use an unlimited layer sentinel.

#### Scenario: Graceful fallback when GPU load fails

- **GIVEN** the device cannot initialize the native GPU backend for the model
- **WHEN** the real local inference engine attempts to load or safely retry the
  model
- **THEN** the app may retry CPU-only loading to keep the flow from crashing
- **AND** CPU-only loading requests zero GPU layers
- **AND** the default attempted path remains bounded GPU acceleration.

### Requirement: Configured Model Runtime Metadata

The app SHALL allow each configured model catalog entry to declare its local
inference runtime, artifact format, acceleration policy, and an optional
llama.cpp GPU-layer count where applicable.

#### Scenario: Parse runtime metadata from catalog

- **GIVEN** a configured llama.cpp catalog entry includes runtime metadata and a
  positive GPU-layer count
- **WHEN** the app parses and resolves the catalog entry
- **THEN** the model records the runtime, artifact format, acceleration policy,
  and GPU-layer count.

#### Scenario: Default legacy GGUF entries to llama.cpp

- **GIVEN** a legacy configured model entry omits runtime and GPU-layer metadata
- **WHEN** the app parses and loads the entry
- **THEN** it defaults to the llama.cpp runtime
- **AND** defaults to the GGUF artifact format
- **AND** defaults to GPU-preferred acceleration
- **AND** uses a conservative finite GPU-layer count.

#### Scenario: Reject incompatible GPU-layer metadata

- **GIVEN** a catalog entry declares a llama.cpp GPU-layer count
- **WHEN** the count is not positive, the runtime is not llama.cpp, or the
  acceleration policy is CPU-only
- **THEN** catalog validation fails with a controlled configuration error.

#### Scenario: Keep unvalidated test models off the Android GPU

- **GIVEN** the checked-in Llama 3.2, LFM2.5, or Ministral 3 test profile is
  selected
- **WHEN** llama.cpp loads the model on the target Android device
- **THEN** the profile requests CPU-only inference
- **AND** LFM2.5 and Ministral are identified as experimental text-only models
- **AND** the optional Ministral vision projector is not downloaded or loaded.
