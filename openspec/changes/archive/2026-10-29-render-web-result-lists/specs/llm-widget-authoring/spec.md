## ADDED Requirements

### Requirement: Preserve requested ranked web result count

For an explicit request of two through ten ranked web search results, widget authoring SHALL request the specified result limit from `web_search_top_results` and SHALL render each returned result in provider order, up to that limit. A one-item collection card SHALL NOT satisfy such a request. The application SHALL construct links from result-proven HTTPS fields without fetching site content.

#### Scenario: Ten results requested

- **WHEN** the user requests a list of up to ten web search results
- **THEN** the authored tool call uses `limit=10` and the render function displays every returned item, up to ten, with its own source-proven link.

#### Scenario: Ranked results have one presentation container

- **WHEN** a successful search returns multiple ranked results
- **THEN** the detail and execution preview show one card containing an ordered list of those results, rather than a separate card for each result.
- **AND** the compact Android launcher projection retains the result titles as text even when the presentation contains only links.
- **AND** the authoring validator counts the grouped result rows inside that card, accepting the list when all returned fixture items are represented.

#### Scenario: Retry after a valid one-card list was rejected

- **GIVEN** the accepted search algorithm and call already request ten results
- **WHEN** a previous render attempt failed because the validator expected the older multi-card structure
- **THEN** retrying render accepts the application-owned one-card list without another model generation.

#### Scenario: Model returns a one-result implementation

- **WHEN** the model selects a one-item render mode or supplies `limit=1` for an explicit ten-result request
- **THEN** authoring rejects that artifact for repair rather than accepting a widget that silently drops results.

#### Scenario: Empty or failed search

- **WHEN** the search has no results or fails
- **THEN** the generated list shows its fallback instead of a broken or invented link.

#### Scenario: Retry after the render model fails to submit an artifact

- **WHEN** an accepted ranked-web-result algorithm and call function reach the render stage, including a retry after a missing model artifact
- **THEN** the application derives the fixed result-to-link mapping and validates it without loading or invoking the authoring model again.
