# contextual-screen-tours Specification

## Purpose
TBD - created by archiving change add-contextual-screen-tours. Update Purpose after archive.

## Requirements

### Requirement: Interior-Screen Contextual Tours

The app SHALL offer optional sequential contextual tours for normal Chat, Voice
Chat, Model Management, and Assistant configuration, and SHALL NOT add a feature
tour to Home.

#### Scenario: Keep Home onboarding limited to model consent

- **WHEN** Home is displayed
- **THEN** no feature-tour overlay is shown
- **AND** the existing first-model download consent dialog remains governed by
  its own one-time eligibility rules.

#### Scenario: Start a screen tour on first applicable visit

- **GIVEN** tours are enabled
- **AND** the current screen has at least one applicable incomplete tour step
- **WHEN** the user visits normal Chat, Voice Chat, Model Management, or
  Assistant configuration
- **THEN** the screen offers its contextual tour after the target is laid out
- **AND** does not navigate to another destination automatically.

#### Scenario: Do not replay completed guidance

- **GIVEN** every currently applicable step for a screen and content version is
  complete
- **WHEN** the user returns to that screen
- **THEN** the completed tour is not shown again.

### Requirement: Sequential Spotlight Presentation

Each tour SHALL focus one real screen target at a time, SHALL keep explanatory
content within safe visible bounds, and SHALL expose compact progress and simple
deterministic navigation without activating the highlighted feature.

#### Scenario: Present one anchored step

- **GIVEN** an applicable step target is laid out
- **WHEN** that step is active
- **THEN** the target is visually emphasized against a dimmed surrounding screen
- **AND** one concise title and explanation are associated with that target
- **AND** a current/total indicator identifies the step's position in the
  resolved tour
- **AND** the available Back, Next, Complete, and close actions are exposed as
  appropriate
- **AND** Complete replaces Next on the final step.

#### Scenario: Bring an off-screen target into view

- **GIVEN** an applicable registered target exists outside the visible viewport
- **WHEN** its step is about to be presented
- **THEN** the screen scrolls the target into view before spotlight measurement
- **AND** the explanation is placed within the safe visible area.

#### Scenario: Advance without invoking a feature

- **WHEN** the user advances, returns, completes, or skips from the tour
- **THEN** the action changes only tour presentation or persistence state
- **AND** does not activate the highlighted control
- **AND** does not start a download, inference, recording, transcription, camera
  capture, permission request, or tool call.

#### Scenario: Dismiss the current screen tour

- **GIVEN** a tour is active
- **WHEN** the user activates its close icon or invokes system Back
- **THEN** the current tour closes
- **AND** that screen-tour version is recorded as dismissed
- **AND** unseen steps from that version are not shown on a later visit
- **AND** tours on other screens remain eligible.

### Requirement: Accessible Tour Interaction

The tour SHALL provide an accessible modal interaction with ordered step
content, progress, target description, and controls, and SHALL respect supported
font scaling and reduced-motion preferences.

#### Scenario: Read an active step with assistive technology

- **WHEN** an active tour step is exposed to an accessibility service
- **THEN** focus enters the tour content in a deterministic order
- **AND** the title, explanation, current/total progress, target description, and available
  actions are announced
- **AND** obscured background controls are not independently actionable.

#### Scenario: Close accessible guidance

- **WHEN** the tour is completed, skipped, or dismissed
- **THEN** accessibility focus returns to a meaningful element on the underlying
  screen
- **AND** the screen remains operable without requiring a feature action.

### Requirement: Persisted Tour Policy

The app SHALL persist tour completion or dismissal locally by stable screen-tour
and content version, SHALL keep each screen's terminal state independent, and
SHALL provide an application option to restore all tours.

#### Scenario: Complete one screen tour

- **WHEN** the user completes all applicable steps on one screen
- **THEN** that screen-tour version is recorded locally as complete
- **AND** tours for other screens remain eligible.

#### Scenario: Close one screen tour

- **GIVEN** any screen tour is active
- **WHEN** the user activates its close icon
- **THEN** the active tour closes
- **AND** only that screen-tour version is recorded as dismissed
- **AND** tours for every other screen remain eligible
- **AND** no feature preference or content is changed.

#### Scenario: Offer materially revised guidance

- **GIVEN** a completed or dismissed screen tour is assigned a newer content
  version because its feature guidance materially changed
- **WHEN** the user visits the eligible screen
- **THEN** the newer screen-tour version may be presented
- **AND** unrelated completed or dismissed screen tours remain terminal.

#### Scenario: Restore all tours

- **GIVEN** one or more screen tours were completed or dismissed
- **WHEN** the user confirms the `Restore tours` action in application options
- **THEN** all locally stored tour completion and dismissal records are cleared
- **AND** no tour opens immediately
- **AND** each screen tour becomes eligible on the next visit to that screen
- **AND** model, conversation, assistant, and feature preferences remain unchanged.

### Requirement: Capability-Aware Conditional Steps

The app SHALL evaluate feature-specific tour steps from the current screen's
presentation capabilities and SHALL omit unavailable conditional steps.

#### Scenario: Omit an unavailable conditional step

- **GIVEN** a tour step requires a control or capability not currently presented
- **WHEN** the screen tour is resolved
- **THEN** that step is omitted from the active sequence
- **AND** no unanchored substitute is displayed
- **AND** completing or dismissing the resolved tour ends that screen-tour
  version without later adding the omitted step.

### Requirement: Normal Chat Guidance

The normal-Chat tour SHALL explain history availability and the model prerequisite
for new messages, SHALL explain reasoning request and reasoning presentation as
distinct controls, and SHALL explain transcript presentation when local
transcription makes that feature applicable.

#### Scenario: Explain history without a downloaded model

- **GIVEN** no Chat model is downloaded and selected
- **WHEN** the user visits normal Chat with its tour eligible
- **THEN** the tour explains that existing conversation history remains available
- **AND** explains that downloading and selecting a Chat model is required before
  the user can send new messages.

#### Scenario: Explain history with a ready model

- **GIVEN** a Chat model is downloaded and selected
- **WHEN** the user visits normal Chat with its tour eligible
- **THEN** the tour explains that conversation history remains available
- **AND** confirms that the ready Chat model allows the user to send new messages
- **AND** does not instruct the user to download another model.

#### Scenario: Explain reasoning controls

- **GIVEN** the selected Chat model exposes the applicable reasoning controls
- **WHEN** the normal-Chat reasoning guidance is presented
- **THEN** it explains that enabling reasoning affects future generation requests
- **AND** explains that the additional reasoning can increase the time before the
  final answer is available
- **AND** explains separately that showing reasoning controls whether returned
  reasoning is visible in the conversation.

#### Scenario: Explain transcript presentation

- **GIVEN** a local transcription model makes completed transcript presentation
  applicable
- **WHEN** the normal-Chat transcript guidance is presented
- **THEN** it explains where transcript visibility is controlled
- **AND** explains that hiding a transcript does not delete its persisted text or
  prevent eligible context reconstruction.

### Requirement: Voice Chat Guidance

The Voice Chat tour SHALL explain reasoning behavior and, for image-capable
models, the in-app camera's manual and pause-triggered automatic photo behavior.

#### Scenario: Explain Voice Chat reasoning

- **GIVEN** the selected model supports reasoning
- **WHEN** Voice Chat reasoning guidance is presented
- **THEN** it explains that reasoning can be retained in shared conversation
  history
- **AND** explains that the additional reasoning can increase response time
- **AND** reasoning is not spoken as the assistant response
- **AND** normal Chat can display it according to the shared visibility preference.

#### Scenario: Explain Voice Chat photos

- **GIVEN** the selected model supports image input and the Voice Chat camera
  action is presented
- **WHEN** photo guidance is presented
- **THEN** it explains that opening the camera keeps the current voice turn active
- **AND** the user can capture a manual photo
- **AND** a valid pause can capture the current frame automatically when no
  manual photo is pending
- **AND** at most one photo is submitted with that voice turn.

### Requirement: Model Management Guidance

The Model Management tour SHALL explain workload separation and independent
single-active-model selection for Chat and Transcription.

#### Scenario: Explain active models by workload

- **WHEN** Model Management guidance is presented
- **THEN** it explains that the Chat and Transcription tabs manage different
  workloads
- **AND** at most one downloaded Chat model is active for Chat at a time
- **AND** at most one downloaded Transcription model is active for transcription
  at a time
- **AND** changing one workload's active model does not activate a model in the
  other workload.

#### Scenario: Keep guidance informational

- **WHEN** the user advances through Model Management guidance
- **THEN** no model is downloaded, selected, updated, or deleted.

#### Scenario: Explain how transcription models help conversations

- **WHEN** Transcription-model guidance is presented
- **THEN** it explains that an active local transcription model can convert an
  eligible voice prompt into persisted text
- **AND** the transcript can represent that voice turn when textual conversation
  context must be reconstructed later
- **AND** the first completed transcript can supply the automatic session title
- **AND** it does not imply that every direct-audio turn is transcribed or that a
  transcription model replaces the required Chat model.

### Requirement: Assistant Configuration Guidance

The Assistant configuration tour SHALL explain tool, context, temperature, and
related per-model generation semantics without changing configuration values.

#### Scenario: Explain tools

- **WHEN** Tools guidance is presented
- **THEN** it distinguishes external-network tools from local-compute tools
- **AND** explains that enablement does not make an unsupported tool available to
  the selected model
- **AND** no tool is enabled or invoked by the tour.

#### Scenario: Explain total context

- **WHEN** context guidance is presented for a selected Chat model
- **THEN** it explains that the configured value is the total input-plus-output
  context capacity for that model
- **AND** reasoning and the final answer share that capacity
- **AND** the setting is persisted independently per Chat model.

#### Scenario: Explain temperature

- **WHEN** temperature guidance is presented for a selected Chat model
- **THEN** it explains the effect of lower and higher sampling temperature
- **AND** identifies the available profiles and manual value as per-model choices
- **AND** explains that catalog defaults can be restored.

#### Scenario: Do not describe an unsupported response limit

- **WHEN** generation guidance is presented
- **THEN** it does not claim that an independent maximum response-token control
  exists
- **AND** does not change any instruction, tool, audio, context, temperature, or
  model preference.
