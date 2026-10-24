## ADDED Requirements

### Requirement: Structured Wikipedia pages tool

The application SHALL register a stable versioned `wikipedia_pages` tool whose
bounded semantic input contains only a search query and supported Wikipedia
language and whose canonical result contains a bounded list of page title,
plain-text extract, canonical HTTPS URL, language, and retrieval time. The tool
SHALL be explicitly eligible for `MODEL` and `WIDGET`, reuse the
application-owned Wikipedia transport and validation policy, and remain subject
to user enablement, verified model capability, and shared dispatch.

#### Scenario: Search Wikipedia for a model or widget

- **GIVEN** the Wikipedia tool is enabled and operationally ready
- **WHEN** an eligible model or widget submits a valid bounded query and language to
  `wikipedia_pages` through the shared dispatcher
- **THEN** the application contacts only the validated language-specific
  Wikipedia API host
- **AND** returns only bounded validated page fields or a controlled failure.

#### Scenario: Reject a widget-controlled transport

- **WHEN** a widget attempts to supply an endpoint, arbitrary URL, header,
  credential, provider, redirect policy, timeout, or executable callback
- **THEN** the request is rejected before network access
- **AND** the application-owned Wikipedia transport remains authoritative.

#### Scenario: Advertise structured Wikipedia pages to verified models

- **GIVEN** Wikipedia is enabled and the selected model declares
  `wikipedia_pages`
- **WHEN** normal Chat or Voice Chat resolves model-callable tools
- **THEN** `wikipedia_pages` is advertised with direct stable page-lookup
  guidance
- **AND** its structured untrusted result and canonical sources are available to
  the model adapter.

#### Scenario: Return untrusted external content

- **WHEN** Wikipedia returns page titles or extracts containing markup,
  instructions, oversized fields, malformed Unicode, or noncanonical links
- **THEN** the tool normalizes and bounds valid plain-text fields and canonical
  links or returns a controlled malformed/unavailable failure
- **AND** no returned text is interpreted as code or application instruction.

### Requirement: Structured Wikipedia events-by-date tool

The application SHALL register a stable versioned `wikipedia_on_this_day` tool
eligible for `MODEL` and `WIDGET`. Its input SHALL contain only numeric month,
numeric day, and a supported Wikipedia language. Its canonical result SHALL
contain a bounded list of historical event year, plain-text description,
related page title, canonical HTTPS URL, language, and retrieval time from a
fixed application-owned official Wikipedia on-this-day endpoint.

#### Scenario: Retrieve actual events for a calendar day

- **GIVEN** Wikipedia is enabled and operationally ready
- **WHEN** an eligible model or widget requests a valid month, day, and language
- **THEN** the application contacts only the validated language-specific
  Wikipedia on-this-day path
- **AND** returns bounded event items rather than a date-index page.

#### Scenario: Reject invalid dates and transport control

- **WHEN** a request supplies an impossible month/day, extra endpoint, arbitrary
  URL, header, credential, redirect policy, timeout, or executable callback
- **THEN** it is rejected before network access
- **AND** no request-controlled transport option is honored.

#### Scenario: Select the correct Wikipedia tool

- **GIVEN** both structured Wikipedia tools are advertised to a verified model
- **WHEN** the user asks for an event on a calendar date
- **THEN** model-visible descriptions and system guidance direct the model to
  `wikipedia_on_this_day`
- **AND** direct biographies, concepts, works, places, or other stable page
  lookups are directed to `wikipedia_pages`.

#### Scenario: Share one bounded Wikipedia turn budget

- **WHEN** a model can call both structured Wikipedia tools in one user turn
- **THEN** their combined calls are limited to the existing Wikipedia per-turn
  maximum
- **AND** successful canonical sources remain attributable to the tool result.
