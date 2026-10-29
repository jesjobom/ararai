## ADDED Requirements

### Requirement: Search-only web results for widgets

Managed widget authoring SHALL offer a widget-only `web_search_top_results@1` contract that searches a bounded query through a configured provider and returns up to ten provider-ranked title and validated HTTPS URL results. It SHALL support presentation of the first ranked result or multiple results without fetching site content. Widget enablement SHALL be independent of Chat and default off; a confirmed revision SHALL still explicitly permit the tool. Provider credentials and readiness are shared, but Chat's `web_search@1` behavior SHALL remain unchanged. Refreshes SHALL use the existing inexact schedule and SHALL not load a model.

#### Scenario: First ranked result

- **WHEN** an enabled widget requests one result for a query and refreshes on its one-hour schedule
- **THEN** the tool returns at most the provider's first ranked result with a result-proven link
- **AND** the UI does not claim the result is independently verified.

#### Scenario: Up to ten results

- **WHEN** an enabled widget requests ten results for a query and refreshes on its twelve-hour schedule
- **THEN** the tool returns at most ten bounded ranked result entries
- **AND** neither provider content extraction nor direct site fetching runs.

#### Scenario: Unconfigured or disabled

- **WHEN** no verified provider is configured or widget web search is disabled
- **THEN** widget dispatch returns a controlled failure without a network request
- **AND** Chat's independent availability is unchanged.
