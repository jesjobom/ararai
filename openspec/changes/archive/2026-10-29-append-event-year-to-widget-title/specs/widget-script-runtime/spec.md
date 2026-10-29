## ADDED Requirements

### Requirement: Bounded collection-card title suffix

A collection card MAY name one string or safe integer field from its selected result item as `titleSuffixField`. The runtime SHALL append that field's value in parentheses to the display title and default link label while retaining the selected item's URL provenance. Missing, invalid, or overlong suffix values SHALL fail validation. Cards without this field SHALL retain their existing title behavior.

#### Scenario: Integer year on a selected event

- **WHEN** the selected event has title `Don Giovanni`, year `1787`, and a proven HTTPS URL
- **THEN** the card title and default link label are `Don Giovanni (1787)` and the link retains the selected event's URL provenance.

#### Scenario: Missing year field

- **WHEN** a card requests `titleSuffixField=year` but the selected item has no year
- **THEN** execution fails rather than presenting a title without the requested suffix.
