## ADDED Requirements

### Requirement: Defensive llama.cpp Sampling

The llama.cpp runtime SHALL use a sampler chain that reduces common degenerate
local-generation loops.

#### Scenario: Initialize defensive sampler chain

- **GIVEN** a llama.cpp model is loaded
- **WHEN** the native runtime initializes generation
- **THEN** it configures top-k sampling
- **AND** top-p sampling
- **AND** min-p sampling
- **AND** a repeat penalty over recent tokens
- **AND** temperature sampling before distribution sampling.

### Requirement: Qwen GGUF Runtime Defaults

Configured Qwen GGUF models SHALL use conservative mobile defaults until their
GPU/Vulkan behavior is validated.

#### Scenario: Load configured Qwen GGUF entry

- **GIVEN** the selected configured model is a Qwen GGUF entry
- **WHEN** the app resolves its runtime configuration
- **THEN** the model uses CPU-only acceleration
- **AND** uses Qwen-specific temperature and top-p defaults
- **AND** uses a bounded max output smaller than the generic 512-token default.
