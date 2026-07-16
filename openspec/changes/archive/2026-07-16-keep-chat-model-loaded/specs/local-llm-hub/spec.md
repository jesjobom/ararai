## ADDED Requirements

### Requirement: Retain Loaded Chat Model Across Internal Navigation

The app SHALL retain the selected Chat model engine while the user navigates
between screens inside the same running app process unless model state requires
the engine to be unloaded.

#### Scenario: Leave Chat with an idle loaded model

- **GIVEN** the selected model is loaded for Chat
- **AND** no generation is active
- **WHEN** the user leaves Chat for another app screen
- **THEN** the app retains the loaded model engine
- **AND** returning to Chat with the same selected model does not require a full
  model reload before the next request.

#### Scenario: Leave Chat during generation

- **GIVEN** assistant generation is active
- **WHEN** the user leaves Chat for another app screen
- **THEN** the active generation is cancelled
- **AND** the unchanged selected model remains loaded after cancellation.

#### Scenario: Selected model changes

- **GIVEN** a model is loaded for Chat
- **WHEN** another model is selected
- **THEN** the previously loaded model is unloaded before the new selected
  model is used.

#### Scenario: Selected model becomes unusable

- **GIVEN** a model is loaded for Chat
- **WHEN** that model becomes missing, invalid, deleted, or otherwise
  unavailable
- **THEN** the app cancels active generation if necessary
- **AND** unloads the unusable model engine.

#### Scenario: Android destroys the app process

- **GIVEN** the selected model was retained during internal navigation
- **WHEN** Android destroys the app process
- **THEN** the app does not promise to retain the loaded model
- **AND** a later process start may load the model again when needed.
