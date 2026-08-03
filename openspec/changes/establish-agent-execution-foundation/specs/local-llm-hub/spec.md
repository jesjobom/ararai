## ADDED Requirements

### Requirement: Repository execution guidance

The repository SHALL provide automatically discoverable guidance for coding
agents that identifies the canonical specification workflow, required automated
quality gate, project source boundaries, and checks that require a physical
Android device.

#### Scenario: Agent starts a repository change

- **WHEN** a coding agent begins work in the ArarAI repository
- **THEN** it can discover the repository operating guide without prior session context
- **AND** the guide directs it to the consolidated OpenSpec contract and active change
- **AND** the guide identifies `scripts/quality-gate.sh` as the required automated gate.

#### Scenario: Automated validation reaches an environment boundary

- **WHEN** an agent completes the automated quality gate without a connected supported device
- **THEN** the guide prevents it from claiming real model, GPU, microphone, TTS, memory, or thermal validation
- **AND** directs it to `docs/device-validation.md` for those checks.

