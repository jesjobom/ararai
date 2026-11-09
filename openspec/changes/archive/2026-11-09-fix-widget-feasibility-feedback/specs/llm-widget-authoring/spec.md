## ADDED Requirements

### Requirement: Clear terminal feasibility review

When staged widget feasibility returns a validated terminal outcome, the
application SHALL show its outcome and bounded reason or clarification question
directly in the stage card. It SHALL NOT offer Advance when there is no next
stage. The captured attempt SHALL remain inspectable and the user SHALL retain
the option to reprocess or discard and start a revised request.

#### Scenario: Unachievable widget

- **WHEN** a persisted feasibility candidate has outcome `unachievable`
- **THEN** the stage card identifies the request as not feasible and shows the model's validated reason
- **AND** Advance is not offered while reprocess and discard remain available.

#### Scenario: Missing details

- **WHEN** a persisted feasibility candidate has outcome `needs_clarification`
- **THEN** the stage card identifies the need for clarification and shows the validated question
- **AND** Advance is not offered while the user can start a revised request.
