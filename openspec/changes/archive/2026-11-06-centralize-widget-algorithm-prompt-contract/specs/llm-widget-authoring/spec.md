## ADDED Requirements

### Requirement: Keep algorithm prompt surfaces aligned with one typed contract

The algorithm capture schema SHALL derive step-kind and render-capability values from the accepted domain types. The validated context SHALL express direct-item, direct-list, and processed-result routing from a single typed presentation catalog rather than conflicting free-text instructions. The controlled algorithm repair SHALL use the same catalog and SHALL continue to distinguish step kinds from presentation capabilities. This internal contract SHALL NOT add a model response field or increase the representative initial algorithm input beyond its pre-change size.

#### Scenario: Random direct item

- **WHEN** the algorithm stage receives a request for one random unfiltered result
- **THEN** its complete initial request identifies `collection_card` as the presentation capability and `seeded_random` as a later card-stage selection
- **AND** no post-result step is prescribed solely for that selection.

#### Scenario: Direct ranked list

- **WHEN** the algorithm stage receives a request for a bounded unmodified result list
- **THEN** its schema and validated context agree on `collection_list` and no presentation-only transform step.

#### Scenario: Rejected algorithm artifact

- **WHEN** an algorithm artifact is rejected and included as bounded repair evidence
- **THEN** the complete repair request retains that evidence while restating the same allowed step kinds and presentation routes as the initial contract.
