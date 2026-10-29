## ADDED Requirements

### Requirement: Derive a direct selected-page card for Wikipedia page summaries

When a single-step algorithm chooses `collection_card` with `wikipedia_pages@2`, authoring SHALL derive the render mapping locally from the selected page's `title`, full `extract`, and `canonicalUrl`. The card SHALL select `pages[0]`, because this tool version already returns one page selected by the frozen call arguments. The resumable and single-shot pipelines SHALL use the same mapping without loading the model for the render stage. Other tool versions and custom presentation algorithms SHALL retain their existing paths.

#### Scenario: Random page about a topic

- **WHEN** a call to `wikipedia_pages@2` requests seeded-random selection and returns one selected page about the topic
- **THEN** the card displays that page's title, complete introduction, and result-proven HTTPS link without another model generation.

#### Scenario: Recovered render retry

- **WHEN** an existing authoring session has accepted a one-call collection-card algorithm and is waiting to retry the render stage
- **THEN** the retry derives the version-two page card from the accepted checkpoint without changing the tool selection or repeating earlier stages.
