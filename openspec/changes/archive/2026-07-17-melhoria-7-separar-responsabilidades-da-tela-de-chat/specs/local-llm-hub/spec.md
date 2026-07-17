## ADDED Requirements

### Requirement: Separated Chat Presentation and Media Boundaries

The app SHALL keep Chat presentation separate from media import, recording,
playback, encoding, and filesystem implementations while preserving current
observable behavior.

#### Scenario: Render Chat without direct media I/O

- **GIVEN** Chat state contains text or media messages
- **WHEN** the Chat presentation renders that state
- **THEN** rendering depends on presentation models and explicit media interfaces
- **AND** Compose components do not directly own media filesystem operations.

#### Scenario: Replace a media implementation in tests

- **GIVEN** image import, audio recording, or playback behavior is under test
- **WHEN** a test supplies a fake implementation
- **THEN** Chat orchestration can be verified without real device media I/O.

#### Scenario: Preserve behavior during refactoring

- **GIVEN** the existing supported Chat flows
- **WHEN** responsibilities are moved behind focused boundaries
- **THEN** attachment, recording, playback, permission, cancellation, and persisted-content behavior remains compatible.
