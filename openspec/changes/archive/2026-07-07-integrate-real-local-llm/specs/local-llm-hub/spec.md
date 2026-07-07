## MODIFIED Requirements

### Requirement: Runtime Boundary

The application SHALL isolate local model execution behind an inference engine
boundary while allowing the app runtime to use a real native local inference
implementation.

#### Scenario: Runtime replacement

- **WHEN** a future runtime is evaluated
- **THEN** the app can add another engine implementation without rewriting the
  chat UI or configured-model resolution flow.

#### Scenario: Real runtime behind boundary

- **GIVEN** the configured GGUF model is available at the standard app-owned
  path
- **WHEN** the chat flow requests generation
- **THEN** the app uses a real `LocalLlmEngine` implementation behind the engine
  boundary
- **AND** the chat UI does not depend directly on JNI, native handles, or
  runtime-specific types.

### Requirement: First Vertical Slice

The first implementation slice SHALL support a single-screen debug chat flow
backed by the local inference engine boundary and a real local model when the
configured GGUF file is already available.

#### Scenario: First prompt loop

- **GIVEN** the configured GGUF model is available at the standard location
- **WHEN** the user submits one text prompt
- **THEN** the app loads the model through the local inference engine boundary
- **AND** streams generated text back into the chat UI
- **AND** surfaces loading or generation failures in the UI.

#### Scenario: Existing model only

- **GIVEN** the configured GGUF model is missing, invalid, or still downloading
- **WHEN** the user opens chat
- **THEN** the app keeps prompt submission disabled
- **AND** reports the current model state
- **AND** does not ask the user to select another model.

### Requirement: Stub Chat Entry Point

The app SHALL keep the dedicated chat screen reachable from home while replacing
the app runtime chat engine with real local inference once native integration is
available.

#### Scenario: Open chat from home

- **GIVEN** the user is on `Home`
- **WHEN** the user opens chat
- **THEN** the app shows the chat screen
- **AND** the chat screen shows the current model availability state.

#### Scenario: Return from chat

- **GIVEN** the user is on the chat screen
- **WHEN** the user taps the back action
- **THEN** the app returns to `Home`.

#### Scenario: Keep fake engine for tests

- **WHEN** JVM tests need deterministic chat generation behavior
- **THEN** the fake/stub `LocalLlmEngine` remains available for test wiring
- **AND** production app wiring can use the real engine without changing chat UI
  code.

## ADDED Requirements

### Requirement: Real Local LLM Runtime

The app SHALL provide a real local inference engine for the configured GGUF
model that is already present and valid on the device.

#### Scenario: Load available configured model

- **GIVEN** model startup reports the configured model as available
- **AND** the configured model file exists at the app-owned path
- **WHEN** chat starts real generation
- **THEN** the app loads that exact file through the real local inference engine
- **AND** applies the configured inference defaults
- **AND** does not use a remote inference API.

#### Scenario: Native load failure

- **GIVEN** the configured model is reported available
- **AND** the native runtime fails to load it
- **WHEN** the user attempts generation
- **THEN** the chat screen shows a load error
- **AND** prompt submission becomes available again when the app is otherwise
  ready
- **AND** the app does not crash.

### Requirement: Real Chat Generation Flow

The chat screen SHALL use the real local engine to generate assistant text while
preserving the existing conversation behavior.

#### Scenario: Stream real assistant output

- **GIVEN** the configured model is available
- **AND** the real local engine is loaded or can be loaded
- **AND** the user typed a non-blank prompt
- **WHEN** the user sends the prompt
- **THEN** the app appends the user message to the conversation
- **AND** creates an assistant message for streamed output
- **AND** appends generated token text as it arrives
- **AND** re-enables sending after generation completes.

#### Scenario: Block concurrent generation

- **GIVEN** model loading or generation is already in progress
- **WHEN** the chat screen renders
- **THEN** the send action is disabled
- **AND** no second generation request starts.

#### Scenario: Surface real generation failure

- **GIVEN** the real local engine reports a generation failure
- **WHEN** generation fails
- **THEN** the chat screen shows an error state
- **AND** preserves the conversation messages already shown
- **AND** allows the user to edit the prompt and try again when sending is
  otherwise allowed.

### Requirement: Native Runtime Lifecycle

The app SHALL manage native inference resources so chat navigation and
cancellation do not leak active model work.

#### Scenario: Leave chat during generation

- **GIVEN** real generation is in progress
- **WHEN** the user leaves the chat screen
- **THEN** the app cancels active generation
- **AND** releases or schedules release of native inference resources
- **AND** does not append further tokens to the hidden chat screen.

#### Scenario: Unload loaded model

- **GIVEN** a real model context is loaded
- **WHEN** the chat lifecycle ends or the configured model becomes unavailable
- **THEN** the app calls the engine unload path
- **AND** releases native resources associated with the loaded model.

### Requirement: Native Runtime Validation

The real LLM integration SHALL be validated with automated boundary tests and a
physical-device smoke test.

#### Scenario: Automated boundary validation

- **WHEN** the implementation is complete
- **THEN** JVM tests cover chat state transitions for loading, generation,
  completion, failure, and cancellation
- **AND** fake-engine tests continue to pass without native libraries.

#### Scenario: Physical device smoke test

- **GIVEN** a debug APK is installed on a physical Android device
- **AND** the configured GGUF file is already present and valid
- **WHEN** the user opens chat and sends a short prompt
- **THEN** the app produces assistant text from local inference
- **AND** the app remains responsive
- **AND** leaving chat does not crash the app.
