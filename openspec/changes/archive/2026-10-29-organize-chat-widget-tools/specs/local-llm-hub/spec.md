## ADDED Requirements

### Requirement: Chat-scoped tool management

The Assistant configuration Tools tab SHALL identify its tool controls and model compatibility as applying to Chat/model use, not widget execution. A tool requiring provider setup SHALL expose a configuration action from that tab; provider configuration SHALL remain shared with other eligible consumers without sharing their enablement choice.

#### Scenario: Review a Chat tool

- **WHEN** the user views a tool in Assistant configuration
- **THEN** the screen shows its Chat enablement, model compatibility, and provider setup state separately where applicable
- **AND** it does not claim that a Chat toggle enables or disables widgets.

#### Scenario: Configure a provider from Chat tools

- **WHEN** the user opens a tool's configuration action in Assistant configuration
- **THEN** the application opens that provider's configuration flow in context
- **AND** returning to the Tools tab shows the updated setup status.
