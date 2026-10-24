# Spec Delta

## ADDED Requirements

### Requirement: Typed result access for widget scripts

The runtime SHALL expose deterministic helpers to check one named outcome,
read an array at an exact path relative to a successful tool payload, and
construct a link node from an exact result URL path. It SHALL also offer a
seeded array-item selector that returns null for failed or empty outcomes and
otherwise returns the selected item and its exact path, plus a compact card
constructor for strings and presentation nodes. For simple array cards, a
single collection-card helper SHALL combine exact array/item field lookup,
first-item or seeded selection, failed/empty fallback, text nodes, and result-proven link
construction. Helpers SHALL accept only
bounded aliases and paths, SHALL NOT perform recursive name searches or select
the first matching type, and SHALL NOT expand script authority. The existing
outcome object shape and direct access SHALL remain valid for stored programs.

#### Scenario: Read one collection from multiple outcomes

- **GIVEN** two successful outcomes with different aliases
- **WHEN** a script reads an array from one alias and an explicit field path
- **THEN** only that alias and path supply the returned array.

#### Scenario: Missing or mistyped field

- **WHEN** a script requests an absent field, an invalid path, or a value that
  is not an array
- **THEN** the helper fails in a controlled manner rather than returning an
  unrelated array or an empty collection.

#### Scenario: Result-proven article link

- **WHEN** a script creates a link from an item URL path
- **THEN** the link carries the exact alias and path and remains subject to the
  existing presentation provenance and URL allowlist checks.

#### Scenario: Deterministic item card

- **WHEN** a script selects an item from a successful nonempty array using the
  granted seed and passes its text and result-proven link to the card helper
- **THEN** the same runtime snapshot selects the same index and yields a valid
  presentation card without script-authored random-index or card-envelope code.
- **AND** a failed or empty outcome can yield a fallback card without a result
  path lookup or link construction.

#### Scenario: Collection card from a nested result

- **WHEN** a script supplies an exact array path and item-relative title, body,
  and URL fields to the collection-card helper
- **THEN** the helper builds a card from a seeded item and a link carrying the
  exact alias and result-relative URL path.
- **AND** on failed or empty outcomes it returns the supplied fallback card
  before reading the array or constructing a link.

#### Scenario: Select first without a seed

- **WHEN** a collection card requests `first` and receives a nonempty array
- **THEN** it renders the first item without requiring a seed.
- **AND** `seeded_random` selects with the granted seed; omitting the selection
  field preserves the existing seeded behavior for stored programs.

#### Scenario: Proven link from a three-level array path

- **WHEN** a selected result array has a three-segment path such as
  `data.history.events`
- **THEN** the helper and presentation validator accept the exact five-segment
  item URL path, including its numeric item index, without a recursive search.
