## ADDED Requirements

### Requirement: Web evidence deduplication uses returned evidence

The shared web evidence normalizer SHALL reserve source URLs, source slots,
and excerpt deduplication keys only when the corresponding evidence is included
in the response.

#### Scenario: An earlier candidate contributes no evidence

- **WHEN** a provider-ranked candidate has no usable excerpt or cannot fit within the consumer's budget
- **THEN** it does not prevent later candidates from contributing the same URL or excerpt if otherwise eligible
- **AND** only included sources count toward the source limit.
