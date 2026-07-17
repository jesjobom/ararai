## ADDED Requirements

### Requirement: Current and Verifiable Project Documentation

The project SHALL maintain onboarding and architecture documentation that
matches implemented ArarAI capabilities, current build configuration, and the
canonical OpenSpec workflow.

#### Scenario: Review current product direction

- **WHEN** a maintainer reads the README and project context
- **THEN** the documented runtimes, model catalog, persistence, multimodal Chat, reasoning, and diagnostics match implemented behavior
- **AND** historical MVP exclusions are not presented as current constraints.

#### Scenario: Follow repository instructions

- **GIVEN** a maintainer follows a documented path or verification command
- **WHEN** it is used in the current repository
- **THEN** the path exists and the command is valid for its stated environment.

#### Scenario: Archive a product change

- **WHEN** an OpenSpec change materially alters documented capabilities, architecture, setup, or validation
- **THEN** its completion checklist includes review of the README and project context
- **AND** documentation claims remain bounded to implemented and verified behavior.
