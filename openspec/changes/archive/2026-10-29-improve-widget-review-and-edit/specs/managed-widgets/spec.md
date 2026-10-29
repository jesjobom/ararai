## ADDED Requirements

### Requirement: Consistent widget source review and edit prompt

Before confirming a generated widget, the application SHALL show its JavaScript using the same selectable, syntax-highlighted, line-wrapped code surface as saved-widget detail. Editing a saved widget SHALL prefill the editable instruction field with the active revision's stored prompt, when one exists, without automatically changing the saved widget.

#### Scenario: Review a generated draft

- **WHEN** a valid draft is ready for confirmation
- **THEN** its full JavaScript source is selectable, syntax-highlighted, and line-wrapped in the same code surface used by widget detail.

#### Scenario: Edit an existing widget

- **WHEN** the user selects Edit from a saved widget's detail
- **THEN** the creation/editing screen opens with the active revision's original prompt already in its editable instruction field
- **AND** the user can modify it before starting a new authoring workflow.

#### Scenario: Edit a legacy widget without a saved prompt

- **WHEN** the active revision has no stored prompt
- **THEN** the editable instruction field starts blank.
