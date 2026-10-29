## ADDED Requirements

### Requirement: Preserve a requested Wikipedia event year in the widget title

When a user explicitly requests that the year of a `wikipedia_on_this_day@1` event appear in the title, the application SHALL select a direct collection card and append the selected event's `year` field in parentheses to its title. The card SHALL retain the event body and result-proven HTTPS link. It SHALL reject a missing or unbounded suffix value rather than silently omitting the requested year. Existing saved revisions SHALL remain unchanged until regenerated.

#### Scenario: Regenerate an event card with its year

- **WHEN** the prompt requests an event year in the title and the selected event has title `Don Giovanni` and year `1787`
- **THEN** the generated card shows `Don Giovanni (1787)` as its title and link label, with the event description and proven URL intact.

#### Scenario: Request without a year in the title

- **WHEN** the prompt does not request a title year
- **THEN** existing collection-card title rendering remains unchanged.
