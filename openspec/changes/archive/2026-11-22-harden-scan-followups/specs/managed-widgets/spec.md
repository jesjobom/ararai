## ADDED Requirements

### Requirement: Launcher widget configuration boundary

The launcher setup activity SHALL accept only IDs that Android associates with
the ArarAI widget provider. Opening or cancelling the activity SHALL leave
bindings unchanged. Only an explicit selection SHALL bind the requested
instance to the selected managed widget.

#### Scenario: Reject an ID outside the ArarAI provider

- **WHEN** the launcher setup activity receives an absent, unknown, or foreign-provider widget ID
- **THEN** it exits with a cancelled result without storing a binding.

#### Scenario: Configure one instance without changing another

- **WHEN** the user explicitly selects a managed widget for a valid instance
- **THEN** the setup activity confirms the selected binding for that instance only.
