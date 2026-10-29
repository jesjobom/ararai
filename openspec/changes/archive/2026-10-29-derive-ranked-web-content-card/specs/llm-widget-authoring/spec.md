## ADDED Requirements

### Requirement: Derive a direct top-ranked web-content card

For a request explicitly asking for the best or first web result and requiring only `web_search_content@1`, the application SHALL interpret best as the provider's highest-ranked result and require a single-result collection card without a post-result transform. It SHALL derive the fixed render program locally using the first result's title, bounded excerpt, and result-proven canonical HTTPS URL. Other search transformations SHALL continue through the custom-source capability. Previously accepted custom-source checkpoints SHALL not be silently rewritten.

#### Scenario: Best result from a ranked search

- **WHEN** a user requests the best result from one `web_search_content@1` search
- **THEN** authoring selects `collection_card` with the existing ranked first item and no post-result transform
- **AND** the render stage completes without another model generation, displaying title, excerpt, and proven link.

#### Scenario: Other result-selection logic

- **WHEN** a request needs filtering, comparison, or another operation on the returned results
- **THEN** authoring retains the validated custom-source path instead of substituting the first-ranked card.

#### Scenario: Accepted custom-source checkpoint

- **WHEN** an existing session already accepted an algorithm using custom source
- **THEN** the application preserves it until the user explicitly reprocesses that algorithm.
