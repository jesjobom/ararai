## ADDED Requirements

### Requirement: Separate random item presentation from algorithm data operations

The algorithm objective, validated context, and capture schema SHALL distinguish
the `steps[].kind` data-operation values from `renderCapability` presentation
values. Selecting one unfiltered item from a single returned result array,
whether first or seeded-random, SHALL be described as a `collection_card`
presentation choice without an additional post-call algorithm step. The later
declarative card artifact SHALL select `first` or `seeded_random` as requested.
The model SHALL NOT be instructed to use `custom_source` as a step kind, and an
invalid-algorithm repair SHALL restate this distinction. Actual post-result
filtering, comparison, or conditional presentation MAY use the `custom_source`
render capability and the necessary typed data-operation steps. Tool-call
`selectionMode` SHALL remain limited to tools whose contract supports it.

#### Scenario: One random web result

- **WHEN** a user requests one random item from a web-search result array
- **THEN** the algorithm guidance offers one search `tool_call` with `renderCapability=collection_card`
- **AND** the card stage offers `selection=seeded_random` without a post-call transform step.

#### Scenario: Model confuses step kind with render capability

- **WHEN** an algorithm artifact submits `steps[].kind=custom_source`
- **THEN** validation rejects the artifact
- **AND** the controlled repair explains that `custom_source` is a render capability, not a step kind, without inventing new step-kind values.
