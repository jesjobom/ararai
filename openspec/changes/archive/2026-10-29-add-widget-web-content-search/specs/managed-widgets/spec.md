## ADDED Requirements

### Requirement: Parallel bounded web search modes

Managed widget authoring SHALL offer both the search-only `web_search_top_results@1` contract and a content-bearing `web_search_content@1` contract. The content tool SHALL reuse the configured Chat web-search provider chain to return at most three source-linked results with bounded excerpts, not full page content. The existing Chat contract and top-results behavior SHALL remain unchanged. Both widget tools SHALL require widget web-search enablement, configured credentials, and revision-specific consent.

#### Scenario: Content-bearing result

- **WHEN** a widget using `web_search_content` refreshes with a valid query and a provider returns focused evidence
- **THEN** its tool result contains at most three ranked title, HTTPS URL, and excerpt entries
- **AND** any presented link matches a returned URL and passes the widget destination policy.

#### Scenario: Parallel selection

- **WHEN** a new widget is authored for web search
- **THEN** it can select `web_search_top_results` for up to ten link-only results or `web_search_content` for up to three results with excerpts
- **AND** the Chat-only `web_search` contract is not offered as a widget tool.

#### Scenario: Disabled or unavailable

- **WHEN** widget web search is disabled or no usable credential is configured
- **THEN** neither widget tool sends a provider request
- **AND** existing confirmed revisions retain their exact recorded version and permission.
